package com.github.axet.hourlyreminder.app;

import android.content.Context;
import android.util.Log;

public class Toast extends com.github.axet.androidlibrary.widgets.Toast {
    public static String TAG = Toast.class.getSimpleName();

    public Toast(Context context, android.widget.Toast t, int d, CharSequence m) {
        super(context, t, d, m);
    }

    public static void Error(Context context, String msg, Throwable e) {
        Log.e(TAG, "unable to use flash", e);
        com.github.axet.androidlibrary.widgets.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show();
    }
}
