package net.colino.stravaautostartstop;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBar;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatDelegate;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;

public class MainActivity extends PreferenceActivity  {

    public static String LOG_TAG = MainActivity.class.getPackage().getName();

    private static MainActivity mContext;

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

        private void addPreferencesListener(){
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            listener = new SharedPreferences.OnSharedPreferenceChangeListener() {

                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (key.equals("enable_bike_detection") || key.equals("enable_run_detection")) {
                        setupAlarm(mContext);
                    }
                    if (key.equals("_detection_interval") || key.equals("_detection_threshold")) {
                        LogUtils.i(MainActivity.LOG_TAG, "Updating detection parameters");
                        rescheduleAlarm(mContext);
                    }
                }
            };
            prefs.registerOnSharedPreferenceChangeListener(listener);
        }

        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);

            addPreferencesListener();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;

        setupAlarm(this);

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

    private static PendingIntent getAlarmPendingIntent(Context context) {
        Intent i = new Intent(context, OnAlarmReceiver.class);
        return PendingIntent.getBroadcast(context, 0, i, 0);
    }

    private static void scheduleAlarm(Context context, AlarmManager mgr, PendingIntent pi) {
        /* start service */
        startService(context);

        /* setup alarm */
        mgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                10 * 60 * 1000,
                pi);
    }

    private static boolean serviceStarted = false;

    public static void startService(Context context) {
        Intent i = new Intent(context, ForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i);
        } else {
            context.startService(i);
        }
        serviceStarted = true;
    }

    public static void stopService(Context context) {
        context.stopService(new Intent(context, ForegroundService.class));
        serviceStarted = false;
    }

    private static void cancelAlarm(Context context, AlarmManager mgr, PendingIntent pi) {
        /* cancel alarm */
        mgr.cancel(pi);
        /* stop updates */
        MainActivity.requestUpdates(context, false);
        /* stop service */
        MainActivity.stopService(context);

        NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(1);
        }
    }

    private static void rescheduleAlarm(Context context) {
        if (!MainActivity.shouldServiceRun(context) || !serviceStarted) {
            LogUtils.i(LOG_TAG, "no need to reschedule");
            return;
        }
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getAlarmPendingIntent(context);

        cancelAlarm(context, mgr, pi);
        scheduleAlarm(context, mgr, pi);
    }

    public static Notification buildNotification(Context context, String text) {
        NotificationCompat.Builder nBuilder;

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
                .setTimeoutAfter(10 * 1000);

        return nBuilder.build();
    }

    public static void requestUpdates(Context context, boolean start) {
        ActivityRecognitionClient activityRecognitionClient = ActivityRecognition.getClient(context);
        Intent intent = new Intent( context, MovementDetectorService.class );
        PendingIntent updatesIntent = PendingIntent.getService( context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT );

        if (start) {
            int threshold = MainActivity.getIntPreference(context, "_detection_threshold");
            int interval = MainActivity.getIntPreference(context, "_detection_interval");
            if (interval < 120 && activityStarted) {
                interval = 120;
            }
            LogUtils.i(MainActivity.LOG_TAG, "requesting updates every " + interval +" seconds with " + threshold + " fiability threshold");
            activityRecognitionClient.requestActivityUpdates(interval * 1000, updatesIntent);
        } else {
            LogUtils.i(LOG_TAG, "Stop requesting updates");
            activityRecognitionClient.removeActivityUpdates(updatesIntent);
        }
    }

    /* start alarm - avoids the service being killed in the background. */
    public static void setupAlarm(Context context) {
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = getAlarmPendingIntent(context);

        if (MainActivity.shouldServiceRun(context)) {
            LogUtils.i(MainActivity.LOG_TAG, "setting alarm up");
            scheduleAlarm(context, mgr, pi);
        } else {
            LogUtils.i(MainActivity.LOG_TAG, "setting alarm down");
            cancelAlarm(context, mgr, pi);
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
    public static boolean getBoolPreference(Context c, String key, boolean def) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        return prefs.getBoolean(key, def);
    }

    private static boolean activityStarted = false;

    public static void setActivityStarted(boolean started) {
        activityStarted = started;
    }
}
