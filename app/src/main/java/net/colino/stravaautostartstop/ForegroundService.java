/*
   Stravomatic - Automatically start Strava when bicycling/running
   Copyright (C) 2018-2023 Colin Leroy-Mira <colin@colino.net>

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.colino.stravaautostartstop;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;

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

    private static void requestUpdates(Context context, boolean start) {
        ActivityRecognitionClient activityRecognitionClient = ActivityRecognition.getClient(context);
        Intent intent = new Intent( context, MovementDetectorService.class );
        PendingIntent updatesIntent = PendingIntent.getService( context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT );

        if (start) {
            int threshold = MainActivity.getIntPreference(context, "_detection_threshold");
            int interval = MainActivity.getIntPreference(context, "_detection_interval");
            if (interval < 120 && MainActivity.isActivityStarted()) {
                interval = 120;
            }
            LogUtils.i(MainActivity.LOG_TAG, "requesting updates every " + interval +" seconds with " + threshold + " fiability threshold");
            activityRecognitionClient.requestActivityUpdates(interval * 1000, updatesIntent);
        } else {
            LogUtils.i(MainActivity.LOG_TAG, "Stop requesting updates");
            activityRecognitionClient.removeActivityUpdates(updatesIntent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.i(MainActivity.LOG_TAG, "startForegroundService");
        startForeground(1, MainActivity.buildNotification(this.getApplicationContext(), null, null, null));
        requestUpdates(this.getApplicationContext(),true);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LogUtils.i(MainActivity.LOG_TAG, "destroyForegroundService");
        requestUpdates(this.getApplicationContext(),false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case if services are bound (Bound Services).
        return null;
    }
}
