package fi.fmi.mobileweather.widgets;

public class SmallForecastWidgetConfigurationActivity extends BaseWidgetConfigurationActivity {
    @Override
    protected Class<?> getWidgetProviderClass() {
        return SmallForecastWidgetProvider.class;
    }
}
