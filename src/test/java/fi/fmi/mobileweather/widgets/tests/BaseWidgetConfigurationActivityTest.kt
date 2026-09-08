package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class BaseWidgetConfigurationActivityTest {
    private lateinit var controller: ActivityController<SmallForecastWidgetConfigurationActivity>
    private lateinit var root: View
    private lateinit var originalPadding: Insets

    @Before fun setUp() {
        controller = Robolectric.buildActivity(
            SmallForecastWidgetConfigurationActivity::class.java,
            Intent().putExtra(EXTRA_APPWIDGET_ID, 1)
        ).create()
        root = controller.get().findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        originalPadding = Insets.of(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
    }

    @After fun tearDown() {
        controller.destroy()
    }

    @Test
    @Config(sdk = [35])
    fun keepsContentInsideSystemBarsAndDisplayCutouts() {
        applyInsets(Insets.of(0, 24, 0, 48), Insets.of(40, 32, 0, 0))

        assertPadding(40, 32, 0, 48)
    }

    @Test fun updatesPaddingWithoutAccumulatingInsetsWhenWindowChanges() {
        applyInsets(Insets.of(0, 24, 0, 48))
        applyInsets(Insets.of(0, 24, 0, 48))
        assertPadding(0, 24, 0, 48)

        applyInsets(Insets.of(0, 0, 48, 0))
        assertPadding(0, 0, 48, 0)

        applyInsets(Insets.NONE)
        assertPadding(0, 0, 0, 0)
    }

    @Test
    @Config(qualifiers = "+night")
    fun usesDarkSystemBarIconsOnTheWhiteConfigurationBackgroundInNightMode() {
        val window = controller.get().window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        assertTrue(insetsController.isAppearanceLightStatusBars)
        assertTrue(insetsController.isAppearanceLightNavigationBars)
    }

    private fun applyInsets(systemBars: Insets, displayCutout: Insets = Insets.NONE) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), systemBars)
            .setInsets(WindowInsetsCompat.Type.displayCutout(), displayCutout)
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)
    }

    private fun assertPadding(left: Int, top: Int, right: Int, bottom: Int) {
        assertEquals(originalPadding.left + left, root.paddingLeft)
        assertEquals(originalPadding.top + top, root.paddingTop)
        assertEquals(originalPadding.right + right, root.paddingRight)
        assertEquals(originalPadding.bottom + bottom, root.paddingBottom)
    }
}
