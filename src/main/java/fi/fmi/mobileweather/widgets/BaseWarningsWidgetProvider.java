package fi.fmi.mobileweather.widgets;

import static android.text.format.DateUtils.isToday;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;

import fi.fmi.mobileweather.widgets.model.LocationRecord;
import fi.fmi.mobileweather.widgets.model.Warning;
import fi.fmi.mobileweather.widgets.model.WarningsRecordRoot;
import fi.fmi.mobileweather.widgets.model.WidgetData;
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper;
import fi.fmi.mobileweather.widgets.util.WarningsIconMapper;
import fi.fmi.mobileweather.widgets.util.WarningsTextMapper;

public abstract class BaseWarningsWidgetProvider extends BaseWidgetProvider {
    private static final String TAG = "BaseWarningsProvider";

    @Override
    protected void setWidgetUi(Context context, AppWidgetManager appWidgetManager, WidgetData widgetData, SharedPreferencesHelper pref, WidgetInitResult widgetInitResult, int widgetId) {
        RemoteViews views = widgetInitResult.widgetRemoteViews();
        WarningsRecordRoot root = widgetData.warnings();
        List<LocationRecord> locations = widgetData.location();

        if (root == null || locations == null || locations.isEmpty()) return;

        try {
            LocationRecord loc = locations.get(0);

            if (!"FI".equals(loc.iso2())) {
                showErrorView(context, appWidgetManager, pref, 
                    context.getString(R.string.location_outside_data_area_title),
                    context.getString(R.string.location_outside_data_area_description),
                    widgetId);
                return;
            }

            views.setTextViewText(R.id.locationNameTextView, loc.name() + ", ");
            views.setTextViewText(R.id.locationRegionTextView, loc.region());

            List<Warning> warnings = root.data().warnings().stream()
                .filter(w -> "fi".equals(w.language()) && isValidDate(w))
                .sorted()
                .collect(Collectors.toList());
            
            warnings = filterUnique(warnings);

            views.removeAllViews(R.id.warningIconContainer);
            views.setViewVisibility(R.id.warningTimeFrameTextView, GONE);

            int toShow = Math.min(warnings.size(), 2);
            for (int i = 0; i < toShow; i++) {
                Warning w = warnings.get(i);
                RemoteViews icon = new RemoteViews(context.getPackageName(), R.layout.warning_icon);
                
                int bg = WarningsIconMapper.getCircleBackgroundResourceId(w.severity());
                if (bg != 0) icon.setInt(R.id.warningIconBackgroundImageView, "setBackgroundResource", bg);

                if ("seaWind".equals(w.type()) || "wind".equals(w.type())) {
                    icon.setImageViewResource(R.id.warningIconImageView, R.drawable.sea_wind);
                    if (w.physical() != null) {
                        icon.setFloat(R.id.warningIconImageView, "setRotation", w.physical().windDirection() - 180);
                        icon.setViewVisibility(R.id.windIntensityTextView, VISIBLE);
                        icon.setTextViewText(R.id.windIntensityTextView, String.valueOf(Math.round(w.physical().windIntensity())));
                    }
                } else {
                    int resId = WarningsIconMapper.getIconResourceId(w.type());
                    if (resId != 0) icon.setImageViewResource(R.id.warningIconImageView, resId);
                }
                views.addView(R.id.warningIconContainer, icon);

                if (toShow == 1) {
                    views.setTextViewText(R.id.warningTextView, context.getString(WarningsTextMapper.getStringResourceId(w.type())));
                    views.setViewVisibility(R.id.warningTimeFrameTextView, VISIBLE);
                    views.setTextViewText(R.id.warningTimeFrameTextView, getFormattedTimeFrame(w.duration().startTime(), w.duration().endTime()));
                }
            }

            if (toShow > 1) {
                views.setTextViewText(R.id.warningTextView, context.getString(R.string.warnings) + " (" + warnings.size() + ")");
            } else if (toShow == 0) {
                views.setTextViewText(R.id.warningTextView, "");
                RemoteViews empty = new RemoteViews(context.getPackageName(), R.layout.custom_text_layout);
                empty.setTextViewText(R.id.customTextView, context.getString(R.string.no_warnings));
                views.addView(R.id.warningIconContainer, empty);
            }

            appWidgetManager.updateAppWidget(widgetId, views);
            pref.saveLong(WIDGET_UI_UPDATED, System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "Warnings UI update failed", e);
        }
    }

    protected boolean isValidDate(Warning w) {
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date start = df.parse(w.duration().startTime());
            if (start == null) return false;
            
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"));
            cal.setTime(start);
            int year = cal.get(Calendar.YEAR);
            int day = cal.get(Calendar.DAY_OF_YEAR);
            
            Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Europe/Helsinki"));
            return year == now.get(Calendar.YEAR) && day == now.get(Calendar.DAY_OF_YEAR);
        } catch (Exception e) {
            return false;
        }
    }

    protected List<Warning> filterUnique(List<Warning> warnings) {
        List<Warning> result = new ArrayList<>();
        for (Warning w : warnings) {
            if (result.stream().noneMatch(r -> r.type().equals(w.type()) && r.severity().equals(w.severity()))) {
                result.add(w);
            }
        }
        return result;
    }

    protected String getFormattedTimeFrame(String start, String end) throws ParseException {
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        in.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date d1 = in.parse(start);
        Date d2 = in.parse(end);
        
        SimpleDateFormat out = new SimpleDateFormat(isToday(d2.getTime()) ? "HH:mm" : "dd.MM. HH:mm", Locale.getDefault());
        out.setTimeZone(TimeZone.getTimeZone("Europe/Helsinki"));
        return out.format(d1) + " - " + out.format(d2);
    }
}
