package fi.fmi.mobileweather.widgets.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat

object SingleShotLocationProvider {

    private const val TAG = "SingleShotLocation"
    private const val TIMEOUT_MS = 15000L

    interface LocationCallback {
        fun onNewLocationAvailable(location: Location)
        fun onLocationFailed()
    }

    @JvmStatic
    fun requestSingleUpdate(context: Context, callback: LocationCallback) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            callback.onLocationFailed()
            return
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permissions missing")
            callback.onLocationFailed()
            return
        }

        val handlerThread = HandlerThread("LocationThread")
        handlerThread.start()
        val looper: Looper = handlerThread.looper
        val handler = Handler(looper)

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "Location received: $location")
                callback.onNewLocationAvailable(location)
                cleanup(locationManager, this, handlerThread)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val timeoutRunnable = Runnable {
            Log.d(TAG, "Location request timed out")
            callback.onLocationFailed()
            cleanup(locationManager, locationListener, handlerThread)
        }

        val criteria = Criteria().apply {
            accuracy = Criteria.ACCURACY_COARSE
            powerRequirement = Criteria.POWER_LOW
        }

        try {
            locationManager.requestSingleUpdate(criteria, locationListener, looper)
            handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting location", e)
            callback.onLocationFailed()
            cleanup(locationManager, locationListener, handlerThread)
        }
    }

    private fun cleanup(locationManager: LocationManager, listener: LocationListener, thread: HandlerThread) {
        try {
            locationManager.removeUpdates(listener)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing updates", e)
        }
        thread.quitSafely()
    }
}
