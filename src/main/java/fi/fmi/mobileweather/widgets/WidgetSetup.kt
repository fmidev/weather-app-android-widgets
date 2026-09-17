package fi.fmi.mobileweather.widgets

data class WidgetSetup(
    val location: Location? = null,
    val weather: Weather? = null,
    val warnings: Warnings? = null,
    val announcements: Announcements? = null,
    val layout: Layout? = null
) {
    data class Location(
        val defaultLocation: DefaultLocation? = null,
        val apiUrl: String? = null
    )

    data class DefaultLocation(
        val name: String? = null,
        val area: String? = null,
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val id: Int = 0,
        val country: String? = null,
        val timezone: String? = null
    )

    data class Weather(
        val apiUrl: String? = null,
        val interval: Int = 0,
        val useCardinalsForWindDirection: Boolean = false
    )

    data class Warnings(
        val apiUrl: String? = null,
        val interval: Int = 0
    )

    data class Announcements(
        val enabled: Boolean = false,
        val api: Api? = null
    )

    data class Api(
        val fi: String? = null,
        val en: String? = null,
        val sv: String? = null
    )

    data class Layout(
        val logo: Logo? = null
    )

    data class Logo(
        val enabled: Boolean = false
    )
}
