package fi.fmi.mobileweather.widgets.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

public class SingleShotLocationProvider {

    private static final String TAG = "SingleShotLocation";
    private static final long TIMEOUT_MS = 15000;

    public interface LocationCallback {
        void onNewLocationAvailable(Location location);
        void onLocationFailed();
    }

    public static void requestSingleUpdate(final Context context, final LocationCallback callback) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            callback.onLocationFailed();
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permissions missing");
            callback.onLocationFailed();
            return;
        }

        final HandlerThread handlerThread = new HandlerThread("LocationThread");
        handlerThread.start();
        final Looper looper = handlerThread.getLooper();
        final Handler handler = new Handler(looper);

        final LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                Log.d(TAG, "Location received: " + location);
                callback.onNewLocationAvailable(location);
                cleanup(locationManager, this, handlerThread);
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        };

        final Runnable timeoutRunnable = () -> {
            Log.d(TAG, "Location request timed out");
            callback.onLocationFailed();
            cleanup(locationManager, locationListener, handlerThread);
        };

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setPowerRequirement(Criteria.POWER_LOW);

        try {
            locationManager.requestSingleUpdate(criteria, locationListener, looper);
            handler.postDelayed(timeoutRunnable, TIMEOUT_MS);
        } catch (Exception e) {
            Log.e(TAG, "Error requesting location", e);
            callback.onLocationFailed();
            cleanup(locationManager, locationListener, handlerThread);
        }
    }

    private static void cleanup(LocationManager locationManager, LocationListener listener, HandlerThread thread) {
        try {
            locationManager.removeUpdates(listener);
        } catch (Exception e) {
            Log.e(TAG, "Error removing updates", e);
        }
        thread.quitSafely();
    }
}
