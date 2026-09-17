package fi.fmi.mobileweather.widgets.model

data class WidgetData(
    val announcements: List<Announcement>? = null,
    val forecast: List<ForecastItem>? = null,
    val warnings: WarningsRecordRoot? = null,
    val location: List<LocationRecord>? = null
) {
    constructor(announcements: List<Announcement>?, forecast: List<ForecastItem>?) : this(
        announcements = announcements,
        forecast = forecast,
        warnings = null,
        location = null
    )
}
