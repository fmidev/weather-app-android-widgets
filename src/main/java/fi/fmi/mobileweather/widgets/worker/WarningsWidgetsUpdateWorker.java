package fi.fmi.mobileweather.widgets.worker;

import static fi.fmi.mobileweather.widgets.WidgetNotification.ACTION_APPWIDGET_AUTO_UPDATE;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import fi.fmi.mobileweather.widgets.MediumWarningsWidgetProvider;
import fi.fmi.mobileweather.widgets.SmallWarningsWidgetProvider;

public class WarningsWidgetsUpdateWorker extends Worker {
    private static final String TAG = "WarningsWidgetsWorker";

    public WarningsWidgetsUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Triggering warnings updates via broadcast");
        Context context = getApplicationContext();

        sendUpdateBroadcast(context, SmallWarningsWidgetProvider.class);
        sendUpdateBroadcast(context, MediumWarningsWidgetProvider.class);

        return Result.success();
    }

    private void sendUpdateBroadcast(Context context, Class<?> cls) {
        Intent intent = new Intent(context, cls);
        intent.setAction(ACTION_APPWIDGET_AUTO_UPDATE);
        context.sendBroadcast(intent);
    }
}
