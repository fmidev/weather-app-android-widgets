package fi.fmi.mobileweather.widgets

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.fmi.mobileweather.widgets.enumeration.WidgetType
import fi.fmi.mobileweather.widgets.model.Announcement
import fi.fmi.mobileweather.widgets.model.ForecastItem
import fi.fmi.mobileweather.widgets.model.LocationConstants.CURRENT_LOCATION
import fi.fmi.mobileweather.widgets.model.LocationRecord
import fi.fmi.mobileweather.widgets.model.PrefKey.FAVORITE_LATLON
import fi.fmi.mobileweather.widgets.model.PrefKey.GRADIENT_BACKGROUND
import fi.fmi.mobileweather.widgets.model.PrefKey.LATEST_JSON
import fi.fmi.mobileweather.widgets.model.PrefKey.LATEST_JSON_UPDATED
import fi.fmi.mobileweather.widgets.model.PrefKey.LAYOUT_RES_ID
import fi.fmi.mobileweather.widgets.model.PrefKey.SELECTED_LOCATION
import fi.fmi.mobileweather.widgets.model.PrefKey.TRANSPARENT_BACKGROUND
import fi.fmi.mobileweather.widgets.model.PrefKey.WARNING_LOCATION
import fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED
import fi.fmi.mobileweather.widgets.model.WarningsRecordRoot
import fi.fmi.mobileweather.widgets.model.WidgetData
import fi.fmi.mobileweather.widgets.repository.WeatherRepository
import fi.fmi.mobileweather.widgets.util.AirplaneModeUtil
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import fi.fmi.mobileweather.widgets.util.SingleShotLocationProvider
import fi.fmi.mobileweather.widgets.util.WidgetBackground
import java.util.Locale

abstract class BaseWidgetProvider : AppWidgetProvider() {

    private val weatherRepository = WeatherRepository()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    protected abstract fun getWidgetType(): WidgetType
    protected abstract fun getLayoutResourceId(): Int

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        // AppWidgetProvider already handles updates with explicit IDs in super.onReceive.
        if (WidgetNotification.ACTION_APPWIDGET_AUTO_UPDATE == action ||
            (AppWidgetManager.ACTION_APPWIDGET_UPDATE == action &&
                intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)?.isNotEmpty() != true)
        ) {
            triggerUpdate(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetNotification.scheduleWidgetUpdate(context, javaClass, getWidgetType())
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetNotification.clearWidgetUpdate(context, getWidgetType())
    }

    private fun triggerUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, javaClass)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetNotification.scheduleWidgetUpdate(context, javaClass, getWidgetType())
        for (widgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    protected open fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val pref = SharedPreferencesHelper.getInstance(context, appWidgetId)
        val selectedLocation = pref.getInt(SELECTED_LOCATION, Int.MAX_VALUE)

        if (selectedLocation == CURRENT_LOCATION) {
            handleCurrentLocationUpdate(context, appWidgetManager, appWidgetId, pref)
        } else if (selectedLocation != Int.MAX_VALUE) {
            val latlon = pref.getString(FAVORITE_LATLON, null)
            fetchDataAndUpdate(context, appWidgetManager, appWidgetId, latlon)
        }
    }

    private fun handleCurrentLocationUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        pref: SharedPreferencesHelper
    ) {
        if (!checkLocationPermissions(context)) {
            showLocationErrorView(context, appWidgetManager, pref, appWidgetId)
            return
        }

        SingleShotLocationProvider.requestSingleUpdate(context, object : SingleShotLocationProvider.LocationCallback {
            override fun onNewLocationAvailable(location: Location) {
                val latlon = getLatLonString(location)
                pref.saveString("latlon", latlon)
                fetchDataAndUpdate(context, appWidgetManager, appWidgetId, latlon)
            }

            override fun onLocationFailed() {
                val storedLatLon = pref.getString("latlon", null)
                if (storedLatLon != null) {
                    fetchDataAndUpdate(context, appWidgetManager, appWidgetId, storedLatLon)
                } else {
                    showLocationErrorView(context, appWidgetManager, pref, appWidgetId)
                }
            }
        })
    }

    private fun fetchDataAndUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        latlon: String?
    ) {
        val callback = object : WeatherRepository.WeatherCallback {
            override fun onSuccess(data: WidgetData) {
                mainHandler.post { updateUI(context, appWidgetManager, appWidgetId, data) }
            }

            override fun onError(e: Exception) {
                mainHandler.post {
                    val pref = SharedPreferencesHelper.getInstance(context, appWidgetId)
                    val cachedData = getCachedData(pref)
                    if (cachedData != null) {
                        updateUI(context, appWidgetManager, appWidgetId, cachedData)
                    } else {
                        showErrorView(
                            context, appWidgetManager, pref,
                            context.getString(
                                if (getWidgetType() == WidgetType.WARNINGS) R.string.failed_to_load_alerts else R.string.update_failed
                            ),
                            getConnectionErrorDescription(context), appWidgetId
                        )
                    }
                }
            }
        }

        if (latlon.isNullOrBlank()) {
            callback.onError(IllegalArgumentException("Widget coordinates not available"))
            return
        }

        if (getWidgetType() == WidgetType.WARNINGS) {
            weatherRepository.fetchWarningsData(context, latlon, callback)
        } else {
            weatherRepository.fetchForecastData(context, latlon, callback)
        }
    }

    private fun updateUI(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        data: WidgetData
    ) {
        val pref = SharedPreferencesHelper.getInstance(context, appWidgetId)
        cacheData(pref, data)

        val initResult = initWidget(context, null, pref, appWidgetId)
        setWidgetUi(context, appWidgetManager, data, pref, initResult, appWidgetId)
    }

    private fun cacheData(pref: SharedPreferencesHelper, data: WidgetData) {
        if (data.forecast != null) {
            pref.saveString(LATEST_JSON, gson.toJson(data.forecast))
            pref.saveLong(LATEST_JSON_UPDATED, System.currentTimeMillis())
        }
        if (data.warnings != null) {
            pref.saveString("latest_warnings_json", gson.toJson(data.warnings))
            pref.saveLong("latest_warnings_json_updated", System.currentTimeMillis())
        }
        if (data.announcements != null) {
            pref.saveString("latest_crisis_json", gson.toJson(data.announcements))
            pref.saveLong("latest_crisis_json_updated", System.currentTimeMillis())
        }
        if (data.location != null) {
            pref.saveString(WARNING_LOCATION, gson.toJson(data.location))
        }
    }

    private fun getCachedData(pref: SharedPreferencesHelper): WidgetData? {
        val now = System.currentTimeMillis()
        try {
            var forecast: List<ForecastItem>? = null
            var warnings: WarningsRecordRoot? = null
            var announcements: List<Announcement>? = null
            var location: List<LocationRecord>? = null

            val forecastUpdated = pref.getLong(LATEST_JSON_UPDATED, 0)
            if (forecastUpdated > now - FORECAST_DATA_VALIDITY) {
                val type = object : TypeToken<List<ForecastItem>>() {}.type
                forecast = gson.fromJson(pref.getString(LATEST_JSON, null), type)
            }

            val warningsUpdated = pref.getLong("latest_warnings_json_updated", 0)
            if (warningsUpdated > now - WARNING_DATA_VALIDITY) {
                warnings = gson.fromJson(pref.getString("latest_warnings_json", null), WarningsRecordRoot::class.java)
                val type = object : TypeToken<List<LocationRecord>>() {}.type
                location = gson.fromJson(pref.getString(WARNING_LOCATION, null), type)
            }

            val crisisUpdated = pref.getLong("latest_crisis_json_updated", 0)
            if (crisisUpdated > now - WARNING_DATA_VALIDITY) {
                val type = object : TypeToken<List<Announcement>>() {}.type
                announcements = gson.fromJson(pref.getString("latest_crisis_json", null), type)
            }

            if (forecast != null || warnings != null) {
                return WidgetData(announcements, forecast, warnings, location)
            }
        } catch (_: Exception) {
        }
        return null
    }

    protected abstract fun setWidgetUi(
        context: Context,
        manager: AppWidgetManager,
        data: WidgetData,
        pref: SharedPreferencesHelper,
        initResult: WidgetInitResult,
        widgetId: Int
    )

    protected open fun initWidget(
        context: Context,
        views: RemoteViews?,
        pref: SharedPreferencesHelper,
        widgetId: Int
    ): WidgetInitResult {
        var remoteViews = views
        if (remoteViews == null) {
            val layoutId = pref.getInt(LAYOUT_RES_ID, getLayoutResourceId())
            remoteViews = RemoteViews(context.packageName, layoutId)
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        remoteViews.setOnClickPendingIntent(R.id.mainLinearLayout, pi)

        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val backgroundResource = WidgetBackground.getResourceId(
            isNightMode = nightMode == Configuration.UI_MODE_NIGHT_YES,
            gradientEnabled = pref.getInt(GRADIENT_BACKGROUND, 0) == 1,
            transparentEnabled = pref.getInt(TRANSPARENT_BACKGROUND, 0) == 1
        )
        remoteViews.setInt(
            R.id.mainLinearLayout,
            "setBackgroundResource",
            backgroundResource
        )

        remoteViews.setViewVisibility(R.id.normalLayout, VISIBLE)
        remoteViews.setViewVisibility(R.id.errorLayout, GONE)

        return WidgetInitResult(remoteViews, backgroundResource == R.drawable.gradient_background)
    }

    private fun showLocationErrorView(
        context: Context,
        manager: AppWidgetManager,
        pref: SharedPreferencesHelper,
        widgetId: Int
    ) {
        val hasPerms = checkLocationPermissions(context)
        showErrorView(
            context, manager, pref,
            context.getString(R.string.location_failed),
            if (hasPerms) context.getString(R.string.retrying_location_services) else context.getString(R.string.location_services_not_allowed),
            widgetId
        )
    }

    protected open fun showErrorView(
        context: Context,
        manager: AppWidgetManager,
        pref: SharedPreferencesHelper,
        error1: String,
        error2: String,
        widgetId: Int
    ) {
        val updated = pref.getLong(WIDGET_UI_UPDATED, 0)
        val validity = if (getWidgetType() == WidgetType.WEATHER_FORECAST) FORECAST_DATA_VALIDITY else WARNING_DATA_VALIDITY

        if (updated > 0 && System.currentTimeMillis() - updated < validity) return

        val views = RemoteViews(context.packageName, getLayoutResourceId())
        initWidget(context, views, pref, widgetId)
        views.setViewVisibility(R.id.errorLayout, VISIBLE)
        views.setViewVisibility(R.id.normalLayout, GONE)
        views.setTextViewText(R.id.errorHeaderTextView, error1)
        views.setTextViewText(R.id.errorBodyTextView, error2)

        manager.updateAppWidget(widgetId, views)
    }

    private fun checkLocationPermissions(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun getLatLonString(loc: Location): String {
        return String.format(Locale.US, "%.4f,%.4f", loc.latitude, loc.longitude)
    }

    protected open fun getConnectionErrorDescription(context: Context): String {
        return if (getWidgetType() == WidgetType.WARNINGS) {
            if (AirplaneModeUtil.isAirplaneModeOn(context)) context.getString(R.string.airplane_mode_warnings) else context.getString(R.string.automatic_retry_warnings)
        } else {
            if (AirplaneModeUtil.isAirplaneModeOn(context)) context.getString(R.string.airplane_mode) else context.getString(R.string.automatic_retry)
        }
    }

    data class WidgetInitResult(
        val widgetRemoteViews: RemoteViews,
        val gradientBackground: Boolean
    )

    companion object {
        private const val TAG = "BaseWidgetProvider"
        private const val FORECAST_DATA_VALIDITY = 24 * 60 * 60 * 1000L
        private const val WARNING_DATA_VALIDITY = 12 * 60 * 60 * 1000L
    }
}
