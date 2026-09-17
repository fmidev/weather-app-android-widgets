package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RemoteViews
import fi.fmi.mobileweather.widgets.enumeration.WidgetType
import fi.fmi.mobileweather.widgets.model.PrefKey.LATEST_JSON_UPDATED
import fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import java.text.SimpleDateFormat
import java.util.Date
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
        resizeForecastWidget(context, appWidgetManager, appWidgetId)
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
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val height = if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
        } else {
            minHeight
        }
        val timeStepCount = getTimestepCount(width)
        // The launcher reports dp, not row counts. Use the shorter orientation's
        // height to estimate two rows consistently in portrait and landscape.
        val hasTwoRows = minHeight >= TWO_ROW_MIN_HEIGHT_DP

        // Leave room for weather icons and the update time/logo footer as text grows.
        val footerHeight = if (hasTwoRows) 24 else 0
        val textScale = ((height - 90 - footerHeight) / 60f).coerceIn(0f, 1f)

        val locationSp = 13f + 7f * textScale
        val timeSp = 12f + 6f * textScale
        val tempSp = 13f + 9f * textScale

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

                val step = RemoteViews(context.packageName, R.layout.medium_forecast_resizable_timestep)
                step.setTextViewText(R.id.timeStepTimeTextView, getFormattedTime(forecast.localtime))
                step.setTextViewText(R.id.temperatureTextView, "${forecast.temperature.roundToInt()}°")

                step.setTextViewTextSize(R.id.timeStepTimeTextView, TypedValue.COMPLEX_UNIT_SP, timeSp)
                step.setTextViewTextSize(R.id.temperatureTextView, TypedValue.COMPLEX_UNIT_SP, tempSp)

                val symbol = forecast.smartSymbol
                val iconRes = context.resources.getIdentifier("s_$symbol", "drawable", context.packageName)
                step.setImageViewResource(R.id.weatherIconImageView, iconRes)
                val descriptionSymbol = if (symbol > 100) symbol - 100 else symbol
                val descriptionRes = context.resources.getIdentifier("s_$descriptionSymbol", "string", context.packageName)
                if (descriptionRes != 0) step.setContentDescription(R.id.weatherIconImageView, context.getString(descriptionRes))

                if (i == maxIndex - 1) {
                    step.setViewVisibility(R.id.forecastBorder, GONE)
                }
                views.addView(R.id.hourForecastRowLayout, step)
            }

            val updatedAt = if (widgetInitResult.preserveUpdateTime) {
                pref.getLong(WIDGET_UI_UPDATED, 0).takeIf { it > 0 }
                    ?: pref.getLong(LATEST_JSON_UPDATED, now)
            } else {
                now
            }
            val locale = context.resources.configuration.locales[0]
            val updateDate = Date(updatedAt)
            views.setTextViewText(R.id.updateTimeTextView, context.getString(
                R.string.updated_date_time,
                SimpleDateFormat("d.M.yyyy", locale).format(updateDate),
                SimpleDateFormat("HH:mm", locale).format(updateDate)
            ))
            views.setViewVisibility(R.id.updateTimeContainer, if (hasTwoRows) VISIBLE else GONE)

            views.removeAllViews(R.id.crisisViewContainer)
            views.setViewVisibility(R.id.crisisViewContainer, GONE)
            views.setViewVisibility(R.id.locationNameTextView, VISIBLE)
            views.setViewVisibility(R.id.locationRegionTextView, VISIBLE)

            val announcements = widgetData.announcements

            if (announcements != null) {
                for (ann in announcements) {
                    if ("Crisis" == ann.type) {
                        val crisisView = RemoteViews(context.packageName, R.layout.crisis_view)
                        crisisView.setTextViewText(R.id.crisisText, ann.content)
                        views.addView(R.id.crisisViewContainer, crisisView)
                        views.setViewVisibility(R.id.crisisViewContainer, VISIBLE)
                        views.setViewVisibility(R.id.updateTimeContainer, GONE)
                        views.setViewVisibility(R.id.locationNameTextView, GONE)
                        views.setViewVisibility(R.id.locationRegionTextView, GONE)
                        break
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            if (!widgetInitResult.preserveUpdateTime) {
                pref.saveLong(WIDGET_UI_UPDATED, updatedAt)
            }
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
        private const val TWO_ROW_MIN_HEIGHT_DP = 110
    }
}
