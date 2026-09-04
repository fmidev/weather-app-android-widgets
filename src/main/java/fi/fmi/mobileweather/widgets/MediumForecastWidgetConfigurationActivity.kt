package fi.fmi.mobileweather.widgets

class MediumForecastWidgetConfigurationActivity : BaseWidgetConfigurationActivity() {
    override fun getWidgetProviderClass(): Class<*> {
        return MediumForecastWidgetProvider::class.java
    }
}
