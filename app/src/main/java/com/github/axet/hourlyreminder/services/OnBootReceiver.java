package com.github.axet.hourlyreminder.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.hourlyreminder.app.HourlyApplication;

public class OnBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        OptimizationPreferenceCompat.setBootInstallTime(context, HourlyApplication.PREFERENCE_BOOT, System.currentTimeMillis());
        AlarmService.registerNextAlarm(context);
    }
}
