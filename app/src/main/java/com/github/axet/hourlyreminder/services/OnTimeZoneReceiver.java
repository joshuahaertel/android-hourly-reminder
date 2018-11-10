package com.github.axet.hourlyreminder.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.github.axet.hourlyreminder.app.HourlyApplication;

public class OnTimeZoneReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED))
            HourlyApplication.registerNext(context);
    }
}
