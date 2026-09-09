package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import fi.fmi.mobileweather.widgets.model.Announcement
import fi.fmi.mobileweather.widgets.model.Data
import fi.fmi.mobileweather.widgets.model.Duration
import fi.fmi.mobileweather.widgets.model.LocationRecord
import fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED
import fi.fmi.mobileweather.widgets.model.Warning
import fi.fmi.mobileweather.widgets.model.WarningsRecordRoot
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
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class MediumWarningsWidgetProviderTest {
    private lateinit var context: Context
    private lateinit var provider: TestProvider
    private var widgetView: View? = null

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        provider = TestProvider()
        shadowOf(AppWidgetManager.getInstance(context)).bindAppWidgetId(
            1, ComponentName(context, MediumWarningsWidgetProvider::class.java)
        )
    }

    @Test
    fun noAnnouncementsHidesCrisisAndShowsLocation() {
        val view = update(null)

        assertNoCrisis(view)
        assertLocationVisible(view)
    }

    @Test
    fun emptyAnnouncementsHidesCrisisAndShowsLocation() {
        val view = update(emptyList())

        assertNoCrisis(view)
        assertLocationVisible(view)
    }

    @Test
    fun otherAnnouncementTypesDoNotShowCrisis() {
        val view = update(listOf(Announcement("Info", "Information"), Announcement(null, "Unknown")))

        assertNoCrisis(view)
        assertLocationVisible(view)
    }

    @Test
    fun crisisShowsMessageAndKeepsLocationVisible() {
        val view = update(listOf(Announcement("Crisis", "Test crisis message")))

        assertSingleCrisis(view, "Test crisis message")
        assertLocationVisible(view)
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
            assertLocationVisible(view)
        }
    }

    @Test
    fun changedCrisisReplacesPreviousMessage() {
        update(listOf(Announcement("Crisis", "Old crisis")))

        val view = update(listOf(Announcement("Crisis", "New crisis")))

        assertSingleCrisis(view, "New crisis")
    }

    @Test
    fun crisisIsRemovedWhenNoCrisisRemains() {
        val updates = listOf(null, emptyList(), listOf(Announcement("Info", "Information")))
        for (announcements in updates) {
            update(listOf(Announcement("Crisis", "Old crisis")))

            val view = update(announcements)

            assertNoCrisis(view)
            assertLocationVisible(view)
        }
    }

    @Test
    fun crisisCanAppearAgainAfterBeingRemoved() {
        update(listOf(Announcement("Crisis", "Old crisis")))
        update(emptyList())

        val view = update(listOf(Announcement("Crisis", "New crisis")))

        assertSingleCrisis(view, "New crisis")
        assertLocationVisible(view)
    }

    @Test
    @Config(qualifiers = "fi")
    fun unknownWarningKeepsItsRowAndDoesNotPreventOtherWarningsFromUpdating() {
        val view = update(null, listOf(
            activeWarning("newWarningType", "Extreme"),
            activeWarning("wind", "Moderate")
        ))

        val rows = view.findViewById<LinearLayout>(R.id.warningRowContainer)
        assertEquals(2, rows.childCount)
        assertEquals("Tuntematon varoitus", rows.getChildAt(0).findViewById<TextView>(R.id.warningTitle).text.toString())
        assertEquals(context.getString(R.string.warnings_wind), rows.getChildAt(1).findViewById<TextView>(R.id.warningTitle).text.toString())
        assertTrue(rows.getChildAt(0).findViewById<TextView>(R.id.warningDuration).text.isNotEmpty())
        assertTrue(SharedPreferencesHelper.getInstance(context, 1).getLong(WIDGET_UI_UPDATED, 0) > 0)
    }

    @Test
    @Config(qualifiers = "fi")
    fun missingWarningTypeShowsUnknownWarningAndCompletesUpdate() {
        val view = update(null, listOf(activeWarning(null, "Moderate")))

        val rows = view.findViewById<LinearLayout>(R.id.warningRowContainer)
        assertEquals(1, rows.childCount)
        assertEquals("Tuntematon varoitus", rows.getChildAt(0).findViewById<TextView>(R.id.warningTitle).text.toString())
        assertTrue(SharedPreferencesHelper.getInstance(context, 1).getLong(WIDGET_UI_UPDATED, 0) > 0)
    }

    private fun activeWarning(type: String?, severity: String): Warning {
        val now = System.currentTimeMillis()
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return Warning(type = type, severity = severity, language = "fi", duration = Duration(
            startTime = format.format(Date(now - 3600000)),
            endTime = format.format(Date(now + 3600000))
        ))
    }

    private fun update(announcements: List<Announcement>?, warnings: List<Warning> = emptyList()): View {
        val data = WidgetData(
            announcements = announcements,
            warnings = WarningsRecordRoot(Data(warnings = warnings)),
            location = listOf(LocationRecord(name = "Helsinki", region = "Uusimaa", iso2 = "FI"))
        )
        val remoteViews = provider.render(context, data)
        val existingView = widgetView
        return if (existingView == null) {
            remoteViews.apply(context, FrameLayout(context)).also { widgetView = it }
        } else {
            // Launchers reapply RemoteViews to an existing widget when its layout is unchanged.
            remoteViews.reapply(context, existingView)
            existingView
        }
    }

    private fun assertSingleCrisis(view: View, message: String) {
        val container = view.findViewById<LinearLayout>(R.id.crisisViewContainer)
        assertEquals(VISIBLE, container.visibility)
        assertEquals(1, container.childCount)
        assertEquals(message, container.findViewById<TextView>(R.id.crisisText).text.toString())
    }

    private fun assertNoCrisis(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.crisisViewContainer)
        assertEquals(GONE, container.visibility)
        assertEquals(0, container.childCount)
    }

    private fun assertLocationVisible(view: View) {
        val name = view.findViewById<TextView>(R.id.locationNameTextView)
        val region = view.findViewById<TextView>(R.id.locationRegionTextView)
        assertEquals(VISIBLE, name.visibility)
        assertEquals(VISIBLE, region.visibility)
        assertEquals("Helsinki, ", name.text.toString())
        assertEquals("Uusimaa", region.text.toString())
    }

    private class TestProvider : MediumWarningsWidgetProvider() {
        fun render(context: Context, data: WidgetData): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.medium_warnings_widget_layout)
            setWidgetUi(
                context,
                AppWidgetManager.getInstance(context),
                data,
                SharedPreferencesHelper.getInstance(context, 1),
                WidgetInitResult(views, false),
                1
            )
            return views
        }
    }
}
