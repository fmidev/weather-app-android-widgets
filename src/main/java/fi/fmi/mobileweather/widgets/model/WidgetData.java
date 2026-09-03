package fi.fmi.mobileweather.widgets.model;

import java.util.List;

public record WidgetData(
    List<Announcement> announcements,
    List<ForecastItem> forecast,
    WarningsRecordRoot warnings,
    List<LocationRecord> location
) {
    public WidgetData(List<Announcement> announcements, List<ForecastItem> forecast) {
        this(announcements, forecast, null, null);
    }
}
