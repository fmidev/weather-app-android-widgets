package fi.fmi.mobileweather.widgets.repository

import fi.fmi.mobileweather.widgets.WidgetSetup
import fi.fmi.mobileweather.widgets.WidgetSetupManager
import fi.fmi.mobileweather.widgets.model.WidgetData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class WeatherRepositoryTest {
    private lateinit var server: ServerSocket
    private lateinit var serverTask: Future<*>
    private val executor = Executors.newSingleThreadExecutor()
    private val workerExecutor = Executors.newFixedThreadPool(4)
    private val repository = WeatherRepository(workerExecutor)
    private lateinit var weatherUrl: String
    private val requests = CopyOnWriteArrayList<URI>()
    private val responses = ConcurrentHashMap<String, String>()
    private val setupField = WidgetSetupManager::class.java.getDeclaredField("widgetSetup").apply {
        isAccessible = true
    }
    private var originalSetup: Any? = null

    @Before
    fun setUp() {
        originalSetup = setupField.get(WidgetSetupManager)
        responses["/timeseries"] = """
            {"658225":[{"epochtime":1800000000,"name":"Helsinki","temperature":12.5,"smartSymbol":1}]}
        """.trimIndent()
        responses["/location"] = """[{"name":"Helsinki","iso2":"FI"}]"""
        responses["/warnings"] = """{"data":{"warnings":[{"type":"wind","severity":"Moderate"}]}}"""
        responses["/announcements"] = """[{"type":"Info","content":"Test announcement"}]"""
        server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        serverTask = executor.submit {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: SocketException) {
                    if (server.isClosed) break else throw e
                }
                socket.use {
                    it.soTimeout = 5000
                    val reader = it.getInputStream().bufferedReader()
                    val uri = URI(reader.readLine().split(" ")[1])
                    requests.add(uri)
                    while (!reader.readLine().isNullOrEmpty()) {
                        // Consume request headers before sending the response.
                    }
                    val path = if (uri.path == "/timeseries" && !queryParameters(uri).containsKey("endtime")) {
                        "/location"
                    } else {
                        uri.path
                    }
                    val response = responses.getValue(path).toByteArray(Charsets.UTF_8)
                    val headers = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n"
                    it.getOutputStream().use { output ->
                        output.write(headers.toByteArray(Charsets.US_ASCII))
                        output.write(response)
                    }
                }
            }
        }
        val baseUrl = "http://127.0.0.1:${server.localPort}"
        weatherUrl = "$baseUrl/timeseries"
        setupField.set(WidgetSetupManager, WidgetSetup(
            weather = WidgetSetup.Weather(apiUrl = weatherUrl),
            warnings = WidgetSetup.Warnings(apiUrl = "$baseUrl/warnings"),
            announcements = WidgetSetup.Announcements(api = WidgetSetup.Api(
                fi = "$baseUrl/announcements", sv = "$baseUrl/announcements", en = "$baseUrl/announcements"
            ))
        ))
    }

    @After
    fun tearDown() {
        workerExecutor.shutdownNow()
        workerExecutor.awaitTermination(5, TimeUnit.SECONDS)
        setupField.set(WidgetSetupManager, originalSetup)
        server.close()
        try {
            serverTask.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun forecastUsesCoordinatesInOneRequestAndParsesGroupedResponse() {
        val forecast = repository.fetchForecast(weatherUrl, "60.1699,24.9384", "fi")

        assertEquals(1, requests.size)
        val query = queryParameters(requests.single())
        assertEquals("60.1699,24.9384", query["latlon"])
        assertFalse(query.containsKey("geoid"))
        assertEquals("fi", query["lang"])
        assertEquals("geoid", query["attributes"])
        assertEquals("data", query["endtime"])
        assertEquals("Helsinki", forecast?.single()?.name)
        assertEquals(12.5, forecast!!.single().temperature, 0.0)
    }

    @Test
    fun forecastPreservesNegativeCoordinatesAndRequestedLanguage() {
        repository.fetchForecast(weatherUrl, "-33.9249,-18.4241", "sv")

        assertEquals(1, requests.size)
        val query = queryParameters(requests.single())
        assertEquals("-33.9249,-18.4241", query["latlon"])
        assertEquals("sv", query["lang"])
        assertFalse(query.containsKey("geoid"))
    }

    @Test
    fun missingCoordinatesDoNotSendAnUnscopedForecastRequest() {
        assertNull(repository.fetchForecast(weatherUrl, "", "fi"))
        assertNull(repository.fetchForecast(weatherUrl, "   ", "fi"))
        assertTrue(requests.isEmpty())
    }

    @Test fun queuedForecastUpdatesAllCompleteWithFourWorkerThreads() {
        val (results, errors) = fetchBatch(count = 8, warnings = false)

        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(8, results.size)
        assertEquals(16, requests.size)
        results.forEach {
            assertEquals("Helsinki", it.forecast?.single()?.name)
            assertEquals("Test announcement", it.announcements?.single()?.content)
        }
    }

    @Test fun queuedWarningUpdatesAllCompleteWithFourWorkerThreads() {
        val (results, errors) = fetchBatch(count = 8, warnings = true)

        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(8, results.size)
        assertEquals(24, requests.size)
        results.forEach {
            assertEquals("wind", it.warnings?.data?.warnings?.single()?.type)
            assertEquals("Helsinki", it.location?.single()?.name)
            assertEquals("Test announcement", it.announcements?.single()?.content)
        }
    }

    @Test fun invalidAnnouncementsDoNotFailForecastUpdates() {
        responses["/announcements"] = "invalid json"
        val (results, errors) = fetchBatch(count = 1, warnings = false)

        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals("Helsinki", results.single().forecast?.single()?.name)
        assertTrue(results.single().announcements!!.isEmpty())
    }

    @Test fun invalidAnnouncementsDoNotFailWarningUpdates() {
        responses["/announcements"] = "invalid json"
        val (results, errors) = fetchBatch(count = 1, warnings = true)

        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals("wind", results.single().warnings?.data?.warnings?.single()?.type)
        assertTrue(results.single().announcements!!.isEmpty())
    }

    @Test fun invalidForecastCallsErrorCallback() {
        responses["/timeseries"] = "{}"
        val (results, errors) = fetchBatch(count = 1, warnings = false)

        assertTrue(results.isEmpty())
        assertEquals("Forecast fetch failed", errors.single().message)
    }

    @Test fun invalidWarningsCallErrorCallback() {
        responses["/warnings"] = "invalid json"
        val (results, errors) = fetchBatch(count = 1, warnings = true)

        assertTrue(results.isEmpty())
        assertEquals("Warnings fetch failed", errors.single().message)
    }

    @Test fun nullLocationCallsErrorCallbackEvenWhenWarningsSucceed() {
        responses["/location"] = "null"
        val (results, errors) = fetchBatch(count = 1, warnings = true)

        assertTrue(results.isEmpty())
        assertEquals("Location fetch failed", errors.single().message)
    }

    @Test fun emptyLocationCallsErrorCallbackEvenWhenWarningsSucceed() {
        responses["/location"] = "[]"
        val (results, errors) = fetchBatch(count = 1, warnings = true)

        assertTrue(results.isEmpty())
        assertEquals("Location fetch failed", errors.single().message)
    }

    @Test fun invalidLocationCallsErrorCallbackEvenWhenWarningsSucceed() {
        responses["/location"] = "invalid json"
        val (results, errors) = fetchBatch(count = 1, warnings = true)

        assertTrue(results.isEmpty())
        assertEquals("Location fetch failed", errors.single().message)
    }

    @Test fun missingLocationUrlCallsErrorCallbackEvenWhenWarningsSucceed() {
        val setup = setupField.get(WidgetSetupManager) as WidgetSetup
        setupField.set(WidgetSetupManager, setup.copy(weather = null))
        val (results, errors) = fetchBatch(count = 1, warnings = true)

        assertTrue(results.isEmpty())
        assertEquals("Location fetch failed", errors.single().message)
    }

    private fun fetchBatch(count: Int, warnings: Boolean): Pair<List<WidgetData>, List<Exception>> {
        val ready = CountDownLatch(4)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(count)
        val results = CopyOnWriteArrayList<WidgetData>()
        val errors = CopyOnWriteArrayList<Exception>()
        repeat(4) {
            workerExecutor.submit {
                ready.countDown()
                release.await()
            }
        }
        try {
            assertTrue("Workers did not start", ready.await(5, TimeUnit.SECONDS))
            // Queue the whole batch before releasing the workers. Nested tasks would then
            // sit behind the outer tasks, deterministically reproducing the old deadlock.
            repeat(count) {
                val callback = object : WeatherRepository.WeatherCallback {
                    override fun onSuccess(data: WidgetData) {
                        results.add(data)
                        completed.countDown()
                    }

                    override fun onError(e: Exception) {
                        errors.add(e)
                        completed.countDown()
                    }
                }
                val context = RuntimeEnvironment.getApplication()
                if (warnings) {
                    repository.fetchWarningsData(context, "60.1699,24.9384", callback)
                } else {
                    repository.fetchForecastData(context, "60.1699,24.9384", callback)
                }
            }
        } finally {
            release.countDown()
        }
        assertTrue("Widget updates stalled", completed.await(10, TimeUnit.SECONDS))
        return results to errors
    }

    private fun queryParameters(uri: URI): Map<String, String> {
        return uri.query.split("&").associate {
            val (key, value) = it.split("=", limit = 2)
            key to value
        }
    }
}
