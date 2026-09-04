package fi.fmi.mobileweather.widgets

class MediumWarningsWidgetConfigurationActivity : BaseWidgetConfigurationActivity() {
    override fun getWidgetProviderClass(): Class<*> {
        return MediumWarningsWidgetProvider::class.java
    }
}
