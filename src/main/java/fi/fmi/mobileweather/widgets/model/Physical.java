package fi.fmi.mobileweather.widgets.model;

public record Physical(
    int windIntensity,
    String windIntensityUom,
    int windDirection,
    String windDirectionUom
) {
}
