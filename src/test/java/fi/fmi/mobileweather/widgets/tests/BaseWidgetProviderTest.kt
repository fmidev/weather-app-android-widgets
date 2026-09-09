package fi.fmi.mobileweather.widgets

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.os.Looper
import android.widget.RemoteViews
import com.google.common.util.concurrent.SettableFuture
import fi.fmi.mobileweather.widgets.BaseWidgetProvider.UpdateResult
import fi.fmi.mobileweather.widgets.WidgetNotification.WARNINGS_WIDGET_UPDATE_WORK
import fi.fmi.mobileweather.widgets.model.LocationConstants.CURRENT_LOCATION
import fi.fmi.mobileweather.widgets.model.PrefKey.FAVORITE_LATLON
import fi.fmi.mobileweather.widgets.model.PrefKey.SELECTED_LOCATION
import fi.fmi.mobileweather.widgets.model.PrefKey.LATEST_JSON_UPDATED
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.model.ForecastItem
import fi.fmi.mobileweather.widgets.repository.WeatherRepository
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import fi.fmi.mobileweather.widgets.util.SingleShotLocationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class BaseWidgetProviderTest {
    private lateinit var env: WidgetWorkTestEnvironment
    private lateinit var provider: RecordingProvider

    @Before fun setUp() {
        env = WidgetWorkTestEnvironment()
        provider = RecordingProvider()
        env.bind(1, RecordingProvider::class.java)
        env.bind(2, RecordingProvider::class.java)
    }

    @After fun tearDown() { env.close() }

    @Test fun enablingProviderSchedulesUpdates() {
        provider.onEnabled(env.context)

        assertEquals(1, env.work(WARNINGS_WIDGET_UPDATE_WORK).size)
    }

    @Test fun explicitUpdateRefreshesRequestedWidgetOnceAndSchedulesUpdates() {
        provider.onReceive(env.context, Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(1)))

        assertEquals(listOf(1), provider.updatedIds)
        assertEquals(1, env.workManager.workDatabase.workSpecDao()
            .getWorkSpec(env.work(WidgetNotification.immediateWorkName(1)).single().id.toString())!!
            .input.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS)!!.single())
        assertEquals(1, env.work(WARNINGS_WIDGET_UPDATE_WORK).size)
    }

    @Test fun automaticUpdateRefreshesAllWidgetsOnceAndPreservesSchedule() {
        provider.onEnabled(env.context)
        val original = env.work(WARNINGS_WIDGET_UPDATE_WORK).single().id

        provider.onReceive(env.context, Intent(WidgetNotification.ACTION_APPWIDGET_AUTO_UPDATE))

        assertEquals(listOf(1, 2), provider.updatedIds.sorted())
        assertEquals(original, env.work(WARNINGS_WIDGET_UPDATE_WORK).single().id)
    }

    @Test fun legacyUpdateWithoutIdsRefreshesAllWidgetsOnce() {
        provider.onReceive(env.context, Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE))

        assertEquals(listOf(1, 2), provider.updatedIds.sorted())
    }

    @Test fun unrelatedBroadcastDoesNotFetchDataOrScheduleWork() {
        provider.onReceive(env.context, Intent("unrelated.action"))

        assertTrue(provider.updatedIds.isEmpty())
        assertTrue(env.work(WARNINGS_WIDGET_UPDATE_WORK).isEmpty())
    }

    @Test fun refreshFinishesOnlyAfterNetworkCallbackAndMainThreadRendering() {
        val renderer = prepareRefresh()
        val completion = renderer.refreshWidget(env.context, env.manager, 1)
        assertFalse(completion.isDone)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(completion.isDone)

        renderer.networkCallback.onSuccess(forecastData())
        assertFalse(completion.isDone)
        assertEquals(0, renderer.renders)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, renderer.renders)
        assertEquals(UpdateResult.SUCCESS, completion.get())
    }

    @Test fun networkFailureRendersFallbackBeforeRequestingRetry() {
        val renderer = prepareRefresh()
        val completion = renderer.refreshWidget(env.context, env.manager, 1)
        shadowOf(Looper.getMainLooper()).idle()
        renderer.networkCallback.onError(Exception("Offline"))
        assertFalse(completion.isDone)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, renderer.errors)
        assertEquals(UpdateResult.RETRY, completion.get())
    }

    @Test fun cancellationStopsNetworkTaskAndIgnoresLateData() {
        val renderer = prepareRefresh()
        val completion = renderer.refreshWidget(env.context, env.manager, 1)
        shadowOf(Looper.getMainLooper()).idle()

        completion.cancel(true)
        assertTrue(renderer.networkTask.isCancelled)
        renderer.networkCallback.onSuccess(forecastData())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, renderer.renders)
        assertEquals(0L, SharedPreferencesHelper.getInstance(env.context, 1).getLong(LATEST_JSON_UPDATED, 0))
    }

    @Test fun cancellationStopsLocationRequestAndPreventsSubsequentFetch() {
        val renderer = prepareRefresh(positioned = true)
        val completion = renderer.refreshWidget(env.context, env.manager, 1)
        shadowOf(Looper.getMainLooper()).idle()

        completion.cancel(true)
        assertTrue(renderer.locationSignal.isCanceled)
        renderer.locationCallback.onLocationFailed()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, renderer.fetches)
        assertEquals(0, renderer.errors)
    }

    @Test fun failedLocationUsesStoredCoordinatesAndWaitsForNetwork() {
        val renderer = prepareRefresh(positioned = true)
        SharedPreferencesHelper.getInstance(env.context, 1).saveString("latlon", "60.1,24.9")
        val completion = renderer.refreshWidget(env.context, env.manager, 1)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(completion.isDone)

        renderer.locationCallback.onLocationFailed()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("60.1,24.9", renderer.requestedCoordinates)
        assertFalse(completion.isDone)
        renderer.networkCallback.onSuccess(forecastData())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(UpdateResult.SUCCESS, completion.get())
    }

    @Test fun missingLocationPermissionRendersErrorAndFinishesWithoutRetry() {
        val renderer = prepareRefresh(positioned = true)
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val completion = renderer.refreshWidget(env.context, env.manager, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, renderer.errors)
        assertEquals(0, renderer.fetches)
        assertEquals(UpdateResult.FAILURE, completion.get())
    }

    @Test fun unconfiguredWidgetCompletesWithoutFetching() {
        val renderer = RefreshProvider()
        val completion = renderer.refreshWidget(env.context, env.manager, 1)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, renderer.fetches)
        assertEquals(UpdateResult.SUCCESS, completion.get())
    }

    private fun prepareRefresh(positioned: Boolean = false): RefreshProvider {
        val pref = SharedPreferencesHelper.getInstance(env.context, 1)
        pref.saveInt(SELECTED_LOCATION, if (positioned) CURRENT_LOCATION else 123)
        pref.saveString(FAVORITE_LATLON, "60.1,24.9")
        if (positioned) shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        return RefreshProvider()
    }

    private fun forecastData() = WidgetData(forecast = listOf(ForecastItem(name = "Helsinki")))

    private class RefreshProvider : SmallForecastWidgetProvider() {
        var renders = 0
        var errors = 0
        var fetches = 0
        var requestedCoordinates: String? = null
        val locationSignal = CancellationSignal()
        val networkTask = SettableFuture.create<Unit>()
        lateinit var networkCallback: WeatherRepository.WeatherCallback
        lateinit var locationCallback: SingleShotLocationProvider.LocationCallback

        internal override fun requestLocation(context: Context, callback: SingleShotLocationProvider.LocationCallback): CancellationSignal {
            locationCallback = callback
            return locationSignal
        }

        internal override fun fetchData(context: Context, latlon: String, callback: WeatherRepository.WeatherCallback): java.util.concurrent.Future<*> {
            fetches++
            requestedCoordinates = latlon
            networkCallback = callback
            return networkTask
        }

        override fun initWidget(context: Context, views: RemoteViews?, pref: SharedPreferencesHelper, widgetId: Int) =
            WidgetInitResult(RemoteViews(context.packageName, R.layout.small_forecast_widget_layout), false)

        override fun setWidgetUi(context: Context, manager: AppWidgetManager, data: WidgetData, pref: SharedPreferencesHelper,
            initResult: WidgetInitResult, widgetId: Int) { renders++ }

        override fun showErrorView(context: Context, manager: AppWidgetManager, pref: SharedPreferencesHelper,
            error1: String, error2: String, widgetId: Int) { errors++ }
    }

    class RecordingProvider : MediumWarningsWidgetProvider() {
        val updatedIds = mutableListOf<Int>()

        override fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            updatedIds.add(appWidgetId)
            super.updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}
