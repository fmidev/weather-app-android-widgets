package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.text.Html
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RemoteViews
import fi.fmi.mobileweather.widgets.enumeration.WidgetType
import fi.fmi.mobileweather.widgets.model.ForecastItem
import fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

open class LargeForecastWidgetProvider : BaseWidgetProvider() {

    override fun getWidgetType(): WidgetType {
        return WidgetType.WEATHER_FORECAST
    }

    override fun getLayoutResourceId(): Int {
        return R.layout.large_forecast_widget_layout
    }

    private fun getTimestepCount(widgetWidth: Int): Double {
        val columnWidth = 46
        val margins = 32
        return floor((widgetWidth - margins) / columnWidth.toDouble())
    }

    override fun setWidgetUi(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetData: WidgetData,
        pref: SharedPreferencesHelper,
        widgetInitResult: WidgetInitResult,
        appWidgetId: Int
    ) {
        val views = widgetInitResult.widgetRemoteViews
        val forecastItems = widgetData.forecast
        val width = appWidgetManager.getAppWidgetOptions(appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val timeStepCount = getTimestepCount(width)

        try {
            if (forecastItems.isNullOrEmpty()) return

            var firstFutureIndex = -1
            val now = System.currentTimeMillis()
            for (i in forecastItems.indices) {
                if (forecastItems[i].epochtime * 1000 > now) {
                    firstFutureIndex = i
                    break
                }
            }

            if (firstFutureIndex == -1 || forecastItems.size < firstFutureIndex + 1) return

            views.removeAllViews(R.id.forecastContainer)

            val maxIndex = minOf(forecastItems.size, (firstFutureIndex + timeStepCount).toInt())
            for (i in firstFutureIndex until maxIndex) {
                val forecast = forecastItems[i]
                if (i == firstFutureIndex) {
                    views.setTextViewText(R.id.locationNameTextView, "${forecast.name}, ")
                    views.setTextViewText(R.id.locationRegionTextView, forecast.region)
                    views.setTextViewText(R.id.timeTextView, getFormattedTime(forecast.localtime))
                    views.setTextViewText(R.id.temperatureTextView, "${forecast.temperature.roundToInt()}°")
                    val symbol = forecast.smartSymbol
                    val iconRes = context.resources.getIdentifier("s_$symbol", "drawable", context.packageName)
                    views.setImageViewResource(R.id.weatherIconImageView, iconRes)
                    continue
                }

                val step = RemoteViews(context.packageName, R.layout.medium_forecast_timestep)
                step.setTextViewText(R.id.timeTextView, getFormattedTime(forecast.localtime))
                step.setTextViewText(R.id.temperatureTextView, "${forecast.temperature.roundToInt()}°")
                val symbol = forecast.smartSymbol
                val iconRes = context.resources.getIdentifier("s_$symbol", "drawable", context.packageName)
                step.setImageViewResource(R.id.weatherIconImageView, iconRes)
                views.addView(R.id.forecastContainer, step)
            }

            val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val updateStr = "${context.getString(R.string.updated)} <b>$formattedTime</b>"
            views.setTextViewText(R.id.updateTimeTextView, Html.fromHtml(updateStr, Html.FROM_HTML_MODE_LEGACY))

            views.setViewVisibility(R.id.crisisViewContainer, GONE)
            val announcements = widgetData.announcements
            if (announcements != null) {
                for (ann in announcements) {
                    if ("Crisis" == ann.type) {
                        val crisisView = RemoteViews(context.packageName, R.layout.crisis_view)
                        crisisView.setTextViewText(R.id.crisisText, ann.content)
                        views.addView(R.id.crisisViewContainer, crisisView)
                        views.setViewVisibility(R.id.crisisViewContainer, VISIBLE)
                        views.setViewVisibility(R.id.locationNameTextView, GONE)
                        views.setViewVisibility(R.id.locationRegionTextView, GONE)
                        break
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            pref.saveLong(WIDGET_UI_UPDATED, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "UI Update failed", e)
        }
    }

    private fun getFormattedTime(localTime: String?): String {
        if (localTime == null) return ""
        return try {
            val inputFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH", Locale.getDefault())
            val date = inputFormat.parse(localTime)
            if (date != null) outputFormat.format(date) else ""
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val TAG = "LargeWidgetProvider"
    }
}
