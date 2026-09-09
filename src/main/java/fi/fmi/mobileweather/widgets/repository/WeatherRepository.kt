package fi.fmi.mobileweather.widgets.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.fmi.mobileweather.widgets.WidgetSetup
import fi.fmi.mobileweather.widgets.WidgetSetupManager
import fi.fmi.mobileweather.widgets.model.Announcement
import fi.fmi.mobileweather.widgets.model.ForecastItem
import fi.fmi.mobileweather.widgets.model.LocationRecord
import fi.fmi.mobileweather.widgets.model.WarningsRecordRoot
import fi.fmi.mobileweather.widgets.model.WidgetData
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class WeatherRepository internal constructor(private val executorService: ExecutorService) {
    constructor() : this(sharedExecutor)

    private val gson = Gson()

    interface WeatherCallback {
        fun onSuccess(data: WidgetData)
        fun onError(e: Exception)
    }

    fun fetchForecastData(
        context: Context,
        latlon: String,
        callback: WeatherCallback
    ): Future<*>? {
        val setup = WidgetSetupManager.getWidgetSetup(context)
        if (setup == null) {
            callback.onError(Exception("Widget setup not available"))
            return null
        }

        val language = getLanguageString()
        val weatherUrl = setup.weather?.apiUrl
        val announcementsUrl = getAnnouncementsUrl(setup, language)

        return executorService.submit {
            try {
                // Run requests directly; waiting for nested tasks can exhaust the pool.
                val forecast = if (weatherUrl != null) fetchForecast(weatherUrl, latlon, language) else null
                if (forecast.isNullOrEmpty()) throw Exception("Forecast fetch failed")

                val announcements = try {
                    fetchAnnouncements(announcementsUrl)
                } catch (_: Exception) {
                    emptyList()
                }

                callback.onSuccess(WidgetData(announcements, forecast))
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching weather data", e)
                callback.onError(e)
            }
        }
    }

    fun fetchWarningsData(context: Context, latlon: String, callback: WeatherCallback): Future<*>? {
        val setup = WidgetSetupManager.getWidgetSetup(context)
        if (setup == null) {
            callback.onError(Exception("Widget setup not available"))
            return null
        }

        val language = getLanguageString()
        val weatherUrl = setup.weather?.apiUrl
        val warningsUrl = setup.warnings?.apiUrl
        val announcementsUrl = getAnnouncementsUrl(setup, language)

        return executorService.submit {
            try {
                val warnings = if (warningsUrl != null) fetchWarnings(warningsUrl, latlon, language) else null
                if (warnings == null) throw Exception("Warnings fetch failed")

                val locations = if (weatherUrl != null) fetchLocationInfo(weatherUrl, latlon) else null
                if (locations.isNullOrEmpty()) throw Exception("Location fetch failed")

                val announcements = try {
                    fetchAnnouncements(announcementsUrl)
                } catch (_: Exception) {
                    emptyList()
                }

                callback.onSuccess(WidgetData(announcements, null, warnings, locations))
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching warnings data", e)
                callback.onError(e)
            }
        }
    }

    private fun getAnnouncementsUrl(setup: WidgetSetup, language: String): String? {
        val api = setup.announcements?.api ?: return null
        return when (language) {
            "fi" -> api.fi
            "sv" -> api.sv
            else -> api.en
        }
    }

    private fun getLanguageString(): String {
        var language = Locale.getDefault().language
        if (language != "fi" && language != "sv" && language != "en") {
            language = "en"
        }
        return language
    }

    internal fun fetchForecast(weatherUrl: String, latlon: String, language: String): List<ForecastItem>? {
        if (latlon.isBlank()) return null

        val params = "geoid,epochtime,localtime,utctime,name,region,iso2,temperature,feelsLike,smartSymbol,windDirection,windSpeedMS,windCompass8"
        val url = "$weatherUrl?latlon=$latlon&endtime=data&format=json&attributes=geoid&lang=$language&param=$params"

        val json = fetchJsonString(url) ?: return null
        return try {
            val type = object : TypeToken<Map<String, List<ForecastItem>>>() {}.type
            val map: Map<String, List<ForecastItem>>? = gson.fromJson(json, type)
            if (!map.isNullOrEmpty()) map.values.iterator().next() else null
        } catch (e: Exception) {
            Log.e(TAG, "Forecast parsing failed", e)
            null
        }
    }

    private fun fetchWarnings(warningsUrl: String, latlon: String, language: String): WarningsRecordRoot? {
        val url = "$warningsUrl?latlon=$latlon&country=$language"
        val json = fetchJsonString(url) ?: return null
        return try {
            gson.fromJson(json, WarningsRecordRoot::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchLocationInfo(weatherUrl: String, latlon: String): List<LocationRecord>? {
        val url = "$weatherUrl?param=geoid,name,region,iso2&latlon=$latlon&format=json"
        val json = fetchJsonString(url) ?: return null
        return try {
            val type = object : TypeToken<List<LocationRecord>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchAnnouncements(src: String?): List<Announcement> {
        val json = fetchJsonString(src) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Announcement>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchJsonString(src: String?): String? {
        if (src.isNullOrEmpty()) return null
        return try {
            val url = URL(src)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.inputStream.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    response.toString()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching $src", e)
            null
        }
    }

    companion object {
        // Workers create short-lived repositories; keep the bounded pool process-wide.
        private val sharedExecutor = Executors.newFixedThreadPool(4)
        private const val TAG = "WeatherRepository"
    }
}
