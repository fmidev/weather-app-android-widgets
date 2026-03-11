package fi.fmi.mobileweather.widgets;

public class LargeForecastWidgetConfigurationActivity extends BaseWidgetConfigurationActivity {
    @Override
    protected Class<?> getWidgetProviderClass() {
        return LargeForecastWidgetProvider.class;
    }
}
