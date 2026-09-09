package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import com.google.gson.Gson
import fi.fmi.mobileweather.widgets.enumeration.WidgetType
import fi.fmi.mobileweather.widgets.model.Announcement
import fi.fmi.mobileweather.widgets.model.ForecastItem
import fi.fmi.mobileweather.widgets.model.LocationConstants.CURRENT_LOCATION
import fi.fmi.mobileweather.widgets.model.PrefKey.LATEST_JSON
import fi.fmi.mobileweather.widgets.model.PrefKey.LATEST_JSON_UPDATED
import fi.fmi.mobileweather.widgets.model.PrefKey.SELECTED_LOCATION
import fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class MediumForecastWidgetProviderTest {
    private lateinit var env: WidgetWorkTestEnvironment
    private lateinit var context: Context
    private lateinit var provider: TestProvider
    private lateinit var manager: AppWidgetManager
    private val widgetId = 1

    @Before
    fun setUp() {
        env = WidgetWorkTestEnvironment()
        context = env.context
        provider = TestProvider()
        manager = AppWidgetManager.getInstance(context)
        shadowOf(manager).bindAppWidgetId(widgetId, ComponentName(context, MediumForecastWidgetProvider::class.java))
        setWidgetSize(180)
    }

    @After fun tearDown() { env.close() }

    @Test fun resizeImmediatelyRendersCachedForecastAtNewDimensionsWithoutRefreshingTimestamps() {
        val data = forecastData(listOf(Announcement("Crisis", "Cached crisis")))
        val pref = SharedPreferencesHelper.getInstance(context, widgetId)
        val updated = System.currentTimeMillis() - 3600000
        pref.saveInt(SELECTED_LOCATION, CURRENT_LOCATION)
        pref.saveString(LATEST_JSON, Gson().toJson(data.forecast))
        pref.saveLong(LATEST_JSON_UPDATED, updated)
        pref.saveLong(WIDGET_UI_UPDATED, updated - 1000)
        pref.saveString("latest_crisis_json", Gson().toJson(data.announcements))
        pref.saveLong("latest_crisis_json_updated", updated)

        resize(180)
        val original = shadowOf(manager).getViewFor(widgetId)
        val originalTemperatureSize = original.findViewById<TextView>(R.id.temperatureTextView).textSize
        assertEquals(5, original.findViewById<LinearLayout>(R.id.hourForecastRowLayout).childCount)

        resize(90, width = 180)
        val resized = shadowOf(manager).getViewFor(widgetId)
        assertEquals(2, resized.findViewById<LinearLayout>(R.id.hourForecastRowLayout).childCount)
        assertTrue(resized.findViewById<TextView>(R.id.temperatureTextView).textSize < originalTemperatureSize)
        assertSingleCrisis(resized, "Cached crisis")
        assertTrue(env.work(WidgetNotification.immediateWorkName(widgetId)).isEmpty())
        assertEquals(updated, pref.getLong(LATEST_JSON_UPDATED, 0))
        assertEquals(updated - 1000, pref.getLong(WIDGET_UI_UPDATED, 0))
        assertEquals(updated, pref.getLong("latest_crisis_json_updated", 0))
    }

    @Test fun resizeWithoutCacheRequestsUpdate() {
        assertResizeRequestsUpdate(null)
    }

    @Test fun resizeWithEmptyForecastRequestsUpdate() {
        assertResizeRequestsUpdate("[]")
    }

    @Test fun resizeWithMalformedCacheRequestsUpdate() {
        assertResizeRequestsUpdate("invalid json")
    }

    @Test fun resizeWithOnlyPastForecastPointsRequestsUpdate() {
        assertResizeRequestsUpdate(Gson().toJson(listOf(ForecastItem(
            epochtime = System.currentTimeMillis() / 1000 - 3600
        ))))
    }

    @Test fun resizeWithExpiredCacheRequestsUpdateEvenWhenForecastHasFuturePoints() {
        assertResizeRequestsUpdate(Gson().toJson(forecastData(null).forecast),
            System.currentTimeMillis() - 25 * 3600000L)
    }

    @Test fun repeatedResizesKeepExistingUnfinishedUpdate() {
        WidgetNotification.enqueueWidgetUpdate(context, WidgetType.WEATHER_FORECAST, widgetId).result.get()
        val original = env.work(WidgetNotification.immediateWorkName(widgetId)).single()
        assertTrue(!original.state.isFinished)

        for (height in listOf(90, 180, 90)) {
            resize(height)
            val work = env.work(WidgetNotification.immediateWorkName(widgetId)).single()
            assertEquals(original.id, work.id)
            assertTrue(!work.state.isFinished)
        }
    }

    @Test fun ordinaryDataUpdateStillRefreshesUiTimestamp() {
        val pref = SharedPreferencesHelper.getInstance(context, widgetId)
        val old = System.currentTimeMillis() - 3600000
        pref.saveLong(WIDGET_UI_UPDATED, old)

        update(null)

        assertTrue(pref.getLong(WIDGET_UI_UPDATED, 0) > old)
    }

    private fun assertResizeRequestsUpdate(json: String?, updated: Long = System.currentTimeMillis()) {
        val pref = SharedPreferencesHelper.getInstance(context, widgetId)
        pref.saveString(LATEST_JSON, json)
        pref.saveLong(LATEST_JSON_UPDATED, updated)
        resize(90)
        assertEquals(1, env.work(WidgetNotification.immediateWorkName(widgetId)).size)
        assertEquals(updated, pref.getLong(LATEST_JSON_UPDATED, 0))
    }

    private fun resize(height: Int, width: Int = 320) {
        setWidgetSize(height, width)
        provider.onReceive(context, Intent(AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, manager.getAppWidgetOptions(widgetId)))
    }

    @Test
    fun noAnnouncementsHidesCrisisAndShowsLocation() {
        val view = update(null)

        assertNoCrisis(view)
        assertLocation(view, VISIBLE)
    }

    @Test
    fun emptyAnnouncementsHidesCrisisAndShowsLocation() {
        val view = update(emptyList())

        assertNoCrisis(view)
        assertLocation(view, VISIBLE)
    }

    @Test
    fun otherAnnouncementTypesDoNotShowCrisis() {
        val view = update(listOf(Announcement("Info", "Information"), Announcement(null, "Unknown")))

        assertNoCrisis(view)
        assertLocation(view, VISIBLE)
    }

    @Test
    fun crisisShowsMessageAndHidesLocation() {
        val view = update(listOf(Announcement("Crisis", "Test crisis message")))

        assertSingleCrisis(view, "Test crisis message")
        assertLocation(view, GONE)
    }

    @Test
    fun onlyFirstCrisisIsShownWhenSeveralAnnouncementsExist() {
        val view = update(listOf(
            Announcement("Info", "Information"),
            Announcement("Crisis", "First crisis"),
            Announcement("Crisis", "Second crisis")
        ))

        assertSingleCrisis(view, "First crisis")
    }

    @Test
    fun repeatedUpdatesDoNotDuplicateCrisisOnReusedView() {
        val announcements = listOf(Announcement("Crisis", "Test crisis message"))
        val originalView = update(announcements)

        repeat(3) {
            val view = update(announcements)
            assertSame(originalView, view)
            assertSingleCrisis(view, "Test crisis message")
            assertLocation(view, GONE)
        }
    }

    @Test
    fun changedCrisisReplacesPreviousMessage() {
        update(listOf(Announcement("Crisis", "Old crisis")))

        val view = update(listOf(Announcement("Crisis", "New crisis")))

        assertSingleCrisis(view, "New crisis")
    }

    @Test
    fun endingCrisisRemovesMessageAndRestoresLocation() {
        val updates = listOf(null, emptyList(), listOf(Announcement("Info", "Information")))
        for (announcements in updates) {
            update(listOf(Announcement("Crisis", "Old crisis")))

            val view = update(announcements)

            assertNoCrisis(view)
            assertLocation(view, VISIBLE)
        }
    }

    @Test
    fun crisisCanAppearAgainAfterBeingRemoved() {
        update(listOf(Announcement("Crisis", "Old crisis")))
        update(emptyList())

        val view = update(listOf(Announcement("Crisis", "New crisis")))

        assertSingleCrisis(view, "New crisis")
        assertLocation(view, GONE)
    }

    @Test
    fun resizingWidgetDoesNotDuplicateCrisis() {
        val announcements = listOf(Announcement("Crisis", "Test crisis message"))
        val originalView = update(announcements)

        for (height in listOf(90, 180, 90)) {
            setWidgetSize(height)
            val view = update(announcements)

            assertSame(originalView, view)
            assertSingleCrisis(view, "Test crisis message")
            assertLocation(view, GONE)
        }
    }

    private fun setWidgetSize(height: Int, width: Int = 320) {
        manager.updateAppWidgetOptions(widgetId, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        })
    }

    private fun update(announcements: List<Announcement>?): View {
        provider.render(context, manager, forecastData(announcements), widgetId)
        // The shadow manager reapplies updates to the same view, as a launcher does.
        return shadowOf(manager).getViewFor(widgetId)
    }

    private fun forecastData(announcements: List<Announcement>?): WidgetData {
        val nextHour = System.currentTimeMillis() / 1000 + 3600
        val format = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
        return WidgetData(
            announcements = announcements,
            forecast = List(6) { index ->
                val time = nextHour + index * 3600
                ForecastItem(
                    epochtime = time,
                    localtime = format.format(Date(time * 1000)),
                    name = "Helsinki",
                    region = "Uusimaa",
                    temperature = 12.0,
                    smartSymbol = 1
                )
            }
        )
    }

    private fun assertSingleCrisis(view: View, message: String) {
        val container = view.findViewById<LinearLayout>(R.id.crisisViewContainer)
        assertEquals(VISIBLE, container.visibility)
        assertEquals(1, container.childCount)
        assertEquals(message, container.findViewById<TextView>(R.id.crisisText).text.toString())
        val forecastRow = view.findViewById<LinearLayout>(R.id.hourForecastRowLayout)
        assertEquals(VISIBLE, forecastRow.visibility)
        assertTrue(forecastRow.childCount > 0)
        assertEquals("12°", forecastRow.findViewById<TextView>(R.id.temperatureTextView).text.toString())
    }

    private fun assertNoCrisis(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.crisisViewContainer)
        assertEquals(GONE, container.visibility)
        assertEquals(0, container.childCount)
    }

    private fun assertLocation(view: View, visibility: Int) {
        val name = view.findViewById<TextView>(R.id.locationNameTextView)
        val region = view.findViewById<TextView>(R.id.locationRegionTextView)
        assertEquals(visibility, name.visibility)
        assertEquals(visibility, region.visibility)
        assertEquals("Helsinki, ", name.text.toString())
        assertEquals("Uusimaa", region.text.toString())
    }

    private class TestProvider : MediumForecastWidgetProvider() {
        // The test application has no launcher activity for the widget click action.
        override fun initWidget(context: Context, views: RemoteViews?, pref: SharedPreferencesHelper, widgetId: Int) =
            WidgetInitResult(views ?: RemoteViews(context.packageName, getLayoutResourceId()), false)

        fun render(context: Context, manager: AppWidgetManager, data: WidgetData, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.medium_forecast_widget_layout)
            setWidgetUi(
                context,
                manager,
                data,
                SharedPreferencesHelper.getInstance(context, widgetId),
                WidgetInitResult(views, false),
                widgetId
            )
        }
    }
}
