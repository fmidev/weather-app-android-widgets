package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RemoteViews
import fi.fmi.mobileweather.widgets.enumeration.WidgetType
import fi.fmi.mobileweather.widgets.model.ForecastItem
import fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

open class MediumForecastWidgetProvider : BaseWidgetProvider() {

    override fun getWidgetType(): WidgetType {
        return WidgetType.WEATHER_FORECAST
    }

    override fun getLayoutResourceId(): Int {
        return R.layout.medium_forecast_widget_layout
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    private fun getTimestepCount(widgetWidth: Int): Double {
        val columnWidth = 52
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
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val timeStepCount = getTimestepCount(width)

        val isTaller = height >= 90 // 4x2 mode or taller

        val locationSp = if (isTaller) 16f else 13f
        val timeSp = if (isTaller) 14f else 12f
        val tempSp = if (isTaller) 16f else 13f

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

            if (firstFutureIndex == -1 || forecastItems.size < firstFutureIndex + 1) {
                showErrorView(context, appWidgetManager, pref, context.getString(R.string.update_failed), "", appWidgetId)
                return
            }

            views.removeAllViews(R.id.hourForecastRowLayout)

            val maxIndex = minOf(forecastItems.size, (firstFutureIndex + timeStepCount).toInt())
            for (i in firstFutureIndex until maxIndex) {
                val forecast = forecastItems[i]
                if (i == firstFutureIndex) {
                    views.setTextViewText(R.id.locationNameTextView, "${forecast.name}, ")
                    views.setTextViewText(R.id.locationRegionTextView, forecast.region)

                    views.setTextViewTextSize(R.id.locationNameTextView, TypedValue.COMPLEX_UNIT_SP, locationSp)
                    views.setTextViewTextSize(R.id.locationRegionTextView, TypedValue.COMPLEX_UNIT_SP, locationSp)
                }

                val step = RemoteViews(context.packageName, R.layout.forecast_timestep)
                step.setTextViewText(R.id.timeStepTimeTextView, getFormattedTime(forecast.localtime))
                step.setTextViewText(R.id.temperatureTextView, "${forecast.temperature.roundToInt()}°")

                step.setTextViewTextSize(R.id.timeStepTimeTextView, TypedValue.COMPLEX_UNIT_SP, timeSp)
                step.setTextViewTextSize(R.id.temperatureTextView, TypedValue.COMPLEX_UNIT_SP, tempSp)

                val symbol = forecast.smartSymbol
                val iconRes = context.resources.getIdentifier("s_$symbol", "drawable", context.packageName)
                step.setImageViewResource(R.id.weatherIconImageView, iconRes)

                if (i == (firstFutureIndex + timeStepCount - 1).toInt()) {
                    step.setViewVisibility(R.id.forecastBorder, GONE)
                }
                views.addView(R.id.hourForecastRowLayout, step)
            }

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
        private const val TAG = "MediumWidgetProvider"
    }
}
