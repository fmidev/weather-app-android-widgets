package fi.fmi.mobileweather.widgets.model

data class ForecastItem(
    val epochtime: Long = 0L,
    val localtime: String? = null,
    val utctime: String? = null,
    val name: String? = null,
    val region: String? = null,
    val iso2: String? = null,
    val temperature: Double = 0.0,
    val feelsLike: Double = 0.0,
    val smartSymbol: Int = 0,
    val windDirection: Int = 0,
    val windSpeedMS: Double = 0.0,
    val windCompass8: String? = null
)
