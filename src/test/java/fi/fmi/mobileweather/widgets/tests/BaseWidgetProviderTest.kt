package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import fi.fmi.mobileweather.widgets.WidgetNotification.WARNINGS_WIDGET_UPDATE_WORK
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    class RecordingProvider : MediumWarningsWidgetProvider() {
        val updatedIds = mutableListOf<Int>()

        override fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            updatedIds.add(appWidgetId)
        }
    }
}
