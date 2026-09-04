package fi.fmi.mobileweather.widgets.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import fi.fmi.mobileweather.widgets.LargeForecastWidgetProvider
import fi.fmi.mobileweather.widgets.MediumForecastWidgetProvider
import fi.fmi.mobileweather.widgets.SmallForecastWidgetProvider
import fi.fmi.mobileweather.widgets.WidgetNotification.ACTION_APPWIDGET_AUTO_UPDATE

class WeatherWidgetsUpdateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d(TAG, "Triggering widget updates via broadcast")
        val context = applicationContext

        sendUpdateBroadcast(context, SmallForecastWidgetProvider::class.java)
        sendUpdateBroadcast(context, MediumForecastWidgetProvider::class.java)
        sendUpdateBroadcast(context, LargeForecastWidgetProvider::class.java)

        return Result.success()
    }

    private fun sendUpdateBroadcast(context: Context, cls: Class<*>) {
        val intent = Intent(context, cls).apply {
            action = ACTION_APPWIDGET_AUTO_UPDATE
        }
        context.sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "WeatherWidgetsWorker"
    }
}
