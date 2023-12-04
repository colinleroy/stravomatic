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

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatDelegate;

public class MainActivity extends PreferenceActivity  {

    public static String LOG_TAG = MainActivity.class.getPackage().getName();

    private AppCompatDelegate mDelegate;

    private AppCompatDelegate getDelegate() {
        if (mDelegate == null) {
            mDelegate = AppCompatDelegate.create(this, null);
        }
        return mDelegate;
    }

    public ActionBar getSupportActionBar() {
        return getDelegate().getSupportActionBar();
    }

    public static class MyPreferenceFragment extends PreferenceFragment
    {
        SharedPreferences.OnSharedPreferenceChangeListener listener;

        private void addPreferencesListener() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity().getApplicationContext());
            listener = new SharedPreferences.OnSharedPreferenceChangeListener() {

                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (key.equals("enable_bike_detection") || key.equals("enable_run_detection")) {
                        LogUtils.i(MainActivity.LOG_TAG, "Handling service change");
                        setupService(MyPreferenceFragment.this.getActivity().getApplicationContext(), false);
                    }
                    if (key.equals("_detection_interval") || key.equals("_detection_threshold")) {
                        LogUtils.i(MainActivity.LOG_TAG, "Updating detection parameters");
                        setupService(MyPreferenceFragment.this.getActivity().getApplicationContext(), true);
                    }
                }
            };
            LogUtils.i(LOG_TAG, "register prefs change listener");
            prefs.registerOnSharedPreferenceChangeListener(listener);

            Preference batteryWhitelistButton = findPreference("battery_whitelist_button");

            if (batteryWhitelistButton != null) {
                PowerManager pm = (PowerManager) this.getActivity().getApplicationContext().getSystemService(Context.POWER_SERVICE);
                if (!pm.isIgnoringBatteryOptimizations(this.getActivity().getApplicationContext().getPackageName())) {
                    batteryWhitelistButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            Intent whiteListIntent = new Intent();
                            whiteListIntent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            startActivity(whiteListIntent);
                            return true;
                        }
                    });
                } else {
                    getPreferenceScreen().removePreference(batteryWhitelistButton);
                }
            }

            Preference systemModal = findPreference("system_modal");

            if (systemModal != null) {
                if (Build.VERSION.SDK_INT > 28 && !Settings.canDrawOverlays(this.getActivity())) {
                    systemModal.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + "net.colino.stravaautostartstop"));
                            startActivityForResult(intent, 1);
                            return true;
                        }
                    });
                } else {
                    getPreferenceScreen().removePreference(systemModal);
                }
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode,  Intent data) {
            switch (requestCode) {
                case 1: {
                    // Check if the app get the permission
                    if (Settings.canDrawOverlays(this.getActivity())) {
                        Preference batteryModal = findPreference("system_modal");
                        getPreferenceScreen().removePreference(batteryModal);
                    }
                }
            }
        }

        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);

            addPreferencesListener();
        }

        @Override
        public void onDestroy()
        {
            super.onDestroy();
            LogUtils.i(LOG_TAG, "unregister prefs change listener");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity().getApplicationContext());
            prefs.unregisterOnSharedPreferenceChangeListener(listener);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupService(this.getApplicationContext(), false);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the action bar for the title.
            actionBar.setDisplayShowCustomEnabled(true);
        }
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();

    }

        /**
         * This method stops fragment injection in malicious applications.
         * Make sure to deny any unknown fragments here.
         */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName);
    }

    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);
        }
    }

    private static boolean serviceStarted = false;

    private static PendingIntent getAlarmPendingIntent(Context context) {
        Intent i = new Intent(context, OnAlarmReceiver.class);
        return PendingIntent.getBroadcast(context, 0, i, 0);
    }

    public static void startService(Context context) {
        if (serviceStarted || !MainActivity.shouldServiceRun(context)) {
            return;
        }

        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getAlarmPendingIntent(context);
        mgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + (60 * 60 * 1000),
                60 * 60 * 1000,
                pi);

        Intent i = new Intent(context.getApplicationContext(), ForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i);
        } else {
            context.startService(i);
        }
        serviceStarted = true;
    }

    private static void stopService(Context context) {
        /* stop service */
        if (!serviceStarted) {
            return;
        }

        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getAlarmPendingIntent(context);
        mgr.cancel(pi);

        context.stopService(new Intent(context.getApplicationContext(), ForegroundService.class));
        serviceStarted = false;

        NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(1);
        }
    }

    public static void updateNotification(Context context, String text, String bigText, boolean addStopIntent) {
        NotificationCompat.Action action = null;
        if (!MainActivity.shouldServiceRun(context)) {
            return;
        }

        if (addStopIntent) {
            Intent stopStravaIntent = new Intent(context, MovementDetectorService.class);
            stopStravaIntent.setAction("net.colino.stravaautostartstop.stop_strava");
            PendingIntent pendingIntent = PendingIntent.getService(context, 100, stopStravaIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            action = new
                    NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.stop_strava_now), pendingIntent);
        }

        Notification n = buildNotification(context, text, bigText, action);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(1, n);
        }
    }

    public static Notification buildNotification(Context context, String text, String bigText, NotificationCompat.Action action) {
        NotificationCompat.Builder nBuilder;

        String label = context.getString(R.string.detection_started);
        String details = getNotificationDetails(context.getApplicationContext());

        if (text == null) {
            text = label;
        }

        if (bigText != null) {
            details = text + "\n" + bigText;
        } else {
            details = text + "\n" + details;
        }

        bigText = details;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nBuilder = new NotificationCompat.Builder(context, "sass_channel");
        } else {
            nBuilder = new NotificationCompat.Builder(context);
        }

        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        nBuilder.setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_status_icon)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setTimeoutAfter(10 * 1000)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText));

        if (action != null) {
            nBuilder.addAction(action);
        }

        return nBuilder.build();
    }

    public static void setupService(Context context, boolean updateParameters) {
        if (MainActivity.shouldServiceRun(context)) {
            if (serviceStarted && updateParameters) {
                LogUtils.i(MainActivity.LOG_TAG, "restarting service");
                stopService(context);
            }
            if(!serviceStarted) {
                LogUtils.i(MainActivity.LOG_TAG, "setting service up");
                startService(context);
            }
        } else {
            if (serviceStarted) {
                LogUtils.i(MainActivity.LOG_TAG, "setting service down");
                stopService(context);
            }
        }
    }

    public static boolean shouldServiceRun(Context c) {
        boolean bike_detection = MainActivity.getBoolPreference(c, "enable_bike_detection", true);
        boolean run_detection = MainActivity.getBoolPreference(c, "enable_run_detection", true);

        return bike_detection || run_detection;
    }

    public static String getStringPreference(Context c, String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        return prefs.getString(key, "");
    }

    public static int getIntPreference(Context c, String key) {
        String s = getStringPreference(c, key);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            LogUtils.e(MainActivity.LOG_TAG, "Error converting '"+s+"' to integer");
        }
        return -1;
    }

    public static String getNotificationDetails(Context c) {
        String[] intervalValues = c.getResources().getStringArray(R.array.detection_interval_values);
        String[] thresholdValues = c.getResources().getStringArray(R.array.detection_threshold_values);
        String[] intervalEntries = c.getResources().getStringArray(R.array.detection_interval_entries);
        String[] thresholdEntries = c.getResources().getStringArray(R.array.detection_threshold_entries);
        String intervalValue = getStringPreference(c, "_detection_interval");
        String thresholdValue = getStringPreference(c, "_detection_threshold");
        int intervalIndex = -1;
        int thresholdIndex = -1;
        int i = 0;

        for (String s: intervalValues) {
            if (s.equals(intervalValue)) {
                intervalIndex = i;
                break;
            }
            i++;
        }

        i = 0;
        for (String s: thresholdValues) {
            if (s.equals(thresholdValue)) {
                thresholdIndex = i;
                break;
            }
            i++;
        }
        if (intervalIndex != -1 && thresholdIndex != -1
         && intervalIndex < intervalEntries.length
         && thresholdIndex < thresholdEntries.length) {
            return String.format(c.getString(R.string.notification_details),
                    intervalEntries[intervalIndex],
                    thresholdEntries[thresholdIndex]);
        }
        return null;
    }

    public static boolean getBoolPreference(Context c, String key, boolean def) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        return prefs.getBoolean(key, def);
    }

    private static boolean activityStarted = false;

    public static void setActivityStarted(boolean started) {
        activityStarted = started;
    }

    public static boolean isActivityStarted() {
        return activityStarted;
    }
}
