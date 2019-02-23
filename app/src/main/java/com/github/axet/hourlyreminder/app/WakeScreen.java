package com.github.axet.hourlyreminder.app;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.app.NotificationManagerCompat;
import com.github.axet.androidlibrary.widgets.AboutPreferenceCompat;
import com.github.axet.androidlibrary.widgets.NotificationChannelCompat;
import com.github.axet.hourlyreminder.R;

public class WakeScreen {
    public static int ID = -5; // notification wake id

    public static final String DOZE = "doze_enabled"; // Ambient Display
    public static final String LOCKN = "lock_screen_show_notifications"; // show notifications on lockscreen

    Context context;
    ContentResolver resolver;
    Notification n;
    NotificationManagerCompat nm;
    NotificationChannelCompat wake;
    PowerManager.WakeLock wl;
    PowerManager.WakeLock wlCpu;
    Handler handler = new Handler();
    Runnable wakeClose = new Runnable() {
        @Override
        public void run() {
            close();
        }
    };

    public WakeScreen(Context context) {
        this.context = context;
        this.resolver = context.getContentResolver();
        this.nm = NotificationManagerCompat.from(context);
        this.wake = new NotificationChannelCompat("wake", "Wake", NotificationManagerCompat.IMPORTANCE_HIGH);
        this.wake.setSound(null, null);
        this.wake.enableVibration(false);
        this.wake.create(context);
    }

    public void wake() {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn;
        if (Build.VERSION.SDK_INT >= 20)
            isScreenOn = pm.isInteractive();
        else
            isScreenOn = pm.isScreenOn();
        if (isScreenOn == false) {
            close();
            boolean doze = false;
            try {
                doze = Build.VERSION.SDK_INT >= 26 && Settings.Secure.getInt(resolver, DOZE) == 1 && Settings.Secure.getInt(resolver, LOCKN) == 1 && wake.getImportance() >= NotificationManagerCompat.IMPORTANCE_HIGH;
            } catch (Settings.SettingNotFoundException ignore) {
            }
            if (doze) {
                NotificationCompat.Builder b = new NotificationCompat.Builder(context);
                b.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                b.setSmallIcon(R.drawable.ic_notifications_black_24dp);
                n = b.build();
                NotificationChannelCompat.setChannelId(n, wake.channelId);
                nm.notify(ID, n);
            } else {
                wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, AboutPreferenceCompat.getApplicationName(context) + "_wakelock");
                wl.acquire();
                wlCpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, AboutPreferenceCompat.getApplicationName(context) + "_cpulock");
                wlCpu.acquire();
            }
            handler.postDelayed(wakeClose, 10 * AlarmManager.SEC1); // old phones crash on handle wl.acquire(10000)
        }
    }

    public void close() {
        if (wl != null) {
            if (wl.isHeld())
                wl.release();
            wl = null;
        }
        if (wlCpu != null) {
            if (wlCpu.isHeld())
                wlCpu.release();
            wlCpu = null;
        }
        if (n != null) {
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            nm.cancel(ID);
            n = null;
        }
        handler.removeCallbacks(wakeClose);
    }

    public void update() {
        handler.removeCallbacks(wakeClose); // remove previous wakeClose actions
        handler.postDelayed(wakeClose, 3 * AlarmManager.SEC1); // screen off after 3 seconds, even if playlist keep playing
    }
}
