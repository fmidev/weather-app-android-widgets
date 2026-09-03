package fi.fmi.mobileweather.widgets.worker;

import static fi.fmi.mobileweather.widgets.WidgetNotification.ACTION_APPWIDGET_AUTO_UPDATE;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import fi.fmi.mobileweather.widgets.LargeForecastWidgetProvider;
import fi.fmi.mobileweather.widgets.MediumForecastWidgetProvider;
import fi.fmi.mobileweather.widgets.SmallForecastWidgetProvider;

public class WeatherWidgetsUpdateWorker extends Worker {
    private static final String TAG = "WeatherWidgetsWorker";

    public WeatherWidgetsUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Triggering widget updates via broadcast");
        Context context = getApplicationContext();
        
        sendUpdateBroadcast(context, SmallForecastWidgetProvider.class);
        sendUpdateBroadcast(context, MediumForecastWidgetProvider.class);
        sendUpdateBroadcast(context, LargeForecastWidgetProvider.class);

        return Result.success();
    }

    private void sendUpdateBroadcast(Context context, Class<?> cls) {
        Intent intent = new Intent(context, cls);
        intent.setAction(ACTION_APPWIDGET_AUTO_UPDATE);
        context.sendBroadcast(intent);
    }
}
