package fi.fmi.mobileweather.widgets.model

data class Data(
    val updated: String? = null,
    val warnings: List<Warning>? = null,
    val startTime: String? = null,
    val endTime: String? = null
)
