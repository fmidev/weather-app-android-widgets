package fi.fmi.mobileweather.widgets

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

object WidgetSetupManager {
    private var widgetSetup: WidgetSetup? = null

    @JvmStatic
    fun getWidgetSetup(context: Context): WidgetSetup? {
        if (widgetSetup == null) {
            initializeSetup(context)
        }
        return widgetSetup
    }

    @JvmStatic
    fun initializeSetup(context: Context) {
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open("widgetConfig.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
            val jsonString = stringBuilder.toString()

            val gson = Gson()
            widgetSetup = gson.fromJson(jsonString, WidgetSetup::class.java)

            Log.d("Widget Update", "Widget setup initialized")
        } catch (e: IOException) {
            Log.e("Widget Update", "Error reading setup file", e)
        }
    }
}
