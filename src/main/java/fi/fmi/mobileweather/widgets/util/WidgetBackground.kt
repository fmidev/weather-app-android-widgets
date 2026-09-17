package fi.fmi.mobileweather.widgets.util

import fi.fmi.mobileweather.widgets.R

object WidgetBackground {
    fun getResourceId(isNightMode: Boolean, gradientEnabled: Boolean, transparentEnabled: Boolean): Int {
        return when {
            transparentEnabled -> R.drawable.liquid_glass
            isNightMode && gradientEnabled -> R.drawable.gradient_background
            else -> R.color.widgetBackground
        }
    }
}
