package com.github.axet.hourlyreminder.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.axet.hourlyreminder.app.HourlyApplication;

public class OnUpgradeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        HourlyApplication.registerNext(context);
    }
}
