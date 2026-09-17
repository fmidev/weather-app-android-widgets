package fi.fmi.mobileweather.widgets.util

import fi.fmi.mobileweather.widgets.R

object WarningsIconMapper {
    private val iconMap = mapOf(
        "flooding" to R.drawable.warnings_flood,
        "grassFireWeather" to R.drawable.warnings_grass_fire_weather,
        "forestFireWeather" to R.drawable.warnings_grass_fire_weather,
        "hotWeather" to R.drawable.warnings_hot_weather,
        "coldWeather" to R.drawable.warnings_hot_weather,
        "seaIcing" to R.drawable.warnings_icing,
        "pedestrianSafety" to R.drawable.warnings_pedestrian_safety,
        "rain" to R.drawable.warnings_rain,
        "seaThunderStorm" to R.drawable.warnings_thunder_storm,
        "seaWaterHeightHigh" to R.drawable.warnings_sea_water_height_high,
        "seaWaterHeightShallow" to R.drawable.warnings_sea_water_height_shallow,
        "seaWaveHeight" to R.drawable.warnings_sea_wave_height,
        "seaWind" to R.drawable.warnings_sea_wind,
        "thunderstorm" to R.drawable.warnings_thunder_storm,
        "trafficWeather" to R.drawable.warnings_traffic_weather,
        "uvNote" to R.drawable.warnings_uv_note,
        "wind" to R.drawable.warnings_wind
    )

    @JvmStatic
    fun getIconResourceId(type: String?): Int {
        if (type == null) return 0
        return iconMap[type] ?: 0
    }

    @JvmStatic
    fun getCircleBackgroundResourceId(severity: String?): Int {
        if (severity == null) return 0
        return when (severity) {
            "Moderate" -> R.drawable.warning_circle_yellow
            "Severe" -> R.drawable.warning_circle_orange
            "Extreme" -> R.drawable.warning_circle_red
            else -> R.drawable.warning_circle_white
        }
    }
}
