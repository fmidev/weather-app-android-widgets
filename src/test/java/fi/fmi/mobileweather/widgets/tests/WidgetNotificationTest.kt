package fi.fmi.mobileweather.widgets

import androidx.work.NetworkType
import androidx.work.WorkInfo
import fi.fmi.mobileweather.widgets.WidgetNotification.WARNINGS_WIDGET_UPDATE_WORK
import fi.fmi.mobileweather.widgets.WidgetNotification.WEATHER_WIDGET_UPDATE_WORK
import fi.fmi.mobileweather.widgets.enumeration.WidgetType
import fi.fmi.mobileweather.widgets.worker.WarningsWidgetsUpdateWorker
import fi.fmi.mobileweather.widgets.worker.WeatherWidgetsUpdateWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class WidgetNotificationTest {
    private lateinit var env: WidgetWorkTestEnvironment

    @Before fun setUp() { env = WidgetWorkTestEnvironment() }
    @After fun tearDown() { env.close() }

    @Test fun schedulesSeparateJobsWithConfiguredIntervalsAndNetworkConstraint() {
        env.bind(1, MediumForecastWidgetProvider::class.java)
        env.bind(2, MediumWarningsWidgetProvider::class.java)

        WidgetNotification.scheduleActiveWidgetUpdates(env.context)

        assertSchedule(WEATHER_WIDGET_UPDATE_WORK, 30, WeatherWidgetsUpdateWorker::class.java.name)
        assertSchedule(WARNINGS_WIDGET_UPDATE_WORK, 45, WarningsWidgetsUpdateWorker::class.java.name)
    }

    @Test fun repeatedUpdatesAndDifferentSizesKeepTheExistingJob() {
        env.bind(1, SmallForecastWidgetProvider::class.java)
        env.bind(2, MediumForecastWidgetProvider::class.java)
        SmallForecastWidgetProvider().onEnabled(env.context)
        val original = env.work(WEATHER_WIDGET_UPDATE_WORK).single().id

        MediumForecastWidgetProvider().onUpdate(env.context, env.manager, intArrayOf(2))
        WidgetNotification.scheduleActiveWidgetUpdates(env.context)

        assertEquals(original, env.work(WEATHER_WIDGET_UPDATE_WORK).single().id)
    }

    @Test fun missingIntervalsDefaultToFifteenMinutes() {
        env.setup(WidgetSetup())
        env.bind(1, MediumForecastWidgetProvider::class.java)
        env.bind(2, MediumWarningsWidgetProvider::class.java)

        WidgetNotification.scheduleActiveWidgetUpdates(env.context)

        assertSchedule(WEATHER_WIDGET_UPDATE_WORK, 15, WeatherWidgetsUpdateWorker::class.java.name)
        assertSchedule(WARNINGS_WIDGET_UPDATE_WORK, 15, WarningsWidgetsUpdateWorker::class.java.name)
    }

    @Test fun intervalsBelowWorkManagerMinimumUseFifteenMinutes() {
        env.setup(WidgetSetup(weather = WidgetSetup.Weather(interval = 0), warnings = WidgetSetup.Warnings(interval = 5)))
        env.bind(1, MediumForecastWidgetProvider::class.java)
        env.bind(2, MediumWarningsWidgetProvider::class.java)

        WidgetNotification.scheduleActiveWidgetUpdates(env.context)

        assertSchedule(WEATHER_WIDGET_UPDATE_WORK, 15, WeatherWidgetsUpdateWorker::class.java.name)
        assertSchedule(WARNINGS_WIDGET_UPDATE_WORK, 15, WarningsWidgetsUpdateWorker::class.java.name)
    }

    @Test fun noActiveWidgetsDoesNotCreateJobs() {
        WidgetNotification.scheduleActiveWidgetUpdates(env.context)
        WidgetNotification.scheduleWidgetUpdate(env.context, MediumWarningsWidgetProvider::class.java, WidgetType.WARNINGS)

        assertTrue(env.work(WEATHER_WIDGET_UPDATE_WORK).isEmpty())
        assertTrue(env.work(WARNINGS_WIDGET_UPDATE_WORK).isEmpty())
    }

    @Test fun removingOneSizePreservesJobsForOtherSizes() {
        env.bind(1, SmallForecastWidgetProvider::class.java)
        env.bind(2, SmallWarningsWidgetProvider::class.java)
        WidgetNotification.scheduleActiveWidgetUpdates(env.context)

        MediumForecastWidgetProvider().onDisabled(env.context)
        MediumWarningsWidgetProvider().onDisabled(env.context)

        assertEquals(WorkInfo.State.ENQUEUED, env.work(WEATHER_WIDGET_UPDATE_WORK).single().state)
        assertEquals(WorkInfo.State.ENQUEUED, env.work(WARNINGS_WIDGET_UPDATE_WORK).single().state)
    }

    @Test fun removingLastWidgetCancelsOnlyItsOwnType() {
        env.bind(1, MediumForecastWidgetProvider::class.java)
        env.bind(2, MediumWarningsWidgetProvider::class.java)
        WidgetNotification.scheduleActiveWidgetUpdates(env.context)
        // Rebinding the ID simulates AppWidgetManager no longer reporting the removed provider.
        env.bind(2, MediumForecastWidgetProvider::class.java)

        MediumWarningsWidgetProvider().onDisabled(env.context)

        assertEquals(WorkInfo.State.CANCELLED, env.work(WARNINGS_WIDGET_UPDATE_WORK).single().state)
        assertEquals(WorkInfo.State.ENQUEUED, env.work(WEATHER_WIDGET_UPDATE_WORK).single().state)
    }

    private fun assertSchedule(name: String, minutes: Long, worker: String) {
        val info = env.work(name).single()
        val spec = env.workManager.workDatabase.workSpecDao().getWorkSpec(info.id.toString())!!
        assertEquals(TimeUnit.MINUTES.toMillis(minutes), spec.intervalDuration)
        assertEquals(worker, spec.workerClassName)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
    }
}
