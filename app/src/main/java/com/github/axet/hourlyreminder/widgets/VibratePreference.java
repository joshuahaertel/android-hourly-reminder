package com.github.axet.hourlyreminder.widgets;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Vibrator;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.util.AttributeSet;

public class VibratePreference extends SwitchPreferenceCompat {
    public static final String[] PERMISSIONS_V = new String[]{Manifest.permission.VIBRATE};

    public VibratePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public VibratePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public VibratePreference(Context context) {
        this(context, null);
        create();
    }

    public void create() {
        onResume();
    }

    public void onResume() {
        if (!hasVibrator()) {
            setVisible(false);
        }
    }

    boolean hasVibrator() {
        Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT < 11)
            return true;
        return true;//v.hasVibrator();
    }
}
