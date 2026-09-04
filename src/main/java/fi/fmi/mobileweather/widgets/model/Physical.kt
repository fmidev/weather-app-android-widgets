package fi.fmi.mobileweather.widgets.model

data class Physical(
    val windIntensity: Int = 0,
    val windIntensityUom: String? = null,
    val windDirection: Int = 0,
    val windDirectionUom: String? = null
)
