package com.github.axet.hourlyreminder.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.AlarmClock;
import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.app.NotificationManagerCompat;
import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.RemoteNotificationCompat;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.activities.MainActivity;
import com.github.axet.hourlyreminder.alarms.Alarm;
import com.github.axet.hourlyreminder.alarms.Reminder;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.app.SoundConfig;

/**
 * System Alarm Manager notifies this service to create/stop alarms.
 * <p/>
 * All Alarm notifications clicks routed to this service.
 */
public class AlarmService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = AlarmService.class.getSimpleName();

    // upcoming notification alarm action. Triggers notification upcoming.
    public static final String NOTIFICATION = AlarmService.class.getCanonicalName() + ".NOTIFICATION";
    // cancel alarm
    public static final String CANCEL = HourlyApplication.class.getCanonicalName() + ".CANCEL";
    // alarm broadcast, triggers sound
    public static final String ALARM = HourlyApplication.class.getCanonicalName() + ".ALARM";
    // reminder broadcast triggers sound
    public static final String REMINDER = HourlyApplication.class.getCanonicalName() + ".REMINDER";

    PowerManager.WakeLock wl;
    PowerManager.WakeLock wlCpu;
    Handler handler = new Handler();
    Runnable wakeClose = new Runnable() {
        @Override
        public void run() {
            wakeClose();
        }
    };
    OptimizationPreferenceCompat.ServiceReceiver optimization;
    Notification notification;
    HourlyApplication.ItemsStorage items;
    Sound sound;

    public static void start(Context context) {
        Intent intent = new Intent(context, AlarmService.class);
        OptimizationPreferenceCompat.startService(context, intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, AlarmService.class);
        context.stopService(intent);
    }

    public static void startClock(Context context) { // https://stackoverflow.com/questions/3590955
        PackageManager packageManager = context.getPackageManager();
        Intent alarmClockIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        alarmClockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        String clockImpls[][] = {
                {"HTC Alarm Clock", "com.htc.android.worldclock", "com.htc.android.worldclock.WorldClockTabControl"},
                {"Standard Alarm Clock", "com.android.deskclock", "com.android.deskclock.AlarmClock"},
                {"Froyo Nexus Alarm Clock", "com.google.android.deskclock", "com.android.deskclock.DeskClock"},
                {"Moto Blur Alarm Clock", "com.motorola.blur.alarmclock", "com.motorola.blur.alarmclock.AlarmClock"},
                {"Samsung Galaxy Clock", "com.sec.android.app.clockpackage", "com.sec.android.app.clockpackage.ClockPackage"},
                {"Sony Ericsson Xperia Z", "com.sonyericsson.organizer", "com.sonyericsson.organizer.Organizer_WorldClock"},
                {"ASUS Tablets", "com.asus.deskclock", "com.asus.deskclock.DeskClock"}

        };

        for (int i = 0; i < clockImpls.length; i++) {
            String packageName = clockImpls[i][1];
            String className = clockImpls[i][2];
            try {
                ComponentName c = new ComponentName(packageName, className);
                packageManager.getActivityInfo(c, PackageManager.GET_META_DATA);
                alarmClockIntent.setComponent(c);
                context.startActivity(alarmClockIntent);
                return;
            } catch (PackageManager.NameNotFoundException ignore) {
            }
        }

        Intent openClockIntent = new Intent(AlarmClock.ACTION_SET_ALARM);
        openClockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(openClockIntent);
    }

    public AlarmService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        HourlyApplication app = HourlyApplication.from(this);
        sound = app.sound;
        items = app.items;

        optimization = new OptimizationPreferenceCompat.ServiceReceiver(this, getClass(), HourlyApplication.PREFERENCE_OPTIMIZATION) {
            @Override
            public void onReceive(Context context, Intent intent) {
                super.onReceive(context, intent);
                String a = intent.getAction();
                if (a != null && a.equals(OptimizationPreferenceCompat.ICON_UPDATE))
                    updateIcon(true);
            }
        };
        optimization.filters.addAction(OptimizationPreferenceCompat.ICON_UPDATE);
        optimization.create();

        updateIcon(true);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);

        if (optimization != null) {
            optimization.close();
            optimization = null;
        }

        updateIcon(false);

        wakeClose();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        items.am.update();
        if (optimization.onStartCommand(intent, flags, startId)) {
            Log.d(TAG, "onStartCommand restart"); // crash fail
            registerNext();
        }

        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand " + action);
            if (action != null) {
                if (action.equals(NOTIFICATION)) {
                    long time = intent.getLongExtra("time", 0);
                    items.showNotificationUpcoming(time);
                    registerNext();
                } else if (action.equals(CANCEL)) {
                    long time = intent.getLongExtra("time", 0);
                    tomorrow(time); // registerNext()
                } else if (action.equals(ALARM) || action.equals(REMINDER)) {
                    long time = intent.getLongExtra("time", 0);
                    soundAlarm(time); // registerNext()
                } else {
                    registerNext();
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    // cancel alarm 'time' by set it time for day+1 (same hour:min)
    public void tomorrow(long time) {
        for (Alarm a : items.alarms) {
            if (a.getTime() == time && a.enabled) {
                if (a.weekdaysCheck) {
                    // be safe for another timezone. if we moved we better call setNext().
                    // but here we have to jump over next alarm.
                    a.setTomorrow();
                } else {
                    a.setEnable(false);
                }
                HourlyApplication.toastAlarmSet(this, a);
            }
        }

        for (ReminderSet rr : items.reminders) {
            if (rr.enabled) {
                for (Reminder r : rr.list) {
                    if (r.getTime() == time && r.enabled) {
                        r.setTomorrow();
                    }
                }
            }
        }

        items.save();
        registerNext();
    }

    public void registerNext() {
        boolean b = items.registerNextAlarm();
        OptimizationPreferenceCompat.State state = OptimizationPreferenceCompat.getState(this, HourlyApplication.PREFERENCE_OPTIMIZATION);
        if (!state.icon && (Build.VERSION.SDK_INT >= 26 || !b)) {
            sound.after(new Runnable() {
                @Override
                public void run() {
                    stopSelf();
                }
            });
        }
    }

    // alarm come from service call (System Alarm Manager) for specified time
    //
    // we have to check what 'alarms' do we have at specified time (can be reminder + alarm)
    // and act properly.
    public void soundAlarm(final long time) {
        // find hourly reminder + alarm = combine proper sound notification_upcoming (can be merge beep, speech, ringtone)
        //
        // then sound alarm or hourly reminder

        FireAlarmService.FireAlarm alarm = null;
        for (Alarm a : items.alarms) { // here can be two alarms with same time
            if (a.getTime() == time && a.enabled) {
                Log.d(TAG, "Sound Alarm " + Alarm.format24(a.getTime()));
                if (alarm == null) {
                    alarm = new FireAlarmService.FireAlarm(a);
                } else {
                    alarm.merge(a);
                }
                if (!a.weekdaysCheck) {
                    // disable alarm after it goes off for non recurring alarms (!a.weekdays)
                    a.setEnable(false);
                } else {
                    // calling setNext is more safe. if this alarm have to fire today we will reset it
                    // to the same time. if it is already past today's time (as we expect) then it will
                    // be set for tomorrow.
                    //
                    // also safe if we moved to another timezone.
                    a.setNext();
                }
            }
        }

        Sound.Playlist rlist = null;
        for (final ReminderSet rr : items.reminders) {
            if (rr.enabled) {
                for (Reminder r : rr.list) {
                    if (r.isSoundAlarm(time) && r.enabled) {
                        // calling setNext is more safe. if this alarm have to fire today we will reset it
                        // to the same time. if it is already past today's time (as we expect) then it will
                        // be set for tomorrow.
                        //
                        // also safe if we moved to another timezone.
                        r.setNext();
                        if (rr.last < time) {
                            rr.last = time;
                            if (alarm == null) { // do not cross alarms
                                if (rlist == null) {
                                    rlist = new Sound.Playlist(rr);
                                } else {
                                    rlist.merge(rr);
                                }
                            } else { // merge reminder with alarm
                                alarm.merge(rr);
                            }
                        }
                    }
                }
            }
        }

        if (alarm != null)
            FireAlarmService.activateAlarm(this, alarm);

        if (rlist != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (prefs.getBoolean(HourlyApplication.PREFERENCE_WAKEUP, true))
                wakeScreen();
            SoundConfig.Silenced s = sound.playList(rlist, time, new Runnable() {
                @Override
                public void run() {
                    ; // do nothing
                }
            });
            sound.silencedToast(s, time);
            handler.removeCallbacks(wakeClose); // remove previous wakeClose actions
            handler.postDelayed(wakeClose, 3 * AlarmManager.SEC1); // screen off after 3 seconds, even if playlist keep playing
        }

        if (alarm != null || rlist != null) {
            items.save();
        } else {
            Log.d(TAG, "Time ignored: " + time);
        }
        registerNext();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged " + key);

        // do not update on pref change for alarms, too slow. use direct call from AlarmFragment
//        if (key.startsWith(HourlyApplication.PREFERENCE_ALARMS_PREFIX)) {
//            alarms = HourlyApplication.loadAlarms(this);
//            registerNext();
//        }

        // reset reminders on special events
        if (key.equals(HourlyApplication.PREFERENCE_ALARM))
            registerNext();
    }

    @SuppressLint("RestrictedApi")
    public static void showNotificationMissedConf(Service context, long settime) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);

        if (settime == 0) {
            nm.cancel(HourlyApplication.NOTIFICATION_MISSED_ICON);
        } else {
            PendingIntent main = PendingIntent.getActivity(context, 0,
                    new Intent(context, MainActivity.class).setAction(MainActivity.SHOW_ALARMS_PAGE).putExtra("time", settime),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            String text = context.getString(R.string.AlarmMissedConflict, Alarm.format2412ap(context, settime));

            RemoteNotificationCompat.Builder builder = new RemoteNotificationCompat.Builder(context, R.layout.notification_alarm);

            builder.setViewVisibility(R.id.notification_button, View.GONE);

            builder.setTheme(HourlyApplication.getTheme(context, R.style.AppThemeLight, R.style.AppThemeDark))
                    .setChannel(HourlyApplication.from(context).channelAlarms)
                    .setImageViewTint(R.id.icon_circle, builder.getThemeColor(R.attr.colorButtonNormal))
                    .setMainIntent(main)
                    .setTitle(context.getString(R.string.AlarmMissed))
                    .setText(text)
                    .setSmallIcon(R.drawable.ic_notifications_black_24dp);

            nm.notify(HourlyApplication.NOTIFICATION_MISSED_ICON, builder.build());
        }
    }

    void wakeScreen() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn;
        if (Build.VERSION.SDK_INT >= 20) {
            isScreenOn = pm.isInteractive();
        } else {
            isScreenOn = pm.isScreenOn();
        }
        if (isScreenOn == false) {
            wakeClose();
            wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, getString(R.string.app_name) + "_wakelock");
            wl.acquire();
            wlCpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getString(R.string.app_name) + "_cpulock");
            wlCpu.acquire();
            handler.postDelayed(wakeClose, 10 * AlarmManager.SEC1); // old phones crash on handle wl.acquire(10000)
        }
    }

    void wakeClose() {
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
        handler.removeCallbacks(wakeClose);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        optimization.onTaskRemoved(rootIntent);
    }

    Notification build() {
        PendingIntent main = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteNotificationCompat.Builder builder = new RemoteNotificationCompat.Low(this, R.layout.notification_alarm);

        builder.setViewVisibility(R.id.notification_button, View.GONE);

        builder.setTheme(HourlyApplication.getTheme(this, R.style.AppThemeLight, R.style.AppThemeDark))
                .setChannel(HourlyApplication.from(this).channelStatus)
                .setImageViewTint(R.id.icon_circle, builder.getThemeColor(R.attr.colorButtonNormal))
                .setTitle(getString(R.string.app_name))
                .setText(TAG)
                .setWhen(notification)
                .setMainIntent(main)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notifications_black_24dp);

        return builder.build();
    }

    void updateIcon(boolean show) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        OptimizationPreferenceCompat.State state = OptimizationPreferenceCompat.getState(this, HourlyApplication.PREFERENCE_OPTIMIZATION);
        if (show && (state.icon || Build.VERSION.SDK_INT >= 26 && getApplicationInfo().targetSdkVersion >= 26)) {
            Notification n = build();
            if (notification == null)
                startForeground(HourlyApplication.NOTIFICATION_PERSISTENT_ICON, n);
            else
                nm.notify(HourlyApplication.NOTIFICATION_PERSISTENT_ICON, n);
            notification = n;
        } else {
            stopForeground(false);
            nm.cancel(HourlyApplication.NOTIFICATION_PERSISTENT_ICON);
        }
    }
}
