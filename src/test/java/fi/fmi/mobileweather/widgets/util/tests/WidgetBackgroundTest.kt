package fi.fmi.mobileweather.widgets.util

import fi.fmi.mobileweather.widgets.R
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetBackgroundTest {
    @Test
    fun defaultBackgroundWorksInBothSystemThemes() {
        for (isNightMode in listOf(false, true)) {
            assertEquals(R.color.widgetBackground, WidgetBackground.getResourceId(isNightMode, false, false))
        }
    }

    @Test
    fun transparentBackgroundWorksInBothSystemThemes() {
        for (isNightMode in listOf(false, true)) {
            assertEquals(R.drawable.liquid_glass, WidgetBackground.getResourceId(isNightMode, false, true))
        }
    }

    @Test
    fun existingGradientPreferenceOnlyAppliesInDarkMode() {
        assertEquals(R.drawable.gradient_background, WidgetBackground.getResourceId(true, true, false))
        assertEquals(R.color.widgetBackground, WidgetBackground.getResourceId(false, true, false))
    }

    @Test
    fun transparentBackgroundTakesPriorityOverGradientPreference() {
        for (isNightMode in listOf(false, true)) {
            assertEquals(R.drawable.liquid_glass, WidgetBackground.getResourceId(isNightMode, true, true))
        }
    }
}
