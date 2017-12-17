package net.colino.stravaautostartstop;
import android.util.Log;

public class LogUtils {
    public static void d(final String tag, String message) {
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            Log.d(tag, message);
        }
    }
    public static void i(final String tag, String message) {
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            Log.i(tag, message);
        }
    }
    public static void e(final String tag, String message) {
        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            Log.e(tag, message);
        }
    }
}
