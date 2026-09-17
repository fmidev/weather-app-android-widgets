package fi.fmi.mobileweather.widgets.util

import android.content.Context
import android.provider.Settings

object AirplaneModeUtil {
    @JvmStatic
    fun isAirplaneModeOn(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0
    }
}
