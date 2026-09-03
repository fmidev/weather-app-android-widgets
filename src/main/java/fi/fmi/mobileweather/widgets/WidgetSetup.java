package fi.fmi.mobileweather.widgets;

public record WidgetSetup(
        Location location,
        Weather weather,
        Warnings warnings,
        Announcements announcements,
        Layout layout
) {
    public record Location(
            DefaultLocation defaultLocation,
            String apiUrl
    ) {}

    public record DefaultLocation(
            String name,
            String area,
            double lat,
            double lon,
            int id,
            String country,
            String timezone
    ) {}

    public record Weather(
            String apiUrl,
            int interval,
            boolean useCardinalsForWindDirection
    ) {}

    public record Warnings(
            String apiUrl,
            int interval
    ) {}

    public record Announcements(
            boolean enabled,
            Api api
    ) {}

    public record Api(
            String fi,
            String en,
            String sv
    ) {}

    public record Layout(
            Logo logo
    ) {}

    public record Logo(
            boolean enabled
    ) {}
}
