package fi.fmi.mobileweather.widgets.worker

import android.content.Context
import androidx.work.WorkerParameters
import fi.fmi.mobileweather.widgets.LargeForecastWidgetProvider
import fi.fmi.mobileweather.widgets.MediumForecastWidgetProvider
import fi.fmi.mobileweather.widgets.SmallForecastWidgetProvider

class WeatherWidgetsUpdateWorker(
    context: Context,
    params: WorkerParameters
) : BaseWidgetsUpdateWorker(context, params) {
    override fun providers() = listOf(
        SmallForecastWidgetProvider(), MediumForecastWidgetProvider(), LargeForecastWidgetProvider()
    )
}
