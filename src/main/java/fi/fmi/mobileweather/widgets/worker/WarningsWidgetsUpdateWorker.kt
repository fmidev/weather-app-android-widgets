package fi.fmi.mobileweather.widgets.worker

import android.content.Context
import androidx.work.WorkerParameters
import fi.fmi.mobileweather.widgets.MediumWarningsWidgetProvider
import fi.fmi.mobileweather.widgets.SmallWarningsWidgetProvider

class WarningsWidgetsUpdateWorker(
    context: Context,
    params: WorkerParameters
) : BaseWidgetsUpdateWorker(context, params) {
    override fun providers() = listOf(SmallWarningsWidgetProvider(), MediumWarningsWidgetProvider())
}
