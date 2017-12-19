package net.colino.stravaautostartstop;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import android.app.IntentService;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Vibrator;

import java.util.List;

public class MovementDetectorService extends IntentService	 {

    private static boolean activityStarted = false;

    private static long startedAt = 0;

    public MovementDetectorService() {
        super("MovementDetectorService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if(ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            handleDetectedActivities( result.getProbableActivities() );
        }
    }

    private boolean isTimeoutReached() {
        long now = System.currentTimeMillis();

        long timeout = MainActivity.getIntPreference(this, "_stop_timeout");
        return ((now - startedAt) / 1000) > timeout;
    }

    private void handleDetectedActivities(List<DetectedActivity> probableActivities) {
        boolean shouldStart = false;
        boolean shouldStop = false;
        boolean bicycling = false;
        boolean running = false;

        int threshold = MainActivity.getIntPreference(this, "_detection_threshold");

        for (DetectedActivity result : probableActivities) {
            LogUtils.i(MainActivity.LOG_TAG, getType(result.getType()) +" (confidence: " + result.getConfidence() + ")");
            if( result.getConfidence() >= threshold ) {
                switch(result.getType()) {
                    case DetectedActivity.ON_BICYCLE:
                        bicycling = true;
                        break;
                    case DetectedActivity.RUNNING:
                        running = true;
                        break;
                    case DetectedActivity.IN_VEHICLE:
                    case DetectedActivity.STILL:
                        shouldStop = true;
                        break;
                    case DetectedActivity.WALKING:
                    case DetectedActivity.ON_FOOT:
                    case DetectedActivity.UNKNOWN:
                    case DetectedActivity.TILTING:
                        /* Don't change anything for those */
                        break;
                }
            }
        }

        String status;
        if (bicycling && MainActivity.getBoolPreference(this, "enable_bike_detection", true)) {
            shouldStart = true;
            status = "Bicycling : ";
        } else if (running && MainActivity.getBoolPreference(this, "enable_run_detection", true)) {
            shouldStart = true;
            status = "Running : ";
        } else {
            status = "Idle";
        }

        if (shouldStart) {
            if (!activityStarted) {
                /* start activity */
                status += "starting activity";

                activityStarted = true;
                sendStartIntent(bicycling ? "Ride" : "Run");
            }
            /* refresh start time */
            startedAt = System.currentTimeMillis();
        } else if (shouldStop && activityStarted) {
            /* stop activity */
            if (isTimeoutReached()) {
                status += " stopping activity";
                sendStopIntent();
                activityStarted = false;
            } else {
                LogUtils.i(MainActivity.LOG_TAG, "not stopping : timeout not reached");
            }
        }

        LogUtils.i(MainActivity.LOG_TAG, "currently: " + status + " (detection threshold " + threshold+")");
    }

    private void vibrate() {
        if (MainActivity.getBoolPreference(this, "vibrate", false)) {
            Vibrator v = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
            long[] pattern = {0, 250, 250, 250, 250, 250, 250, 250, 250};
            if (v != null) {
                v.vibrate(pattern, -1);
            }
        }
    }

    private String getType(int type){
        if(type == DetectedActivity.UNKNOWN)
            return "unknown";
        else if(type == DetectedActivity.IN_VEHICLE)
            return "in Vehicle";
        else if(type == DetectedActivity.ON_BICYCLE)
            return "on Bicycle";
        else if(type == DetectedActivity.RUNNING)
            return "running";
        else if(type == DetectedActivity.WALKING)
            return "walking";
        else if(type == DetectedActivity.ON_FOOT)
            return "on Foot";
        else if(type == DetectedActivity.STILL)
            return "still";
        else if(type == DetectedActivity.TILTING)
            return "tilting";
        else
            return "";
    }

    private void sendStartIntent(String type){
        try {
            Intent i = new Intent(Intent.ACTION_RUN);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setData(Uri.parse("http://strava.com/nfc/record"));
            i.putExtra("rideType", type);
            i.putExtra("show_activity", false);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            LogUtils.e(MainActivity.LOG_TAG, "Strava not found");
        }
        LogUtils.i(MainActivity.LOG_TAG, "sent start intent " + type);
        vibrate();

        int interval = MainActivity.getIntPreference(this, "_detection_interval");
        if (interval < 120) {
            /* Don't ask to often while we're moving */
            MainActivity.requestUpdates(this, 120, true);
        }
    }

    private void sendStopIntent(){
        try {
            Intent i = new Intent(Intent.ACTION_RUN);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setData(Uri.parse("http://strava.com/nfc/record/stop"));
            i.putExtra("show_activity",false);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            LogUtils.e(MainActivity.LOG_TAG, "Strava not found");
        }
        LogUtils.i(MainActivity.LOG_TAG, "sent stop intent");
        vibrate();

        MainActivity.requestUpdates(this, 0, true);
    }
}
