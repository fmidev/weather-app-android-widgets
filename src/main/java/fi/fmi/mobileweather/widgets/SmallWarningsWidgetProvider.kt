package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import fi.fmi.mobileweather.widgets.enumeration.WidgetType
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper

open class SmallWarningsWidgetProvider : BaseWarningsWidgetProvider() {

    override fun getWidgetType(): WidgetType {
        return WidgetType.WARNINGS
    }

    override fun getLayoutResourceId(): Int {
        return R.layout.small_warnings_widget_layout
    }

    override fun setWidgetUi(
        context: Context,
        manager: AppWidgetManager,
        data: WidgetData,
        pref: SharedPreferencesHelper,
        initResult: WidgetInitResult,
        widgetId: Int
    ) {
        super.setWidgetUi(context, manager, data, pref, initResult, widgetId)
    }
}
