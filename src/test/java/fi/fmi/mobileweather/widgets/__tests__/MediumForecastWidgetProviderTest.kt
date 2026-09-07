package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import fi.fmi.mobileweather.widgets.model.Announcement
import fi.fmi.mobileweather.widgets.model.ForecastItem
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class MediumForecastWidgetProviderTest {
    private lateinit var context: Context
    private lateinit var provider: TestProvider
    private lateinit var manager: AppWidgetManager
    private val widgetId = 1

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        provider = TestProvider()
        manager = AppWidgetManager.getInstance(context)
        shadowOf(manager).bindAppWidgetId(widgetId, ComponentName(context, MediumForecastWidgetProvider::class.java))
        setWidgetSize(180)
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

    private fun setWidgetSize(height: Int) {
        manager.updateAppWidgetOptions(widgetId, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        })
    }

    private fun update(announcements: List<Announcement>?): View {
        val nextHour = System.currentTimeMillis() / 1000 + 3600
        val format = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
        val data = WidgetData(
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
        provider.render(context, manager, data, widgetId)
        // The shadow manager reapplies updates to the same view, as a launcher does.
        return shadowOf(manager).getViewFor(widgetId)
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
