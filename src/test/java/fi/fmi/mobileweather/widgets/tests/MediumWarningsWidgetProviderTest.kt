package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import fi.fmi.mobileweather.widgets.model.Announcement
import fi.fmi.mobileweather.widgets.model.LocationRecord
import fi.fmi.mobileweather.widgets.model.WarningsRecordRoot
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

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

    private fun update(announcements: List<Announcement>?): View {
        val data = WidgetData(
            announcements = announcements,
            warnings = WarningsRecordRoot(),
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
