package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RemoteViews
import fi.fmi.mobileweather.widgets.enumeration.WidgetType
import fi.fmi.mobileweather.widgets.model.Announcement
import fi.fmi.mobileweather.widgets.model.ForecastItem
import fi.fmi.mobileweather.widgets.model.PrefKey.LAYOUT_RES_ID
import fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import kotlin.math.roundToInt

open class SmallForecastWidgetProvider : BaseWidgetProvider() {

    override fun getWidgetType(): WidgetType {
        return WidgetType.WEATHER_FORECAST
    }

    override fun getLayoutResourceId(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            R.layout.small_forecast_widget_layout
        } else {
            R.layout.xs_forecast_widget_layout
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val layoutId = getLayoutResourceIdForResize(minWidth, minHeight)
        SharedPreferencesHelper.getInstance(context, appWidgetId).saveInt(LAYOUT_RES_ID, layoutId)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    private fun getLayoutResourceIdForResize(minWidth: Int, minHeight: Int): Int {
        if (minWidth < 100) return R.layout.xs_forecast_widget_layout
        return if (minWidth < 250) R.layout.small_forecast_widget_layout else R.layout.horizontal_forecast_widget_layout
    }

    override fun setWidgetUi(
        context: Context,
        manager: AppWidgetManager,
        data: WidgetData,
        pref: SharedPreferencesHelper,
        initResult: WidgetInitResult,
        widgetId: Int
    ) {
        val views = initResult.widgetRemoteViews
        val forecastItems = data.forecast

        try {
            if (forecastItems.isNullOrEmpty()) return

            var index = -1
            val now = System.currentTimeMillis()
            for (i in forecastItems.indices) {
                if (forecastItems[i].epochtime * 1000 > now) {
                    index = i
                    break
                }
            }
            if (index == -1) index = 0
            val first = forecastItems[index]

            views.setTextViewText(R.id.locationNameTextView, "${first.name},")
            views.setTextViewText(R.id.locationRegionTextView, first.region)
            views.setTextViewText(R.id.temperatureTextView, first.temperature.roundToInt().toString())
            views.setTextViewText(R.id.temperatureUnitTextView, "°")

            val symbol = first.smartSymbol
            val iconRes = context.resources.getIdentifier("s_$symbol", "drawable", context.packageName)
            views.setImageViewResource(R.id.weatherIconImageView, iconRes)

            showCrisisViewIfNeeded(context, data.announcements, views)

            manager.updateAppWidget(widgetId, views)
            pref.saveLong(WIDGET_UI_UPDATED, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "UI Update failed", e)
        }
    }

    private fun showCrisisViewIfNeeded(context: Context, announcements: List<Announcement>?, views: RemoteViews) {
        views.removeAllViews(R.id.crisisViewContainer)
        if (announcements.isNullOrEmpty()) {
            views.setViewVisibility(R.id.crisisViewContainer, GONE)
            return
        }

        for (ann in announcements) {
            if ("Crisis" == ann.type) {
                val crisisView = RemoteViews(context.packageName, R.layout.crisis_view)
                crisisView.setTextViewText(R.id.crisisText, ann.content)
                views.addView(R.id.crisisViewContainer, crisisView)
                views.setViewVisibility(R.id.crisisViewContainer, VISIBLE)
                return
            }
        }
        views.setViewVisibility(R.id.crisisViewContainer, GONE)
    }

    companion object {
        private const val TAG = "SmallWidgetProvider"
    }
}
