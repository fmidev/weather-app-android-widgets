package fi.fmi.mobileweather.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import fi.fmi.mobileweather.widgets.enumeration.WidgetType;
import fi.fmi.mobileweather.widgets.model.WidgetData;
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper;

public class SmallWarningsWidgetProvider extends BaseWarningsWidgetProvider {
    @Override
    protected WidgetType getWidgetType() {
        return WidgetType.WARNINGS;
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.small_warnings_widget_layout;
    }

    @Override
    protected void setWidgetUi(Context context, AppWidgetManager manager, WidgetData data, SharedPreferencesHelper pref, WidgetInitResult initResult, int widgetId) {
        super.setWidgetUi(context, manager, data, pref, initResult, widgetId);
    }
}
