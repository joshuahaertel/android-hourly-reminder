package com.github.axet.hourlyreminder.services;

import android.content.Context;
import android.content.Intent;

import com.github.axet.hourlyreminder.app.HourlyApplication;

public class OnExternalReceiver extends com.github.axet.androidlibrary.services.OnExternalReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isExternal(context))
            return;
        AlarmService.registerNextAlarm(context);
    }
}
