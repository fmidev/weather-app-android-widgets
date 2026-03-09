package fi.fmi.mobileweather.widgets.model;

import org.json.JSONArray;
import org.json.JSONObject;

public record WidgetData(
    JSONArray announcements,
    JSONObject forecast,
    JSONObject warnings,
    String location
) {
    public WidgetData(JSONArray announcements, JSONObject forecast) {
        this(announcements, forecast, null, null);
    }
}
