package fi.fmi.mobileweather.widgets;

import fi.fmi.mobileweather.R;
import fi.fmi.mobileweather.widgets.enumeration.WidgetType;

public class SmallWarningsWidgetProvider extends BaseWarningsWidgetProvider {
    @Override
    protected WidgetType getWidgetType() {
        return WidgetType.WARNINGS;
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.small_warnings_widget_layout;
    }
}
