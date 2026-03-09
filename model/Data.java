package fi.fmi.mobileweather.widgets.model;

import java.util.List;

public record Data(
    String updated,
    List<Warning> warnings,
    String startTime,
    String endTime
) {
}
