package fi.fmi.mobileweather.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import androidx.work.Configuration
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

internal class WidgetWorkTestEnvironment {
    val context: Context = RuntimeEnvironment.getApplication()
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val workManager: WorkManagerImpl
    private val setupField = WidgetSetupManager::class.java.getDeclaredField("widgetSetup").apply {
        isAccessible = true
    }
    private val originalSetup = setupField.get(WidgetSetupManager)

    init {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build())
        workManager = WorkManagerImpl.getInstance(context)
        setup(WidgetSetup(weather = WidgetSetup.Weather(interval = 30), warnings = WidgetSetup.Warnings(interval = 45)))
    }

    fun setup(value: WidgetSetup) = setupField.set(WidgetSetupManager, value)

    fun bind(id: Int, provider: Class<out AppWidgetProvider>) {
        shadowOf(manager).bindAppWidgetId(id, ComponentName(context, provider))
    }

    fun work(name: String) = workManager.getWorkInfosForUniqueWork(name).get()

    fun close() {
        workManager.cancelAllWork().result.get()
        workManager.workDatabase.close()
        WorkManagerImpl.setDelegate(null)
        setupField.set(WidgetSetupManager, originalSetup)
    }
}
