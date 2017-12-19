package net.colino.stravaautostartstop;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class ForegroundService extends Service {
    public void onCreate() {
        super.onCreate();
        LogUtils.i(MainActivity.LOG_TAG, "createForegroundService");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            String id = "sass_channel";
            CharSequence name = getString(R.string.app_name);
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel mChannel = new NotificationChannel(id, name, importance);
            mChannel.setShowBadge(true);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(mChannel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.i(MainActivity.LOG_TAG, "startForegroundService");
        startForeground(1, MainActivity.buildNotification(this, getString(R.string.detection_started)));
        MainActivity.requestUpdates(this, 0,true);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LogUtils.i(MainActivity.LOG_TAG, "destroyForegroundService");
        MainActivity.requestUpdates(this, 0,false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case if services are bound (Bound Services).
        return null;
    }
}
