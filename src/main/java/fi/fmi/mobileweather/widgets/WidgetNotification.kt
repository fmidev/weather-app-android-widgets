package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import fi.fmi.mobileweather.widgets.enumeration.WidgetType
import fi.fmi.mobileweather.widgets.worker.WarningsWidgetsUpdateWorker
import fi.fmi.mobileweather.widgets.worker.WeatherWidgetsUpdateWorker
import java.util.concurrent.TimeUnit

object WidgetNotification {

    const val ACTION_APPWIDGET_AUTO_UPDATE = "fi.fmi.mobileweather.AUTO_UPDATE"
    const val WEATHER_WIDGET_UPDATE_WORK = "WeatherWidgetUpdate"
    const val WARNINGS_WIDGET_UPDATE_WORK = "WarningsWidgetUpdate"
    const val DEFAULT_INTERVAL = 15

    @JvmStatic
    fun getActiveWidgetIds(context: Context, providerClass: Class<out AppWidgetProvider>): IntArray {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        return appWidgetManager.getAppWidgetIds(ComponentName(context, providerClass)) ?: intArrayOf()
    }

    @JvmStatic
    fun scheduleWidgetUpdate(context: Context, providerClass: Class<out AppWidgetProvider>, widgetType: WidgetType) {
        val widgetIds = getActiveWidgetIds(context, providerClass)
        if (widgetIds.isNotEmpty()) {
            Log.d("scheduleWidgetUpdate", "Trying to schedule widget update")
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            when (widgetType) {
                WidgetType.WEATHER_FORECAST -> scheduleWeatherWidgetUpdate(context, constraints)
                WidgetType.WARNINGS -> scheduleWarningsWidgetUpdate(context, constraints)
            }
        } else {
            Log.d("Widget Update", "Widget update could not be scheduled, because no active widgets")
        }
    }

    private fun scheduleWeatherWidgetUpdate(context: Context, constraints: Constraints) {
        val weatherRepeatInterval = WidgetSetupManager.getWidgetSetup(context)?.weather?.interval ?: DEFAULT_INTERVAL

        val weatherUpdateRequest = PeriodicWorkRequest.Builder(
            WeatherWidgetsUpdateWorker::class.java,
            weatherRepeatInterval.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(WEATHER_WIDGET_UPDATE_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WEATHER_WIDGET_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.REPLACE,
            weatherUpdateRequest
        )

        Log.d("Widget Update", "Weather widget update scheduled with interval $weatherRepeatInterval minutes")
    }

    private fun scheduleWarningsWidgetUpdate(context: Context, constraints: Constraints) {
        val warningsRepeatInterval = WidgetSetupManager.getWidgetSetup(context)?.warnings?.interval ?: DEFAULT_INTERVAL

        val weatherUpdateRequest = PeriodicWorkRequest.Builder(
            WarningsWidgetsUpdateWorker::class.java,
            warningsRepeatInterval.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(WARNINGS_WIDGET_UPDATE_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WARNINGS_WIDGET_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.REPLACE,
            weatherUpdateRequest
        )

        Log.d("Widget Update", "Warnings widget update scheduled with interval $warningsRepeatInterval minutes")
    }

    @JvmStatic
    fun clearWidgetUpdate(context: Context, widgetType: WidgetType) {
        when (widgetType) {
            WidgetType.WEATHER_FORECAST -> {
                WorkManager.getInstance(context).cancelAllWorkByTag(WEATHER_WIDGET_UPDATE_WORK)
                Log.d("Widget Update", "Weather forecast widgets update cleared")
            }
            WidgetType.WARNINGS -> {
                WorkManager.getInstance(context).cancelAllWorkByTag(WARNINGS_WIDGET_UPDATE_WORK)
                Log.d("Widget Update", "Warnings widgets update cleared")
            }
        }
    }
}
