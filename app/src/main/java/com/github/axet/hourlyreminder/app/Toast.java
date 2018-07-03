package com.github.axet.hourlyreminder.app;

import android.content.Context;
import android.util.Log;

public class Toast extends com.github.axet.androidlibrary.widgets.Toast {
    public static String TAG = Toast.class.getSimpleName();

    public static com.github.axet.androidlibrary.widgets.Toast Error(Context context, String msg, Throwable e) {
        Log.e(TAG, "unable to use flash", e);
        com.github.axet.androidlibrary.widgets.Toast t = com.github.axet.androidlibrary.widgets.Toast.Error(context, msg, e);
        t.show();
        return t;
    }

    public Toast(Context context, android.widget.Toast t, int d, CharSequence m) {
        super(context, t, d, m);
    }

}
