package fi.fmi.mobileweather.widgets.util

import fi.fmi.mobileweather.widgets.R

object WarningsTextMapper {
    private val stringMap = mapOf(
        "flooding" to R.string.warnings_flood,
        "grassFireWeather" to R.string.warnings_grass_fire_weather,
        "forestFireWeather" to R.string.warnings_forest_fire_weather,
        "hotWeather" to R.string.warnings_hot_weather,
        "coldWeather" to R.string.warnings_cold_weather,
        "seaIcing" to R.string.warnings_icing,
        "pedestrianSafety" to R.string.warnings_pedestrian_safety,
        "rain" to R.string.warnings_rain,
        "seaThunderStorm" to R.string.warnings_sea_thunder_storm,
        "seaWaterHeightHigh" to R.string.warnings_sea_water_height_high,
        "seaWaterHeightShallow" to R.string.warnings_sea_water_height_shallow,
        "seaWaveHeight" to R.string.warnings_sea_wave_height,
        "seaWind" to R.string.warnings_sea_wind,
        "thunderstorm" to R.string.warnings_thunder_storm,
        "trafficWeather" to R.string.warnings_traffic_weather,
        "uvNote" to R.string.warnings_uv_note,
        "wind" to R.string.warnings_wind
    )

    @JvmStatic
    fun getStringResourceId(type: String?): Int {
        if (type == null) return 0
        return stringMap[type] ?: 0
    }
}
