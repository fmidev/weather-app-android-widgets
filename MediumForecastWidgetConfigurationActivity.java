package fi.fmi.mobileweather.widgets;

public class MediumForecastWidgetConfigurationActivity extends BaseWidgetConfigurationActivity {
    @Override
    protected Class<?> getWidgetProviderClass() {
        return MediumForecastWidgetProvider.class;
    }
}
