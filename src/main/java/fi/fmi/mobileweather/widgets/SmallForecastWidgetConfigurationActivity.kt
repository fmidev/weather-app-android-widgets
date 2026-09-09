package fi.fmi.mobileweather.widgets

class SmallForecastWidgetConfigurationActivity : BaseWidgetConfigurationActivity() {
    override fun getWidgetProviderClass(): Class<*> {
        return SmallForecastWidgetProvider::class.java
    }
}
