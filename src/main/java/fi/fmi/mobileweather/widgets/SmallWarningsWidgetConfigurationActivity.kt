package fi.fmi.mobileweather.widgets

class SmallWarningsWidgetConfigurationActivity : BaseWidgetConfigurationActivity() {
    override fun getWidgetProviderClass(): Class<*> {
        return SmallWarningsWidgetProvider::class.java
    }
}
