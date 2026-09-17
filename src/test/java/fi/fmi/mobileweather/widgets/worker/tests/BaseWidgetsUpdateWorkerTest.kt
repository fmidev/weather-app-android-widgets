package fi.fmi.mobileweather.widgets.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import fi.fmi.mobileweather.widgets.BaseWidgetProvider
import fi.fmi.mobileweather.widgets.BaseWidgetProvider.UpdateResult
import fi.fmi.mobileweather.widgets.SmallForecastWidgetProvider
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class BaseWidgetsUpdateWorkerTest {
    private lateinit var context: Context
    private lateinit var provider: RecordingProvider

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        provider = RecordingProvider()
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        manager.bindAppWidgetId(1, ComponentName(context, RecordingProvider::class.java))
        manager.bindAppWidgetId(2, ComponentName(context, RecordingProvider::class.java))
    }

    @Test fun waitsForEveryWidgetBeforeReportingSuccess() {
        val result = worker().startWork()

        assertEquals(setOf(1, 2), provider.updates.keys)
        assertFalse(result.isDone)
        provider.updates.getValue(1).set(UpdateResult.SUCCESS)
        assertFalse(result.isDone)
        provider.updates.getValue(2).set(UpdateResult.SUCCESS)
        assertEquals(Result.success(), result.get())
    }

    @Test fun retryWaitsForTheOtherWidgetsToFinish() {
        val result = worker().startWork()
        provider.updates.getValue(1).set(UpdateResult.RETRY)
        assertFalse(result.isDone)
        provider.updates.getValue(2).set(UpdateResult.SUCCESS)
        assertEquals(Result.retry(), result.get())
    }

    @Test fun unexpectedFailureDoesNotLeaveOtherUpdatesOutsideTheWorkerLifecycle() {
        val result = worker().startWork()
        provider.updates.getValue(1).setException(IOException("Test failure"))
        assertFalse(result.isDone)
        provider.updates.getValue(2).set(UpdateResult.SUCCESS)
        assertEquals(Result.retry(), result.get())
    }

    @Test fun permanentFailureDoesNotRequestAnAutomaticRetry() {
        val result = worker().startWork()
        provider.updates.getValue(1).set(UpdateResult.FAILURE)
        provider.updates.getValue(2).set(UpdateResult.SUCCESS)
        assertEquals(Result.failure(), result.get())
    }

    @Test fun oneTimeWorkRefreshesOnlyRequestedActiveIds() {
        val input = Data.Builder().putIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(2, 99)).build()
        val result = worker(input).startWork()
        assertEquals(setOf(2), provider.updates.keys)
        provider.updates.getValue(2).set(UpdateResult.SUCCESS)
        assertEquals(Result.success(), result.get())
    }

    @Test fun deletedWidgetDoesNotStartAnUpdate() {
        val input = Data.Builder().putIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(99)).build()
        assertEquals(Result.success(), worker(input).startWork().get())
        assertTrue(provider.updates.isEmpty())
    }

    @Test fun stoppingWorkerCancelsAllUnfinishedUpdates() {
        val worker = worker()
        val result = worker.startWork()
        worker.onStopped()
        assertTrue(result.isCancelled)
        assertTrue(provider.updates.values.all { it.isCancelled })
    }

    @Test fun cancellingReturnedFutureCancelsAllUpdates() {
        val result = worker().startWork()
        result.cancel(true)
        assertTrue(provider.updates.values.all { it.isCancelled })
    }

    @Test fun stoppedWorkerDoesNotStartNewUpdates() {
        val worker = worker()
        worker.onStopped()
        assertTrue(worker.startWork().isCancelled)
        assertTrue(provider.updates.isEmpty())
    }

    private fun worker(input: Data = Data.EMPTY): TestWorker {
        return TestListenableWorkerBuilder.from(context, TestWorker::class.java)
            .setInputData(input).build().also { it.testProviders = listOf(provider) }
    }

    class TestWorker(context: Context, params: WorkerParameters) : BaseWidgetsUpdateWorker(context, params) {
        var testProviders: List<BaseWidgetProvider> = emptyList()
        override fun providers() = testProviders
    }

    class RecordingProvider : SmallForecastWidgetProvider() {
        internal val updates = mutableMapOf<Int, SettableFuture<UpdateResult>>()
        internal override fun refreshWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): ListenableFuture<UpdateResult> {
            return SettableFuture.create<UpdateResult>().also { updates[appWidgetId] = it }
        }
    }
}
