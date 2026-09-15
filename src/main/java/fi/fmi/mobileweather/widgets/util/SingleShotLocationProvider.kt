package fi.fmi.mobileweather.widgets.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import java.util.concurrent.atomic.AtomicBoolean

object SingleShotLocationProvider {

    private const val TAG = "SingleShotLocation"
    private const val TIMEOUT_MS = 15000L
    private const val MAX_LOCATION_AGE_MS = 60000L

    interface LocationCallback {
        fun onNewLocationAvailable(location: Location)
        fun onLocationFailed()
    }

    // Both location permissions are checked before the supplied request is invoked.
    @SuppressLint("MissingPermission")
    @JvmStatic
    fun requestSingleUpdate(context: Context, callback: LocationCallback): CancellationSignal {
        return requestSingleUpdate(context, callback) { request, token ->
            LocationServices.getFusedLocationProviderClient(context.applicationContext)
                .getCurrentLocation(request, token)
        }
    }

    internal fun requestSingleUpdate(
        context: Context,
        callback: LocationCallback,
        requestLocation: (CurrentLocationRequest, CancellationToken) -> Task<Location>
    ): CancellationSignal {
        val signal = CancellationSignal()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permissions missing")
            callback.onLocationFailed()
            return signal
        }

        val handler = Handler(Looper.getMainLooper())
        val cancellation = CancellationTokenSource()
        val completed = AtomicBoolean(false)
        lateinit var timeout: Runnable

        fun finish(location: Location?) {
            if (!completed.compareAndSet(false, true)) return
            handler.removeCallbacks(timeout)
            cancellation.cancel()
            if (location != null) {
                callback.onNewLocationAvailable(location)
            } else {
                callback.onLocationFailed()
            }
        }

        timeout = Runnable {
            Log.d(TAG, "Location request timed out")
            finish(null)
        }
        signal.setOnCancelListener {
            if (completed.compareAndSet(false, true)) {
                handler.removeCallbacks(timeout)
                cancellation.cancel()
            }
        }
        // Also bound the wait when Play services cannot complete the task promptly.
        handler.postDelayed(timeout, TIMEOUT_MS)

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MS)
            .setDurationMillis(TIMEOUT_MS)
            .build()

        try {
            requestLocation(request, cancellation.token).addOnCompleteListener { task ->
                finish(if (task.isSuccessful) task.result else null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting location", e)
            finish(null)
        }
        return signal
    }
}
