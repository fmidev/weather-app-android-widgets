package fi.fmi.mobileweather.widgets.util

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesHelper private constructor(context: Context, appWidgetId: Int) {
    val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME_PREFIX + appWidgetId, Context.MODE_PRIVATE)

    fun saveString(key: String, value: String?) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String?): String? {
        return sharedPreferences.getString(key, defaultValue)
    }

    fun saveLong(key: String, value: Long) {
        sharedPreferences.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }

    fun saveInt(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    companion object {
        private const val PREFS_NAME_PREFIX = "fi.fmi.mobileweather.widget_"

        @JvmStatic
        fun getInstance(context: Context, appWidgetId: Int): SharedPreferencesHelper {
            return SharedPreferencesHelper(context, appWidgetId)
        }
    }
}
