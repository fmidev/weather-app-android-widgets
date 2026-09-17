package fi.fmi.mobileweather.widgets.worker

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import fi.fmi.mobileweather.widgets.BaseWidgetProvider
import fi.fmi.mobileweather.widgets.BaseWidgetProvider.UpdateResult
import fi.fmi.mobileweather.widgets.WidgetNotification

abstract class BaseWidgetsUpdateWorker(context: Context, params: WorkerParameters) : ListenableWorker(context, params) {
    private val completion = SettableFuture.create<Result>()

    protected abstract fun providers(): List<BaseWidgetProvider>

    override fun startWork(): ListenableFuture<Result> {
        if (completion.isCancelled) return completion
        val updates = mutableListOf<ListenableFuture<UpdateResult>>()
        try {
            val manager = AppWidgetManager.getInstance(applicationContext)
            // Periodic work updates all widgets; one-time work carries the requested IDs.
            val requestedIds = inputData.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS)?.toSet()
            for (provider in providers()) {
                val ids = WidgetNotification.getActiveWidgetIds(applicationContext, provider.javaClass)
                for (id in ids) {
                    if (requestedIds == null || id in requestedIds) {
                        updates.add(Futures.catching(
                            provider.refreshWidget(applicationContext, manager, id),
                            Exception::class.java, { UpdateResult.RETRY }, MoreExecutors.directExecutor()
                        ))
                    }
                }
            }
            val aggregate = Futures.transform(Futures.allAsList(updates), { results ->
                when {
                    results!!.contains(UpdateResult.RETRY) -> Result.retry()
                    results.contains(UpdateResult.FAILURE) -> Result.failure()
                    else -> Result.success()
                }
            }, MoreExecutors.directExecutor())
            completion.setFuture(aggregate)
        } catch (e: Exception) {
            updates.forEach { it.cancel(true) }
            completion.set(Result.retry())
        }
        return completion
    }

    override fun onStopped() {
        // Cancellation propagates through the aggregate to location and network requests.
        completion.cancel(true)
        super.onStopped()
    }
}
