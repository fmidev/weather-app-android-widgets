package fi.fmi.mobileweather.widgets.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import fi.fmi.mobileweather.widgets.WidgetSetup;
import fi.fmi.mobileweather.widgets.WidgetSetupManager;
import fi.fmi.mobileweather.widgets.model.Announcement;
import fi.fmi.mobileweather.widgets.model.ForecastItem;
import fi.fmi.mobileweather.widgets.model.LocationRecord;
import fi.fmi.mobileweather.widgets.model.WarningsRecordRoot;
import fi.fmi.mobileweather.widgets.model.WidgetData;

public class WeatherRepository {
    private static final String TAG = "WeatherRepository";
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Gson gson = new Gson();

    public interface WeatherCallback {
        void onSuccess(WidgetData data);
        void onError(Exception e);
    }

    public void fetchForecastData(Context context, @Nullable String latlon, @Nullable Integer geoId, WeatherCallback callback) {
        WidgetSetup setup = WidgetSetupManager.getWidgetSetup(context);
        if (setup == null) {
            callback.onError(new Exception("Widget setup not available"));
            return;
        }

        String language = getLanguageString();
        String weatherUrl = setup.weather().apiUrl();
        String announcementsUrl = getAnnouncementsUrl(setup, language);

        executorService.submit(() -> {
            try {
                String finalGeoId = (geoId != null) ? String.valueOf(geoId) : (latlon != null ? fetchGeoid(weatherUrl, latlon) : null);

                Future<List<ForecastItem>> forecastFuture = executorService.submit(() -> fetchForecast(weatherUrl, finalGeoId, latlon, language));
                Future<List<Announcement>> announcementsFuture = executorService.submit(() -> fetchAnnouncements(announcementsUrl));

                List<Announcement> announcements = Collections.emptyList();
                try { announcements = announcementsFuture.get(); } catch (Exception ignored) {}
                
                List<ForecastItem> forecast = forecastFuture.get();
                if (forecast == null || forecast.isEmpty()) throw new Exception("Forecast fetch failed");

                callback.onSuccess(new WidgetData(announcements, forecast));
            } catch (Exception e) {
                Log.e(TAG, "Error fetching weather data", e);
                callback.onError(e);
            }
        });
    }

    public void fetchWarningsData(Context context, String latlon, WeatherCallback callback) {
        WidgetSetup setup = WidgetSetupManager.getWidgetSetup(context);
        if (setup == null) {
            callback.onError(new Exception("Widget setup not available"));
            return;
        }

        String language = getLanguageString();
        String weatherUrl = setup.weather().apiUrl();
        String warningsUrl = setup.warnings().apiUrl();
        String announcementsUrl = getAnnouncementsUrl(setup, language);

        executorService.submit(() -> {
            try {
                Future<WarningsRecordRoot> warningsFuture = executorService.submit(() -> fetchWarnings(warningsUrl, latlon, language));
                Future<List<Announcement>> announcementsFuture = executorService.submit(() -> fetchAnnouncements(announcementsUrl));
                Future<List<LocationRecord>> locationFuture = executorService.submit(() -> fetchLocationInfo(weatherUrl, latlon));

                List<Announcement> announcements = Collections.emptyList();
                try { announcements = announcementsFuture.get(); } catch (Exception ignored) {}
                
                WarningsRecordRoot warnings = warningsFuture.get();
                List<LocationRecord> locations = locationFuture.get();

                if (warnings == null) throw new Exception("Warnings fetch failed");

                callback.onSuccess(new WidgetData(announcements, null, warnings, locations));
            } catch (Exception e) {
                Log.e(TAG, "Error fetching warnings data", e);
                callback.onError(e);
            }
        });
    }

    private String getAnnouncementsUrl(WidgetSetup setup, String language) {
        if (setup.announcements() == null || setup.announcements().api() == null) return null;
        return switch (language) {
            case "fi" -> setup.announcements().api().fi();
            case "sv" -> setup.announcements().api().sv();
            default -> setup.announcements().api().en();
        };
    }

    private String getLanguageString() {
        String language = Locale.getDefault().getLanguage();
        if (!language.equals("fi") && !language.equals("sv") && !language.equals("en"))
            language = "en";
        return language;
    }

    private String fetchGeoid(String weatherUrl, String latlon) {
        String url = weatherUrl + "?param=geoid&latlon=" + latlon + "&format=json";
        String json = fetchJsonString(url);
        if (json == null) return null;
        try {
            Type type = new TypeToken<List<Map<String, String>>>(){}.getType();
            List<Map<String, String>> list = gson.fromJson(json, type);
            if (list != null && !list.isEmpty()) return list.get(0).get("geoid");
        } catch (Exception ignored) {}
        return null;
    }

    private List<ForecastItem> fetchForecast(String weatherUrl, String geoid, String latlon, String language) {
        var params = "geoid,epochtime,localtime,utctime,name,region,iso2,temperature,feelsLike,smartSymbol,windDirection,windSpeedMS,windCompass8";
        String url;
        if (geoid != null && !geoid.isEmpty()) {
            url = weatherUrl + "?geoid=" + geoid + "&endtime=data&format=json&attributes=geoid&lang=" + language + "&param=" + params;
        } else if (latlon != null && !latlon.isEmpty()) {
            url = weatherUrl + "?latlon=" + latlon + "&endtime=data&format=json&attributes=geoid&lang=" + language + "&param=" + params;
        } else return null;

        String json = fetchJsonString(url);
        if (json == null) return null;
        try {
            Type type = new TypeToken<Map<String, List<ForecastItem>>>(){}.getType();
            Map<String, List<ForecastItem>> map = gson.fromJson(json, type);
            if (map != null && !map.isEmpty()) return map.values().iterator().next();
        } catch (Exception e) {
            Log.e(TAG, "Forecast parsing failed", e);
        }
        return null;
    }

    private WarningsRecordRoot fetchWarnings(String warningsUrl, String latlon, String language) {
        String url = warningsUrl + "?latlon=" + latlon + "&country=" + language;
        String json = fetchJsonString(url);
        if (json == null) return null;
        try {
            return gson.fromJson(json, WarningsRecordRoot.class);
        } catch (Exception e) {
            return null;
        }
    }

    private List<LocationRecord> fetchLocationInfo(String weatherUrl, String latlon) {
        String url = weatherUrl + "?param=geoid,name,region,iso2&latlon=" + latlon + "&format=json";
        String json = fetchJsonString(url);
        if (json == null) return null;
        try {
            Type type = new TypeToken<List<LocationRecord>>(){}.getType();
            return gson.fromJson(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Announcement> fetchAnnouncements(String src) {
        String json = fetchJsonString(src);
        if (json == null) return Collections.emptyList();
        try {
            Type type = new TypeToken<List<Announcement>>(){}.getType();
            return gson.fromJson(json, type);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String fetchJsonString(String src) {
        if (src == null || src.isEmpty()) return null;
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                return response.toString();
            }
        } catch (IOException e) {
            Log.e(TAG, "Network error fetching " + src, e);
            return null;
        }
    }
}
