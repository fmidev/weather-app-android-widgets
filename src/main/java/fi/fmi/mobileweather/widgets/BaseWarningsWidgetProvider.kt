package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.text.format.DateUtils.isToday
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RemoteViews
import fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED
import fi.fmi.mobileweather.widgets.model.Warning
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import fi.fmi.mobileweather.widgets.util.WarningsIconMapper
import fi.fmi.mobileweather.widgets.util.WarningsTextMapper
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

abstract class BaseWarningsWidgetProvider : BaseWidgetProvider() {

    override fun setWidgetUi(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetData: WidgetData,
        pref: SharedPreferencesHelper,
        widgetInitResult: WidgetInitResult,
        widgetId: Int
    ) {
        val views = widgetInitResult.widgetRemoteViews
        val root = widgetData.warnings
        val locations = widgetData.location

        if (root == null || locations.isNullOrEmpty()) return

        try {
            val loc = locations[0]

            if ("FI" != loc.iso2) {
                showErrorView(
                    context, appWidgetManager, pref,
                    context.getString(R.string.location_outside_data_area_title),
                    context.getString(R.string.location_outside_data_area_description),
                    widgetId
                )
                return
            }

            views.setTextViewText(R.id.locationNameTextView, "${loc.name}, ")
            views.setTextViewText(R.id.locationRegionTextView, loc.region)

            val rawWarnings = root.data?.warnings ?: emptyList()
            var warnings = rawWarnings.filter { w -> "fi" == w.language && isValidDate(w) }
                .sorted()

            warnings = filterUnique(warnings)

            views.removeAllViews(R.id.warningIconContainer)
            views.setViewVisibility(R.id.warningTimeFrameTextView, GONE)

            val toShow = minOf(warnings.size, 2)
            for (i in 0 until toShow) {
                val w = warnings[i]
                val icon = RemoteViews(context.packageName, R.layout.warning_icon)

                val bg = WarningsIconMapper.getCircleBackgroundResourceId(w.severity)
                if (bg != 0) icon.setInt(R.id.warningIconBackgroundImageView, "setBackgroundResource", bg)

                if ("seaWind" == w.type || "wind" == w.type) {
                    icon.setImageViewResource(R.id.warningIconImageView, R.drawable.sea_wind)
                    if (w.physical != null) {
                        icon.setFloat(R.id.warningIconImageView, "setRotation", (w.physical.windDirection - 180).toFloat())
                        icon.setViewVisibility(R.id.windIntensityTextView, VISIBLE)
                        icon.setTextViewText(R.id.windIntensityTextView, w.physical.windIntensity.toDouble().roundToInt().toString())
                    }
                } else {
                    val resId = WarningsIconMapper.getIconResourceId(w.type)
                    if (resId != 0) icon.setImageViewResource(R.id.warningIconImageView, resId)
                }
                views.addView(R.id.warningIconContainer, icon)

                if (toShow == 1) {
                    views.setTextViewText(R.id.warningTextView, context.getString(WarningsTextMapper.getStringResourceId(w.type)))
                    views.setViewVisibility(R.id.warningTimeFrameTextView, VISIBLE)
                    val startTime = w.duration?.startTime ?: ""
                    val endTime = w.duration?.endTime ?: ""
                    views.setTextViewText(R.id.warningTimeFrameTextView, getFormattedTimeFrame(startTime, endTime))
                }
            }

            if (toShow > 1) {
                views.setTextViewText(R.id.warningTextView, "${context.getString(R.string.warnings)} (${warnings.size})")
            } else if (toShow == 0) {
                views.setTextViewText(R.id.warningTextView, "")
                val empty = RemoteViews(context.packageName, R.layout.custom_text_layout)
                empty.setTextViewText(R.id.customTextView, context.getString(R.string.no_warnings))
                views.addView(R.id.warningIconContainer, empty)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
            pref.saveLong(WIDGET_UI_UPDATED, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Warnings UI update failed", e)
        }
    }

    protected open fun isValidDate(w: Warning): Boolean = isValidDate(w, System.currentTimeMillis())

    internal fun isValidDate(w: Warning, now: Long): Boolean {
        return try {
            val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            df.timeZone = TimeZone.getTimeZone("UTC")
            val startTime = w.duration?.startTime ?: return false
            val endTime = w.duration?.endTime ?: return false
            val start = df.parse(startTime) ?: return false
            val end = df.parse(endTime) ?: return false

            val tomorrow = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki")).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, 1)
            }

            end.time > now && start.time < tomorrow.timeInMillis
        } catch (_: Exception) {
            false
        }
    }

    protected open fun filterUnique(warnings: List<Warning>): List<Warning> {
        val result = mutableListOf<Warning>()
        for (w in warnings) {
            if (result.none { r -> r.type == w.type && r.severity == w.severity }) {
                result.add(w)
            }
        }
        return result
    }

    @Throws(ParseException::class)
    protected open fun getFormattedTimeFrame(start: String, end: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val d1 = inputFormat.parse(start) ?: return ""
        val d2 = inputFormat.parse(end) ?: return ""

        val outputTimeZone = TimeZone.getTimeZone("Europe/Helsinki")
        val today = Calendar.getInstance(outputTimeZone)
        val endDay = Calendar.getInstance(outputTimeZone).apply { time = d2 }
        val endsToday = today.get(Calendar.ERA) == endDay.get(Calendar.ERA) &&
                today.get(Calendar.YEAR) == endDay.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == endDay.get(Calendar.DAY_OF_YEAR)
        val outputFormat = SimpleDateFormat(if (endsToday) "HH:mm" else "dd.MM. HH:mm", Locale.getDefault())
        outputFormat.timeZone = outputTimeZone
        return "${outputFormat.format(d1)} - ${outputFormat.format(d2)}"
    }

    companion object {
        private const val TAG = "BaseWarningsProvider"
    }
}
