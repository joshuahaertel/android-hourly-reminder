package com.github.axet.hourlyreminder.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.hourlyreminder.app.HourlyApplication;

public class OnUpgradeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        shared.edit().putLong(HourlyApplication.PREFERENCE_INSTALL, System.currentTimeMillis()).commit();
        HourlyApplication.registerNext(context);
    }
}
