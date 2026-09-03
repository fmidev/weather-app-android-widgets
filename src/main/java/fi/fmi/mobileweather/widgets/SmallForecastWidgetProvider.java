package fi.fmi.mobileweather.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.List;

import fi.fmi.mobileweather.widgets.enumeration.WidgetType;
import fi.fmi.mobileweather.widgets.model.Announcement;
import fi.fmi.mobileweather.widgets.model.ForecastItem;
import fi.fmi.mobileweather.widgets.model.WidgetData;
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper;
import static fi.fmi.mobileweather.widgets.model.PrefKey.LAYOUT_RES_ID;
import static fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class SmallForecastWidgetProvider extends BaseWidgetProvider {
    private static final String TAG = "SmallWidgetProvider";

    @Override
    protected WidgetType getWidgetType() {
        return WidgetType.WEATHER_FORECAST;
    }

    @Override
    protected int getLayoutResourceId() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return R.layout.small_forecast_widget_layout;
        } else {
            return R.layout.xs_forecast_widget_layout;
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        int minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        int layoutId = getLayoutResourceIdForResize(minWidth, minHeight);
        SharedPreferencesHelper.getInstance(context, appWidgetId).saveInt(LAYOUT_RES_ID, layoutId);
        updateAppWidget(context, appWidgetManager, appWidgetId);
    }

    private int getLayoutResourceIdForResize(int minWidth, int minHeight) {
        if (minWidth < 100) return R.layout.xs_forecast_widget_layout;
        if (minWidth < 250) return R.layout.small_forecast_widget_layout;
        return R.layout.horizontal_forecast_widget_layout;
    }

    @Override
    protected void setWidgetUi(Context context, AppWidgetManager manager, WidgetData data, SharedPreferencesHelper pref, WidgetInitResult initResult, int widgetId) {
        RemoteViews views = initResult.widgetRemoteViews();
        List<ForecastItem> forecastItems = data.forecast();

        try {
            if (forecastItems == null || forecastItems.isEmpty()) return;
            
            int index = -1;
            long now = System.currentTimeMillis();
            for (int i = 0; i < forecastItems.size(); i++) {
                if (forecastItems.get(i).epochtime() * 1000 > now) {
                    index = i;
                    break;
                }
            }
            if (index == -1) index = 0;
            ForecastItem first = forecastItems.get(index);

            views.setTextViewText(R.id.locationNameTextView, first.name() + ",");
            views.setTextViewText(R.id.locationRegionTextView, first.region());
            views.setTextViewText(R.id.temperatureTextView, String.valueOf(Math.round(first.temperature())));
            views.setTextViewText(R.id.temperatureUnitTextView, "°");

            int symbol = first.smartSymbol();
            int iconRes = context.getResources().getIdentifier("s_" + symbol, "drawable", context.getPackageName());
            views.setImageViewResource(R.id.weatherIconImageView, iconRes);

            showCrisisViewIfNeeded(context, data.announcements(), views);

            manager.updateAppWidget(widgetId, views);
            pref.saveLong(WIDGET_UI_UPDATED, System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "UI Update failed", e);
        }
    }

    private void showCrisisViewIfNeeded(Context context, List<Announcement> announcements, RemoteViews views) {
        views.removeAllViews(R.id.crisisViewContainer);
        if (announcements == null || announcements.isEmpty()) {
            views.setViewVisibility(R.id.crisisViewContainer, GONE);
            return;
        }

        for (Announcement ann : announcements) {
            if ("Crisis".equals(ann.type())) {
                RemoteViews crisisView = new RemoteViews(context.getPackageName(), R.layout.crisis_view);
                crisisView.setTextViewText(R.id.crisisText, ann.content());
                views.addView(R.id.crisisViewContainer, crisisView);
                views.setViewVisibility(R.id.crisisViewContainer, VISIBLE);
                return;
            }
        }
        views.setViewVisibility(R.id.crisisViewContainer, GONE);
    }
}
