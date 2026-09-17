package fi.fmi.mobileweather.widgets.util

import android.content.Context
import androidx.core.content.ContextCompat
import fi.fmi.mobileweather.widgets.R

object ColorUtils {
    @JvmStatic
    fun getPrimaryBlue(context: Context): Int {
        return ContextCompat.getColor(context, R.color.primaryBlue)
    }
}
