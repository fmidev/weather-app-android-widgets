package fi.fmi.mobileweather.widgets.repository

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class WeatherRepositoryTest {
    private lateinit var server: ServerSocket
    private lateinit var serverTask: Future<*>
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var weatherUrl: String
    private val requests = CopyOnWriteArrayList<URI>()

    @Before
    fun setUp() {
        server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
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
                    requests.add(URI(reader.readLine().split(" ")[1]))
                    while (!reader.readLine().isNullOrEmpty()) {
                        // Consume request headers before sending the response.
                    }
                    val response = """
                        {"658225":[{"epochtime":1800000000,"name":"Helsinki","temperature":12.5,"smartSymbol":1}]}
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                    val headers = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n"
                    it.getOutputStream().use { output ->
                        output.write(headers.toByteArray(Charsets.US_ASCII))
                        output.write(response)
                    }
                }
            }
        }
        weatherUrl = "http://127.0.0.1:${server.localPort}/timeseries"
    }

    @After
    fun tearDown() {
        server.close()
        try {
            serverTask.get(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun forecastUsesCoordinatesInOneRequestAndParsesGroupedResponse() {
        val forecast = WeatherRepository().fetchForecast(weatherUrl, "60.1699,24.9384", "fi")

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
        WeatherRepository().fetchForecast(weatherUrl, "-33.9249,-18.4241", "sv")

        assertEquals(1, requests.size)
        val query = queryParameters(requests.single())
        assertEquals("-33.9249,-18.4241", query["latlon"])
        assertEquals("sv", query["lang"])
        assertFalse(query.containsKey("geoid"))
    }

    @Test
    fun missingCoordinatesDoNotSendAnUnscopedForecastRequest() {
        val repository = WeatherRepository()

        assertNull(repository.fetchForecast(weatherUrl, "", "fi"))
        assertNull(repository.fetchForecast(weatherUrl, "   ", "fi"))
        assertTrue(requests.isEmpty())
    }

    private fun queryParameters(uri: URI): Map<String, String> {
        return uri.query.split("&").associate {
            val (key, value) = it.split("=", limit = 2)
            key to value
        }
    }
}
