package fi.fmi.mobileweather.widgets

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class BaseWidgetConfigurationActivityTest {
    private lateinit var controller: ActivityController<SmallForecastWidgetConfigurationActivity>
    private lateinit var root: View
    private lateinit var originalPadding: Insets

    @Before fun setUp() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
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

    @Test fun missingForegroundPermissionRequestsCoarseAndFineLocationTogether() {
        controller.get().askLocationPermissionIfNeeded()
        confirmPermissionExplanation()

        val request = shadowOf(controller.get()).lastRequestedPermission
        assertEquals(1, request.requestCode)
        assertArrayEquals(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), request.requestedPermissions)
        assertNotFinalized()
    }

    @Test
    @Config(sdk = [28, 29, 30, 35])
    fun approximateForegroundGrantContinuesToTheRequiredNextStep() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        // Permission names, rather than array position or fine-location access, decide success.
        controller.get().onRequestPermissionsResult(1,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            intArrayOf(PERMISSION_DENIED, PERMISSION_GRANTED))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            assertFinalized()
            assertNull(ShadowAlertDialog.getLatestAlertDialog())
        } else {
            assertNotFinalized()
            confirmPermissionExplanation()
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                val request = shadowOf(controller.get()).lastRequestedPermission
                assertEquals(2, request.requestCode)
                assertArrayEquals(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), request.requestedPermissions)
            } else {
                assertSettingsOpened()
                assertNull(shadowOf(controller.get()).lastRequestedPermission)
            }
        }
    }

    @Test fun deniedOrCancelledForegroundRequestDoesNotFinishOrRequestBackgroundLocation() {
        for (results in listOf(intArrayOf(PERMISSION_DENIED, PERMISSION_DENIED), intArrayOf())) {
            controller.get().onRequestPermissionsResult(1,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), results)

            assertNotFinalized()
            assertNull(ShadowAlertDialog.getLatestAlertDialog())
            assertNull(shadowOf(controller.get()).nextStartedActivity)
            assertEquals(controller.get().getString(R.string.denied_positioning), ShadowToast.getTextOfLatestToast())
        }
    }

    @Test
    @Config(sdk = [28, 29, 30, 35])
    fun existingApproximateAndRequiredBackgroundPermissionsFinishImmediately() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            grant(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        controller.get().askLocationPermissionIfNeeded()

        assertFinalized()
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertNull(shadowOf(controller.get()).nextStartedActivity)
    }

    @Test
    @Config(sdk = [29])
    fun backgroundPermissionGrantFinishesOnAndroidTen() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        controller.get().askLocationPermissionIfNeeded()
        confirmPermissionExplanation()
        grant(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        controller.get().onRequestPermissionsResult(2,
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), intArrayOf(PERMISSION_GRANTED))

        assertFinalized()
    }

    @Test
    @Config(sdk = [29])
    fun deniedBackgroundPermissionKeepsConfigurationOpen() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        controller.get().askLocationPermissionIfNeeded()
        confirmPermissionExplanation()

        controller.get().onRequestPermissionsResult(2,
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), intArrayOf(PERMISSION_DENIED))

        assertNotFinalized()
        assertEquals(controller.get().getString(R.string.denied_positioning), ShadowToast.getTextOfLatestToast())
    }

    @Test
    @Config(sdk = [30, 35])
    fun returningFromSettingsWithBackgroundPermissionFinishesWidget() {
        openBackgroundPermissionSettings()
        controller.pause()
        grant(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        controller.resume()

        assertFinalized()
    }

    @Test
    @Config(sdk = [30, 35])
    fun returningWithoutBackgroundPermissionDoesNotFinishOrReopenSettings() {
        openBackgroundPermissionSettings()
        controller.pause().resume()

        assertNotFinalized()
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)

        // A later, unrelated resume must not complete the abandoned settings flow.
        grant(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        controller.pause().resume()
        assertNotFinalized()
    }

    @Test
    @Config(sdk = [30, 35])
    fun returningFromSettingsWithoutForegroundPermissionDoesNotFinish() {
        openBackgroundPermissionSettings()
        controller.pause()
        grant(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        controller.resume()

        assertNotFinalized()
    }

    @Test
    @Config(sdk = [30, 35])
    fun settingsFlowSurvivesActivityRecreation() {
        openBackgroundPermissionSettings()
        val savedState = Bundle()
        controller.pause().saveInstanceState(savedState).stop().destroy()
        grant(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        controller = Robolectric.buildActivity(
            SmallForecastWidgetConfigurationActivity::class.java,
            Intent().putExtra(EXTRA_APPWIDGET_ID, 1)
        ).create(savedState).start().resume()

        assertFinalized()
    }

    private fun grant(vararg permissions: String) {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(*permissions)
    }

    private fun confirmPermissionExplanation() {
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun openBackgroundPermissionSettings() {
        controller.start().resume()
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        controller.get().askLocationPermissionIfNeeded()
        confirmPermissionExplanation()
        assertSettingsOpened()
        assertNotFinalized()
    }

    private fun assertSettingsOpened() {
        val intent = shadowOf(controller.get()).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${controller.get().packageName}", intent.data.toString())
    }

    private fun assertFinalized() {
        assertTrue(controller.get().isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(controller.get()).resultCode)
        assertEquals(1, shadowOf(controller.get()).resultIntent.getIntExtra(EXTRA_APPWIDGET_ID, 0))
    }

    private fun assertNotFinalized() {
        assertFalse(controller.get().isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(controller.get()).resultCode)
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
