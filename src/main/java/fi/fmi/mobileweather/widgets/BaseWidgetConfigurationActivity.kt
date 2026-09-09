package fi.fmi.mobileweather.widgets

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.reactnativecommunity.asyncstorage.AsyncLocalStorageUtil
import com.reactnativecommunity.asyncstorage.ReactDatabaseSupplier
import fi.fmi.mobileweather.widgets.model.LocationConstants.CURRENT_LOCATION
import fi.fmi.mobileweather.widgets.model.PrefKey.FAVORITE_LATLON
import fi.fmi.mobileweather.widgets.model.PrefKey.GRADIENT_BACKGROUND
import fi.fmi.mobileweather.widgets.model.PrefKey.SELECTED_LOCATION
import fi.fmi.mobileweather.widgets.model.PrefKey.TRANSPARENT_BACKGROUND
import fi.fmi.mobileweather.widgets.util.SharedPreferencesHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToLong

abstract class BaseWidgetConfigurationActivity : Activity() {

    protected abstract fun getWidgetProviderClass(): Class<*>

    private var appWidgetId = INVALID_APPWIDGET_ID
    private var locationRadioGroup: RadioGroup? = null
    private var waitingForAppSettings = false

    protected open fun getLayoutResourceId(): Int {
        return R.layout.base_widget_configure
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(getLayoutResourceId())
        configureWindowInsets()
        setResult(RESULT_CANCELED)

        if (savedInstanceState != null) {
            waitingForAppSettings = savedInstanceState.getBoolean(STATE_WAITING_FOR_APP_SETTINGS, false)
        }

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(EXTRA_APPWIDGET_ID, INVALID_APPWIDGET_ID)
        }

        if (appWidgetId == INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        initViews()
    }

    @Suppress("DEPRECATION")
    private fun configureWindowInsets() {
        // The configuration layout has a white background in both light and dark mode.
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Color.TRANSPARENT
        } else {
            Color.BLACK
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                initialLeft + safeInsets.left,
                initialTop + safeInsets.top,
                initialRight + safeInsets.right,
                initialBottom + safeInsets.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onResume() {
        super.onResume()

        setLocationFavoritesButtons()

        if (waitingForAppSettings && hasRequiredLocationPermissions()) {
            waitingForAppSettings = false
            finalizeWidget(CURRENT_LOCATION, null)
        }
    }

    open fun initViews() {
        setReadyButton()
        setLocationFavoritesButtons()
        setAddFavoriteLocationsClickListener()

        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val gradientVisibility = if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) VISIBLE else GONE
        findViewById<RadioButton>(R.id.gradientBackgroundRadioButton).visibility = gradientVisibility
        findViewById<TextView>(R.id.gradientBackgroundLimitations).visibility = gradientVisibility

        val pref = SharedPreferencesHelper.getInstance(this, appWidgetId)
        findViewById<RadioGroup>(R.id.themeRadioGroup).check(
            when {
                pref.getInt(TRANSPARENT_BACKGROUND, 0) == 1 -> R.id.transparentBackgroundRadioButton
                gradientVisibility == VISIBLE && pref.getInt(GRADIENT_BACKGROUND, 0) == 1 -> R.id.gradientBackgroundRadioButton
                else -> R.id.defaultBackgroundRadioButton
            }
        )
    }

    private fun setLocationFavoritesButtons() {
        locationRadioGroup = findViewById(R.id.locationRadioGroup)

        val readableDatabase = ReactDatabaseSupplier.getInstance(applicationContext).readableDatabase

        if (readableDatabase != null) {
            val impl = AsyncLocalStorageUtil.getItemImpl(readableDatabase, "persist:location")

            if (impl != null) {
                try {
                    val dump = JSONObject(impl)
                    val favorites = JSONArray(dump.getString("favorites"))

                    Log.d("Widget Update", "Favorites: $favorites")

                    val addFavoriteLocationsExplanationTextView = findViewById<TextView>(R.id.addFavoriteLocationsExplanationTextView)
                    val addFavoriteLocationsButton = findViewById<Button>(R.id.addFavoriteLocationsButton)

                    if (favorites.length() == 0) {
                        addFavoriteLocationsExplanationTextView.visibility = VISIBLE
                        addFavoriteLocationsButton.setText(R.string.add_your_favorite_locations)
                    } else {
                        addFavoriteLocationsExplanationTextView.visibility = GONE
                        addFavoriteLocationsButton.setText(R.string.add_more_favorite_locations)
                    }

                    val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    for (i in 0 until favorites.length()) {
                        val current = favorites.getJSONObject(i)
                        val geoId = current.getInt("id")
                        val name = current.getString("name")

                        val latlon = getLatLonString(current)

                        val existingRadioButton = findViewById<RadioButton>(geoId)

                        if (existingRadioButton == null) {
                            val favoriteRadioButton = inflater.inflate(R.layout.favorite_radio_button, locationRadioGroup, false) as RadioButton
                            favoriteRadioButton.text = name
                            favoriteRadioButton.tag = latlon
                            favoriteRadioButton.id = geoId
                            locationRadioGroup?.addView(favoriteRadioButton)
                        }
                    }
                } catch (e: JSONException) {
                    Log.d("Widget Update", "Error parsing location favorites: ${e.message}")
                }
            }
        }
    }

    private fun setReadyButton() {
        val okButton = findViewById<Button>(R.id.okButton)
        okButton.setOnClickListener { showAppWidget() }
    }

    private fun setAddFavoriteLocationsClickListener() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("fmiweather://search"))
        val addFavoriteLocationsButton = findViewById<Button>(R.id.addFavoriteLocationsButton)
        addFavoriteLocationsButton.setOnClickListener { startActivity(intent) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 || requestCode == 2) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                finalizeWidget(CURRENT_LOCATION, null)
            } else {
                Toast.makeText(this, getString(R.string.denied_positioning), Toast.LENGTH_SHORT).show()
            }
        }
    }

    protected open fun showAppWidget() {
        var widgetId = INVALID_APPWIDGET_ID
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            widgetId = extras.getInt(EXTRA_APPWIDGET_ID, INVALID_APPWIDGET_ID)

            val radioGroup = locationRadioGroup
            val selectedLocation = radioGroup?.checkedRadioButtonId ?: INVALID_APPWIDGET_ID

            if (selectedLocation == R.id.currentLocationRadioButton) {
                Log.d("Widget Update", "Selected location: current")
                askLocationPermissionIfNeeded()
            } else {
                Log.d("Widget Update", "Selected location: $selectedLocation")
                val selectedRadioButton = findViewById<RadioButton>(selectedLocation)
                val latlon = selectedRadioButton?.tag as? String
                Log.d("Widget Update", "Selected latlon: $latlon")
                finalizeWidget(selectedLocation, latlon)
            }
        }
        if (widgetId == INVALID_APPWIDGET_ID) {
            Log.i("Widget Update", "Invalid appwidget id")
            finish()
        }
    }

    private fun finalizeWidget(selectedLocation: Int, latlon: String?) {
        val context = baseContext

        val pref = SharedPreferencesHelper.getInstance(context, appWidgetId)
        Log.d("Widget Update", "pref for this appWidgetId: $appWidgetId")

        pref.saveInt(SELECTED_LOCATION, selectedLocation)
        if (latlon != null) {
            pref.saveString(FAVORITE_LATLON, latlon)
        }

        val selectedTheme = findViewById<RadioGroup>(R.id.themeRadioGroup).checkedRadioButtonId
        pref.saveInt(GRADIENT_BACKGROUND, if (selectedTheme == R.id.gradientBackgroundRadioButton) 1 else 0)
        pref.saveInt(TRANSPARENT_BACKGROUND, if (selectedTheme == R.id.transparentBackgroundRadioButton) 1 else 0)

        val updateIntent = Intent(ACTION_APPWIDGET_UPDATE).setClass(context, getWidgetProviderClass())
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        sendBroadcast(updateIntent)

        val resultValue = Intent()
        resultValue.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

    fun askLocationPermissionIfNeeded() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            showGenericLocationPermissionDialog()
        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showBackgroundLocationPermissionDialog()
        } else {
            finalizeWidget(CURRENT_LOCATION, null)
        }
    }

    private fun showBackgroundLocationPermissionDialog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.allow_background_location_service)
            .setPositiveButton(android.R.string.ok) { _, _ -> requestBackgroundLocationPermission() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestGenericLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            1
        )
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                2
            )
        } else {
            openAppDetailsSettings()
        }
    }

    private fun openAppDetailsSettings() {
        waitingForAppSettings = true
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun hasRequiredLocationPermissions(): Boolean {
        val hasForegroundLocation =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasForegroundLocation) {
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        }

        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_WAITING_FOR_APP_SETTINGS, waitingForAppSettings)
    }

    private fun showGenericLocationPermissionDialog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.allow_background_location_service)
            .setPositiveButton(android.R.string.ok) { _, _ -> requestGenericLocationPermissions() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationRadioGroup?.removeAllViews()
        locationRadioGroup = null
    }

    companion object {
        private const val STATE_WAITING_FOR_APP_SETTINGS = "waiting_for_app_settings"

        @Throws(JSONException::class)
        private fun getLatLonString(current: JSONObject): String {
            var latitude = current.getDouble("lat")
            var longitude = current.getDouble("lon")
            latitude = (latitude * 10000.0).roundToLong() / 10000.0
            longitude = (longitude * 10000.0).roundToLong() / 10000.0
            return "$latitude,$longitude"
        }
    }
}
