package fi.fmi.mobileweather.widgets.model;

public record ForecastItem(
    long epochtime,
    String localtime,
    String utctime,
    String name,
    String region,
    String iso2,
    double temperature,
    double feelsLike,
    int smartSymbol,
    int windDirection,
    double windSpeedMS,
    String windCompass8
) {}
