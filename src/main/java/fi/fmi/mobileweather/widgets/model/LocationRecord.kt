package fi.fmi.mobileweather.widgets.model

data class LocationRecord(
    val geoid: Int = 0,
    val name: String? = null,
    val region: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val country: String? = null,
    val iso2: String? = null,
    val localtz: String? = null
)
