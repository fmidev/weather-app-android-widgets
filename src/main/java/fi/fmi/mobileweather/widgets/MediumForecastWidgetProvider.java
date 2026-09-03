package fi.fmi.mobileweather.widgets;

import static android.view.View.GONE;
import static fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

import fi.fmi.mobileweather.widgets.enumeration.WidgetType;
import fi.fmi.mobileweather.widgets.model.Announcement;
import fi.fmi.mobileweather.widgets.model.ForecastItem;
import fi.fmi.mobileweather.widgets.model.WidgetData;
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper;

public class MediumForecastWidgetProvider extends BaseWidgetProvider {
    private static final String TAG = "MediumWidgetProvider";

    @Override
    protected WidgetType getWidgetType() {
        return WidgetType.WEATHER_FORECAST;
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.medium_forecast_widget_layout;
    }

    private double getTimestepCount(int widgetWidth) {
        final int columnWidth = 52;
        final int margins = 32;
        return Math.floor((widgetWidth - margins) / (double)columnWidth);
    }

    @Override
    protected void setWidgetUi(Context context, AppWidgetManager appWidgetManager, WidgetData widgetData, SharedPreferencesHelper pref, WidgetInitResult widgetInitResult, int appWidgetId) {
        RemoteViews views = widgetInitResult.widgetRemoteViews();
        List<ForecastItem> forecastItems = widgetData.forecast();
        int width = appWidgetManager.getAppWidgetOptions(appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        double timeStepCount = getTimestepCount(width);

        try {
            if (forecastItems == null || forecastItems.isEmpty()) return;
            
            int firstFutureIndex = -1;
            long now = System.currentTimeMillis();
            for (int i = 0; i < forecastItems.size(); i++) {
                if (forecastItems.get(i).epochtime() * 1000 > now) {
                    firstFutureIndex = i;
                    break;
                }
            }

            if (firstFutureIndex == -1 || forecastItems.size() < (firstFutureIndex + 1)) {
                showErrorView(context, appWidgetManager, pref, context.getString(R.string.update_failed), "", appWidgetId);
                return;
            }

            views.removeAllViews(R.id.hourForecastRowLayout);

            for (int i = firstFutureIndex; i < Math.min(forecastItems.size(), firstFutureIndex + timeStepCount); i++) {
                ForecastItem forecast = forecastItems.get(i);
                if (i == firstFutureIndex) {
                    views.setTextViewText(R.id.locationNameTextView, forecast.name() + ", ");
                    views.setTextViewText(R.id.locationRegionTextView, forecast.region());
                }

                RemoteViews step = new RemoteViews(context.getPackageName(), R.layout.forecast_timestep);
                step.setTextViewText(R.id.timeStepTimeTextView, getFormattedTime(forecast.localtime()));
                step.setTextViewText(R.id.temperatureTextView, Math.round(forecast.temperature()) + "°");

                int symbol = forecast.smartSymbol();
                int iconRes = context.getResources().getIdentifier("s_" + symbol, "drawable", context.getPackageName());
                step.setImageViewResource(R.id.weatherIconImageView, iconRes);
                
                if (i == (int)(firstFutureIndex + timeStepCount - 1)) {
                    step.setViewVisibility(R.id.forecastBorder, GONE);
                }
                views.addView(R.id.hourForecastRowLayout, step);
            }

            views.setViewVisibility(R.id.crisisViewContainer, GONE);
            List<Announcement> announcements = widgetData.announcements();
            if (announcements != null) {
                for (Announcement ann : announcements) {
                    if ("Crisis".equals(ann.type())) {
                        RemoteViews crisisView = new RemoteViews(context.getPackageName(), R.layout.crisis_view);
                        crisisView.setTextViewText(R.id.crisisText, ann.content());
                        views.addView(R.id.crisisViewContainer, crisisView);
                        views.setViewVisibility(R.id.crisisViewContainer, View.VISIBLE);
                        views.setViewVisibility(R.id.locationNameTextView, GONE);
                        views.setViewVisibility(R.id.locationRegionTextView, GONE);
                        break;
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views);
            pref.saveLong(WIDGET_UI_UPDATED, System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "UI Update failed", e);
        }
    }

    private String getFormattedTime(String localTime) {
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.getDefault());
            java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("HH", java.util.Locale.getDefault());
            return out.format(in.parse(localTime));
        } catch (Exception e) {
            return "";
        }
    }
}
