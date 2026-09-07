package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RemoteViews
import fi.fmi.mobileweather.widgets.enumeration.WidgetType
import fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import fi.fmi.mobileweather.widgets.util.WarningsIconMapper
import fi.fmi.mobileweather.widgets.util.WarningsTextMapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

open class MediumWarningsWidgetProvider : BaseWarningsWidgetProvider() {

    override fun getWidgetType(): WidgetType {
        return WidgetType.WARNINGS
    }

    override fun getLayoutResourceId(): Int {
        return R.layout.medium_warnings_widget_layout
    }

    override fun setWidgetUi(
        context: Context,
        manager: AppWidgetManager,
        data: WidgetData,
        pref: SharedPreferencesHelper,
        initResult: WidgetInitResult,
        widgetId: Int
    ) {
        val maxWarnings = 3
        val views = initResult.widgetRemoteViews
        val root = data.warnings
        val locations = data.location

        if (root == null || locations.isNullOrEmpty()) return

        try {
            val loc = locations[0]

            if ("FI" != loc.iso2) {
                showErrorView(
                    context, manager, pref,
                    context.getString(R.string.location_outside_data_area_title),
                    context.getString(R.string.location_outside_data_area_description), widgetId
                )
                return
            }

            views.setTextViewText(R.id.locationNameTextView, "${loc.name}, ")
            views.setTextViewText(R.id.locationRegionTextView, loc.region)
            views.setViewVisibility(R.id.locationNameTextView, VISIBLE)
            views.setViewVisibility(R.id.locationRegionTextView, VISIBLE)

            val rawWarnings = root.data?.warnings ?: emptyList()
            var warnings = rawWarnings.filter { w -> "fi" == w.language && isValidDate(w) }
                .sorted()

            warnings = filterUnique(warnings)
            views.removeAllViews(R.id.warningRowContainer)

            val count = minOf(warnings.size, maxWarnings)
            for (i in 0 until count) {
                val w = warnings[i]
                val row = RemoteViews(context.packageName, R.layout.warning_row)
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

                row.addView(R.id.warningIconContainer, icon)
                row.setTextViewText(R.id.warningTitle, context.getString(WarningsTextMapper.getStringResourceId(w.type)))
                val startTime = w.duration?.startTime ?: ""
                val endTime = w.duration?.endTime ?: ""
                row.setTextViewText(R.id.warningDuration, getFormattedTimeFrame(startTime, endTime))
                views.addView(R.id.warningRowContainer, row)
            }

            if (warnings.size > maxWarnings) {
                val more = RemoteViews(context.packageName, R.layout.more_warnings)
                more.setTextViewText(R.id.moreWarnings, "${context.getString(R.string.more_warnings)} (${warnings.size - maxWarnings})")
                views.addView(R.id.warningRowContainer, more)
            } else if (warnings.isEmpty()) {
                val noWarnings = RemoteViews(context.packageName, R.layout.custom_text_layout)
                noWarnings.setTextViewText(R.id.customTextView, context.getString(R.string.no_warnings))
                views.addView(R.id.warningRowContainer, noWarnings)
            }

            val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.updateTime, "${context.getString(R.string.updated)} $formattedTime")

            val announcements = data.announcements

            views.setViewVisibility(R.id.crisisViewContainer, GONE)
            if (announcements != null) {
                for (ann in announcements) {
                    if ("Crisis" == ann.type) {
                        val crisis = RemoteViews(context.packageName, R.layout.crisis_view)
                        crisis.setTextViewText(R.id.crisisText, ann.content)
                        views.addView(R.id.crisisViewContainer, crisis)
                        views.setViewVisibility(R.id.crisisViewContainer, VISIBLE)
                        break
                    }
                }
            }

            manager.updateAppWidget(widgetId, views)
            pref.saveLong(WIDGET_UI_UPDATED, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "UI Update failed", e)
        }
    }

    companion object {
        private const val TAG = "MediumWarningsProvider"
    }
}
