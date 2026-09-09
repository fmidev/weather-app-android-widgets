package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
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

    internal fun immediateWorkName(widgetId: Int) = "WidgetUpdate:$widgetId"

    internal fun enqueueWidgetUpdate(context: Context, widgetType: WidgetType, widgetId: Int): Operation {
        val worker = when (widgetType) {
            WidgetType.WEATHER_FORECAST -> WeatherWidgetsUpdateWorker::class.java
            WidgetType.WARNINGS -> WarningsWidgetsUpdateWorker::class.java
        }
        val request = OneTimeWorkRequest.Builder(worker)
            .setInputData(Data.Builder().putIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId)).build())
            .build()
        return WorkManager.getInstance(context).enqueueUniqueWork(
            immediateWorkName(widgetId), ExistingWorkPolicy.REPLACE, request
        )
    }

    internal fun cancelWidgetUpdate(context: Context, widgetId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(immediateWorkName(widgetId))
    }

    private fun providersFor(widgetType: WidgetType): List<Class<out AppWidgetProvider>> = when (widgetType) {
        WidgetType.WEATHER_FORECAST -> listOf(
            SmallForecastWidgetProvider::class.java,
            MediumForecastWidgetProvider::class.java,
            LargeForecastWidgetProvider::class.java
        )
        WidgetType.WARNINGS -> listOf(
            SmallWarningsWidgetProvider::class.java,
            MediumWarningsWidgetProvider::class.java
        )
    }

    // Also restores scheduling for widgets installed before periodic updates were enabled.
    @JvmStatic
    fun scheduleActiveWidgetUpdates(context: Context) {
        for (widgetType in WidgetType.values()) {
            val provider = providersFor(widgetType).firstOrNull {
                getActiveWidgetIds(context, it).isNotEmpty()
            }
            if (provider != null) {
                scheduleWidgetUpdate(context, provider, widgetType)
            } else {
                clearWidgetUpdate(context, widgetType)
            }
        }
    }

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
        val weatherRepeatInterval = (WidgetSetupManager.getWidgetSetup(context)?.weather?.interval ?: DEFAULT_INTERVAL)
            .coerceAtLeast(DEFAULT_INTERVAL)

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
            ExistingPeriodicWorkPolicy.KEEP,
            weatherUpdateRequest
        )

        Log.d("Widget Update", "Weather widget update scheduled with interval $weatherRepeatInterval minutes")
    }

    private fun scheduleWarningsWidgetUpdate(context: Context, constraints: Constraints) {
        val warningsRepeatInterval = (WidgetSetupManager.getWidgetSetup(context)?.warnings?.interval ?: DEFAULT_INTERVAL)
            .coerceAtLeast(DEFAULT_INTERVAL)

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
            ExistingPeriodicWorkPolicy.KEEP,
            weatherUpdateRequest
        )

        Log.d("Widget Update", "Warnings widget update scheduled with interval $warningsRepeatInterval minutes")
    }

    @JvmStatic
    fun clearWidgetUpdate(context: Context, widgetType: WidgetType) {
        // All sizes of the same widget type share one periodic job.
        if (providersFor(widgetType).any { getActiveWidgetIds(context, it).isNotEmpty() }) return

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
