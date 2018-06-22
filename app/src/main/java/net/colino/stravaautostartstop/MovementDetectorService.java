package net.colino.stravaautostartstop;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import android.app.IntentService;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Vibrator;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class MovementDetectorService extends IntentService	 {

    private static boolean activityStarted = false;
    private static boolean bicyclingStarted = false;
    private static boolean runningStarted = false;

    private static int currentMovement = -1;
    private static long lastMovementChange = System.currentTimeMillis();
    private static long triggerAt = 0;
    private static long startedAt = 0;

    public MovementDetectorService() {
        super("MovementDetectorService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        LogUtils.d(MainActivity.LOG_TAG, "Intent is "+intent.getAction());
        if (intent.getAction() != null && intent.getAction().equals("net.colino.stravaautostartstop.stop_strava")) {
            handleDetectedActivities(null, true);
            return;
        }

        if(ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            handleDetectedActivities( result.getProbableActivities(), false );
        }
    }

    private boolean isTimeoutReached() {
        long now = System.currentTimeMillis();

        long timeout = MainActivity.getIntPreference(this.getApplicationContext(), "_stop_timeout");
        return ((now - startedAt) / 1000) > timeout;
    }

    private void setupNotification(boolean stravaTriggerOK) {
        String details = null;
        String label = null;

        String timeSinceLastMovement = DateFormat.getTimeInstance().format(new Date(lastMovementChange));

        if ((System.currentTimeMillis() - lastMovementChange) / 1000 < MainActivity.getIntPreference(this.getApplicationContext(), "_stop_timeout")) {
            switch (currentMovement) {
                case DetectedActivity.ON_BICYCLE:
                    details = String.format(this.getApplicationContext().getString(R.string.movement_bicycling),
                            timeSinceLastMovement);
                    break;
                case DetectedActivity.RUNNING:
                    details = String.format(this.getApplicationContext().getString(R.string.movement_running),
                            timeSinceLastMovement);
                    break;
                case DetectedActivity.IN_VEHICLE:
                    details = String.format(this.getApplicationContext().getString(R.string.movement_in_vehicle),
                            timeSinceLastMovement);
                    break;
                case DetectedActivity.STILL:
                    details = String.format(this.getApplicationContext().getString(R.string.movement_still),
                            timeSinceLastMovement);
                    break;
                case DetectedActivity.WALKING:
                    details = String.format(this.getApplicationContext().getString(R.string.movement_walking),
                            timeSinceLastMovement);
                    break;
                case DetectedActivity.ON_FOOT:
                    details = String.format(this.getApplicationContext().getString(R.string.movement_on_foot),
                            timeSinceLastMovement);
                    break;
                case DetectedActivity.UNKNOWN:
                case DetectedActivity.TILTING:
                default:
                    details = null;
            }
        }

        if (activityStarted) {
           label = String.format(this.getApplicationContext().getString(R.string.strava_started_at),
                                    DateFormat.getTimeInstance().format(new Date(triggerAt)));
        } else if (!stravaTriggerOK){
            label = this.getApplicationContext().getString(R.string.strava_not_found);
        } else {
            label = null;
        }

        MainActivity.updateNotification(this.getApplicationContext(), label, details, activityStarted && stravaTriggerOK);
    }

    private void handleDetectedActivities(List<DetectedActivity> probableActivities, boolean forceStop) {
        boolean shouldStart = false;
        boolean shouldStop = false;
        boolean bicycling = false;
        boolean running = false;
        boolean updateNotification = false;
        boolean stravaTriggerOK = true;

        int threshold = MainActivity.getIntPreference(this.getApplicationContext(), "_detection_threshold");

        if (probableActivities != null) {
            for (DetectedActivity result : probableActivities) {
                LogUtils.i(MainActivity.LOG_TAG, getType(result.getType()) + " (confidence: " + result.getConfidence() + ")");
                if (result.getConfidence() >= threshold) {
                    switch (result.getType()) {
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
                            if (bicyclingStarted) {
                                shouldStop = true;
                            } /* else don't change anything. */
                            break;
                        case DetectedActivity.UNKNOWN:
                        case DetectedActivity.TILTING:
                            /* Don't change anything for those */
                            break;
                    }

                    if (result.getType() != currentMovement) {
                        lastMovementChange = System.currentTimeMillis();
                        currentMovement = result.getType();
                        updateNotification = true;
                    } else if ((System.currentTimeMillis() - lastMovementChange) / 1000 > MainActivity.getIntPreference(this.getApplicationContext(), "_stop_timeout")) {
                        updateNotification = true;
                    }

                    break;
                }
            }
        } else if (forceStop) {
            shouldStop = true;
            startedAt = 0;
        } else {
            LogUtils.e(MainActivity.LOG_TAG, "Wrong calling of handleDetectedActivities");
        }
        String status;
        if (bicycling && MainActivity.getBoolPreference(this.getApplicationContext(), "enable_bike_detection", true)) {
            shouldStart = true;
            status = "Bicycling : ";
        } else if (running && MainActivity.getBoolPreference(this.getApplicationContext(), "enable_run_detection", true)) {
            shouldStart = true;
            status = "Running : ";
        } else {
            status = "Idle";
        }

        if (shouldStart) {
            if (!activityStarted) {
                String activity = (bicycling ? "Ride" : "Run");
                /* start activity */
                status += "starting activity: " + activity;

                updateNotification = true;
                try {
                    sendStartIntent(activity);
                    triggerAt = System.currentTimeMillis();
                    activityStarted = true;
                    bicyclingStarted = bicycling;
                    runningStarted = running;

                } catch (ActivityNotFoundException e) {
                    LogUtils.e(MainActivity.LOG_TAG, "Strava not found");
                    stravaTriggerOK = false;
                }

            }
            /* refresh start time */
            startedAt = System.currentTimeMillis();
        } else if (shouldStop && activityStarted) {
            /* stop activity */
            if (isTimeoutReached()) {
                status += " stopping activity";
                try {
                    sendStopIntent();
                } catch (ActivityNotFoundException e) {
                    LogUtils.e(MainActivity.LOG_TAG, "Strava not found");
                    stravaTriggerOK = false;
                }
                activityStarted = false;
                bicyclingStarted = false;
                runningStarted = false;
                triggerAt = 0;
                updateNotification = true;
            } else {
                LogUtils.i(MainActivity.LOG_TAG, "not stopping : timeout not reached");
            }
        }

        LogUtils.i(MainActivity.LOG_TAG, "currently: " + status + " (detection threshold " + threshold+")");

        if (updateNotification) {
            setupNotification(stravaTriggerOK);
        }
    }

    private void vibrate() {
        if (MainActivity.getBoolPreference(this.getApplicationContext(), "vibrate", false)) {
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

    private void sendStartIntent(String type) throws ActivityNotFoundException {
        Intent i = new Intent(Intent.ACTION_RUN);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                 | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.setData(Uri.parse("http://strava.com/nfc/record"));
        i.putExtra("rideType", type);
        startActivity(i);
        LogUtils.i(MainActivity.LOG_TAG, "sent start intent " + type);
        vibrate();

        MainActivity.setActivityStarted(true);
    }

    private void sendStopIntent() throws ActivityNotFoundException {
        Intent i = new Intent(Intent.ACTION_RUN);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.setData(Uri.parse("http://strava.com/nfc/record/stop"));
        startActivity(i);
        LogUtils.i(MainActivity.LOG_TAG, "sent stop intent");
        vibrate();

        MainActivity.setActivityStarted(false);
    }
}
