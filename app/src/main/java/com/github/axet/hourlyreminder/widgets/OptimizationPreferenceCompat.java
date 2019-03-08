package com.github.axet.hourlyreminder.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.util.AttributeSet;

import com.github.axet.hourlyreminder.app.HourlyApplication;

public class OptimizationPreferenceCompat extends com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat {
    public static int BOOT_DELAY = 2 * 60 * 1000;

    public OptimizationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public OptimizationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public OptimizationPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OptimizationPreferenceCompat(Context context) {
        super(context);
    }

    public static boolean needBootWarning(Context context) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        long auto = shared.getLong(HourlyApplication.PREFERENCE_BOOT, -1);
        long install = shared.getLong(HourlyApplication.PREFERENCE_INSTALL, -1);
        if (auto == -1 && install == -1)
            return false; // freshly installed app, never ran before
        long now = System.currentTimeMillis();
        long boot = now - SystemClock.elapsedRealtime(); // boot time
        if (install > boot)
            return false; // app was installed after boot
        if (boot + BOOT_DELAY > now) // give 2 minutes to receive boot event
            return false; // 2 minutes maybe not enougth to receive boot event
        return boot > auto; // boot > auto = boot event never received
    }

    public static AlertDialog buildBootWarninig(Context context) {
        return new AlertDialog.Builder(context).setMessage("Application never received BOOT event, check if it has been removed from autostart").create();
    }
}
