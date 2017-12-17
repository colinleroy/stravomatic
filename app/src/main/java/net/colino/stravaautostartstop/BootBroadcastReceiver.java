package net.colino.stravaautostartstop;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        LogUtils.i(MainActivity.LOG_TAG, "Got broadcast : "+intent.getAction());
        MainActivity.setupAlarm(context);
    }
}