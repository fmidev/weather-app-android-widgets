package fi.fmi.mobileweather.widgets.model

import android.util.Log

data class Warning(
    val type: String? = null,
    val language: String? = null,
    val severity: String? = null,
    val description: String? = null,
    val duration: Duration? = null,
    val physical: Physical? = null
) : Comparable<Warning> {

    private fun getSeverityValue(): Int {
        return when (severity) {
            "Extreme" -> 3
            "Severe" -> 2
            "Moderate" -> 1
            else -> 0
        }
    }

    private fun getTypePriorityValue(): Int {
        return when (type) {
            "thunderstorm" -> 17
            "forestFireWeather" -> 16
            "grassFireWeather" -> 15
            "wind" -> 14
            "trafficWeather" -> 13
            "rain" -> 12
            "pedestrianSafety" -> 11
            "hotWeather" -> 10
            "coldWeather" -> 9
            "uvNote" -> 8
            "flooding" -> 7
            "seaWind" -> 6
            "seaThunderStorm" -> 5
            "seaWaveHeight" -> 4
            "seaWaterHeightHighWater" -> 3
            "seaWaterHeightShallowWater" -> 2
            "seaIcing" -> 1
            else -> 0
        }
    }

    override fun compareTo(other: Warning): Int {
        Log.d("compareTo", "this: ${getSeverityValue()} other: ${other.getSeverityValue()}")
        val severityComparison = other.getSeverityValue().compareTo(getSeverityValue())
        if (severityComparison != 0) {
            return severityComparison
        }
        return other.getTypePriorityValue().compareTo(getTypePriorityValue())
    }
}
