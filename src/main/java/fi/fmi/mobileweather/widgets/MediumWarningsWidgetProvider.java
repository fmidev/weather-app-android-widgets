package fi.fmi.mobileweather.widgets;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static fi.fmi.mobileweather.widgets.model.PrefKey.WIDGET_UI_UPDATED;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import fi.fmi.mobileweather.widgets.enumeration.WidgetType;
import fi.fmi.mobileweather.widgets.model.Announcement;
import fi.fmi.mobileweather.widgets.model.LocationRecord;
import fi.fmi.mobileweather.widgets.model.Warning;
import fi.fmi.mobileweather.widgets.model.WarningsRecordRoot;
import fi.fmi.mobileweather.widgets.model.WidgetData;
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper;
import fi.fmi.mobileweather.widgets.util.WarningsIconMapper;
import fi.fmi.mobileweather.widgets.util.WarningsTextMapper;

public class MediumWarningsWidgetProvider extends BaseWarningsWidgetProvider {
    private static final String TAG = "MediumWarningsProvider";

    @Override
    protected WidgetType getWidgetType() {
        return WidgetType.WARNINGS;
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.medium_warnings_widget_layout;
    }

    @Override
    protected void setWidgetUi(Context context, AppWidgetManager manager, WidgetData data, SharedPreferencesHelper pref, WidgetInitResult initResult, int widgetId) {
        final int MAX_WARNINGS = 3;
        RemoteViews views = initResult.widgetRemoteViews();
        WarningsRecordRoot root = data.warnings();
        List<LocationRecord> locations = data.location();

        if (root == null || locations == null || locations.isEmpty()) return;

        try {
            LocationRecord loc = locations.get(0);

            if (!"FI".equals(loc.iso2())) {
                showErrorView(context, manager, pref,
                    context.getString(R.string.location_outside_data_area_title),
                    context.getString(R.string.location_outside_data_area_description), widgetId);
                return;
            }

            views.setTextViewText(R.id.locationNameTextView, loc.name() + ", ");
            views.setTextViewText(R.id.locationRegionTextView, loc.region());

            List<Warning> warnings = root.data().warnings().stream()
                .filter(w -> "fi".equals(w.language()) && isValidDate(w))
                .sorted()
                .collect(Collectors.toList());
            
            warnings = filterUnique(warnings);
            views.removeAllViews(R.id.warningRowContainer);

            int count = Math.min(warnings.size(), MAX_WARNINGS);
            for (int i = 0; i < count; i++) {
                Warning w = warnings.get(i);
                RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.warning_row);
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

                row.addView(R.id.warningIconContainer, icon);
                row.setTextViewText(R.id.warningTitle, context.getString(WarningsTextMapper.getStringResourceId(w.type())));
                row.setTextViewText(R.id.warningDuration, getFormattedTimeFrame(w.duration().startTime(), w.duration().endTime()));
                views.addView(R.id.warningRowContainer, row);
            }

            if (warnings.size() > MAX_WARNINGS) {
                RemoteViews more = new RemoteViews(context.getPackageName(), R.layout.more_warnings);
                more.setTextViewText(R.id.moreWarnings, context.getString(R.string.more_warnings) + " (" + (warnings.size() - MAX_WARNINGS) + ")");
                views.addView(R.id.warningRowContainer, more);
            } else if (warnings.isEmpty()) {
                RemoteViews noWarnings = new RemoteViews(context.getPackageName(), R.layout.custom_text_layout);
                noWarnings.setTextViewText(R.id.customTextView, context.getString(R.string.no_warnings));
                views.addView(R.id.warningRowContainer, noWarnings);
            }

            views.setTextViewText(R.id.updateTime, context.getString(R.string.updated) + " " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            
            // Crisis view
            List<Announcement> announcements = data.announcements();
            views.setViewVisibility(R.id.crisisViewContainer, GONE);
            if (announcements != null) {
                for (Announcement ann : announcements) {
                    if ("Crisis".equals(ann.type())) {
                        RemoteViews crisis = new RemoteViews(context.getPackageName(), R.layout.crisis_view);
                        crisis.setTextViewText(R.id.crisisText, ann.content());
                        views.addView(R.id.crisisViewContainer, crisis);
                        views.setViewVisibility(R.id.crisisViewContainer, VISIBLE);
                        views.setViewVisibility(R.id.locationNameTextView, GONE);
                        views.setViewVisibility(R.id.locationRegionTextView, GONE);
                        break;
                    }
                }
            }

            manager.updateAppWidget(widgetId, views);
            pref.saveLong(WIDGET_UI_UPDATED, System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "UI Update failed", e);
        }
    }
}
