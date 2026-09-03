package fi.fmi.mobileweather.widgets;

import static android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE;
import static android.content.res.Configuration.UI_MODE_NIGHT_MASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static fi.fmi.mobileweather.widgets.model.LocationConstants.CURRENT_LOCATION;
import static fi.fmi.mobileweather.widgets.model.PrefKey.*;
import static fi.fmi.mobileweather.widgets.WidgetNotification.ACTION_APPWIDGET_AUTO_UPDATE;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import fi.fmi.mobileweather.widgets.enumeration.WidgetType;
import fi.fmi.mobileweather.widgets.model.Announcement;
import fi.fmi.mobileweather.widgets.model.ForecastItem;
import fi.fmi.mobileweather.widgets.model.LocationRecord;
import fi.fmi.mobileweather.widgets.model.WarningsRecordRoot;
import fi.fmi.mobileweather.widgets.model.WidgetData;
import fi.fmi.mobileweather.widgets.repository.WeatherRepository;
import fi.fmi.mobileweather.widgets.util.AirplaneModeUtil;
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper;
import fi.fmi.mobileweather.widgets.util.SingleShotLocationProvider;

public abstract class BaseWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "BaseWidgetProvider";
    private static final long FORECAST_DATA_VALIDITY = 24 * 60 * 60 * 1000;
    private static final long WARNING_DATA_VALIDITY = 12 * 60 * 60 * 1000;

    private final WeatherRepository weatherRepository = new WeatherRepository();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    protected abstract WidgetType getWidgetType();
    protected abstract int getLayoutResourceId();

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_APPWIDGET_AUTO_UPDATE.equals(action) || ACTION_APPWIDGET_UPDATE.equals(action)) {
            triggerUpdate(context);
        }
    }

    private void triggerUpdate(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, getClass());
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId);
        }
    }

    protected void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferencesHelper pref = SharedPreferencesHelper.getInstance(context, appWidgetId);
        int selectedLocation = pref.getInt(SELECTED_LOCATION, Integer.MAX_VALUE);

        if (selectedLocation == CURRENT_LOCATION) {
            handleCurrentLocationUpdate(context, appWidgetManager, appWidgetId, pref);
        } else if (selectedLocation != Integer.MAX_VALUE) {
            fetchDataAndUpdate(context, appWidgetManager, appWidgetId, null, selectedLocation);
        }
    }

    private void handleCurrentLocationUpdate(Context context, AppWidgetManager appWidgetManager, int appWidgetId, SharedPreferencesHelper pref) {
        if (!checkLocationPermissions(context)) {
            showLocationErrorView(context, appWidgetManager, pref, appWidgetId);
            return;
        }

        SingleShotLocationProvider.requestSingleUpdate(context, new SingleShotLocationProvider.LocationCallback() {
            @Override
            public void onNewLocationAvailable(Location location) {
                String latlon = getLatLonString(location);
                pref.saveString("latlon", latlon);
                fetchDataAndUpdate(context, appWidgetManager, appWidgetId, latlon, null);
            }

            @Override
            public void onLocationFailed() {
                String storedLatLon = pref.getString("latlon", null);
                if (storedLatLon != null) {
                    fetchDataAndUpdate(context, appWidgetManager, appWidgetId, storedLatLon, null);
                } else {
                    showLocationErrorView(context, appWidgetManager, pref, appWidgetId);
                }
            }
        });
    }

    private void fetchDataAndUpdate(Context context, AppWidgetManager appWidgetManager, int appWidgetId, String latlon, Integer geoId) {
        WeatherRepository.WeatherCallback callback = new WeatherRepository.WeatherCallback() {
            @Override
            public void onSuccess(WidgetData data) {
                mainHandler.post(() -> updateUI(context, appWidgetManager, appWidgetId, data));
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
                    SharedPreferencesHelper pref = SharedPreferencesHelper.getInstance(context, appWidgetId);
                    WidgetData cachedData = getCachedData(pref);
                    if (cachedData != null) {
                        updateUI(context, appWidgetManager, appWidgetId, cachedData);
                    } else {
                        showErrorView(context, appWidgetManager, pref, 
                            context.getString(getWidgetType() == WidgetType.WARNINGS ? R.string.failed_to_load_alerts : R.string.update_failed), 
                            getConnectionErrorDescription(context), appWidgetId);
                    }
                });
            }
        };

        if (getWidgetType() == WidgetType.WARNINGS) {
            weatherRepository.fetchWarningsData(context, latlon, callback);
        } else {
            weatherRepository.fetchForecastData(context, latlon, geoId, callback);
        }
    }

    private void updateUI(Context context, AppWidgetManager appWidgetManager, int appWidgetId, WidgetData data) {
        SharedPreferencesHelper pref = SharedPreferencesHelper.getInstance(context, appWidgetId);
        cacheData(pref, data);
        
        WidgetInitResult initResult = initWidget(context, null, pref, appWidgetId);
        setWidgetUi(context, appWidgetManager, data, pref, initResult, appWidgetId);
    }

    private void cacheData(SharedPreferencesHelper pref, WidgetData data) {
        if (data.forecast() != null) {
            pref.saveString(LATEST_JSON, gson.toJson(data.forecast()));
            pref.saveLong(LATEST_JSON_UPDATED, System.currentTimeMillis());
        }
        if (data.warnings() != null) {
            pref.saveString("latest_warnings_json", gson.toJson(data.warnings()));
            pref.saveLong("latest_warnings_json_updated", System.currentTimeMillis());
        }
        if (data.announcements() != null) {
            pref.saveString("latest_crisis_json", gson.toJson(data.announcements()));
            pref.saveLong("latest_crisis_json_updated", System.currentTimeMillis());
        }
        if (data.location() != null) {
            pref.saveString(WARNING_LOCATION, gson.toJson(data.location()));
        }
    }

    private WidgetData getCachedData(SharedPreferencesHelper pref) {
        long now = System.currentTimeMillis();
        try {
            List<ForecastItem> forecast = null;
            WarningsRecordRoot warnings = null;
            List<Announcement> announcements = null;
            List<LocationRecord> location = null;

            long forecastUpdated = pref.getLong(LATEST_JSON_UPDATED, 0);
            if (forecastUpdated > (now - FORECAST_DATA_VALIDITY)) {
                forecast = gson.fromJson(pref.getString(LATEST_JSON, null), new TypeToken<List<ForecastItem>>(){}.getType());
            }

            long warningsUpdated = pref.getLong("latest_warnings_json_updated", 0);
            if (warningsUpdated > (now - WARNING_DATA_VALIDITY)) {
                warnings = gson.fromJson(pref.getString("latest_warnings_json", null), WarningsRecordRoot.class);
                location = gson.fromJson(pref.getString(WARNING_LOCATION, null), new TypeToken<List<LocationRecord>>(){}.getType());
            }

            long crisisUpdated = pref.getLong("latest_crisis_json_updated", 0);
            if (crisisUpdated > (now - WARNING_DATA_VALIDITY)) {
                announcements = gson.fromJson(pref.getString("latest_crisis_json", null), new TypeToken<List<Announcement>>(){}.getType());
            }

            if (forecast != null || warnings != null) {
                return new WidgetData(announcements, forecast, warnings, location);
            }
        } catch (Exception ignored) {}
        return null;
    }

    protected abstract void setWidgetUi(Context context, AppWidgetManager manager, WidgetData data, SharedPreferencesHelper pref, WidgetInitResult initResult, int widgetId);

    protected WidgetInitResult initWidget(Context context, RemoteViews views, SharedPreferencesHelper pref, int widgetId) {
        if (views == null) {
            int layoutId = pref.getInt(LAYOUT_RES_ID, getLayoutResourceId());
            views = new RemoteViews(context.getPackageName(), layoutId);
        }

        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.mainLinearLayout, pi);

        int nightMode = context.getResources().getConfiguration().uiMode & UI_MODE_NIGHT_MASK;
        boolean gradient = nightMode == UI_MODE_NIGHT_YES && pref.getInt(GRADIENT_BACKGROUND, 0) == 1;
        views.setInt(R.id.mainLinearLayout, "setBackgroundResource", gradient ? R.drawable.gradient_background : R.color.widgetBackground);
        
        views.setViewVisibility(R.id.normalLayout, VISIBLE);
        views.setViewVisibility(R.id.errorLayout, GONE);

        return new WidgetInitResult(views, gradient);
    }

    private void showLocationErrorView(Context context, AppWidgetManager manager, SharedPreferencesHelper pref, int widgetId) {
        boolean hasPerms = checkLocationPermissions(context);
        showErrorView(context, manager, pref, 
            context.getString(R.string.location_failed),
            hasPerms ? context.getString(R.string.retrying_location_services) : context.getString(R.string.location_services_not_allowed),
            widgetId);
    }

    protected void showErrorView(Context context, AppWidgetManager manager, SharedPreferencesHelper pref, String error1, String error2, int widgetId) {
        long updated = pref.getLong(WIDGET_UI_UPDATED, 0);
        long validity = getWidgetType() == WidgetType.WEATHER_FORECAST ? FORECAST_DATA_VALIDITY : WARNING_DATA_VALIDITY;

        if (updated > 0 && (System.currentTimeMillis() - updated < validity)) return;

        RemoteViews views = new RemoteViews(context.getPackageName(), getLayoutResourceId());
        views.setViewVisibility(R.id.errorLayout, VISIBLE);
        views.setViewVisibility(R.id.normalLayout, GONE);
        views.setTextViewText(R.id.errorHeaderTextView, error1);
        views.setTextViewText(R.id.errorBodyTextView, error2);

        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.mainLinearLayout, pi);

        manager.updateAppWidget(widgetId, views);
    }

    private boolean checkLocationPermissions(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private String getLatLonString(Location loc) {
        return String.format(Locale.US, "%.4f,%.4f", loc.getLatitude(), loc.getLongitude());
    }

    protected String getConnectionErrorDescription(Context context) {
        if (getWidgetType() == WidgetType.WARNINGS) {
            return AirplaneModeUtil.isAirplaneModeOn(context) ? context.getString(R.string.airplane_mode_warnings) : context.getString(R.string.automatic_retry_warnings);
        }
        return AirplaneModeUtil.isAirplaneModeOn(context) ? context.getString(R.string.airplane_mode) : context.getString(R.string.automatic_retry);
    }

    protected record WidgetInitResult(RemoteViews widgetRemoteViews, boolean gradientBackground) {}
}
