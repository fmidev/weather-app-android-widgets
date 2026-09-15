package fi.fmi.mobileweather.widgets

class LargeForecastWidgetConfigurationActivity : BaseWidgetConfigurationActivity() {
    override fun getWidgetProviderClass(): Class<*> {
        return LargeForecastWidgetProvider::class.java
    }
}
