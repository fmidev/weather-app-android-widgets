package fi.fmi.mobileweather.widgets.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import fi.fmi.mobileweather.widgets.MediumWarningsWidgetProvider
import fi.fmi.mobileweather.widgets.SmallWarningsWidgetProvider
import fi.fmi.mobileweather.widgets.WidgetNotification.ACTION_APPWIDGET_AUTO_UPDATE

class WarningsWidgetsUpdateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d(TAG, "Triggering warnings updates via broadcast")
        val context = applicationContext

        sendUpdateBroadcast(context, SmallWarningsWidgetProvider::class.java)
        sendUpdateBroadcast(context, MediumWarningsWidgetProvider::class.java)

        return Result.success()
    }

    private fun sendUpdateBroadcast(context: Context, cls: Class<*>) {
        val intent = Intent(context, cls).apply {
            action = ACTION_APPWIDGET_AUTO_UPDATE
        }
        context.sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "WarningsWidgetsWorker"
    }
}
