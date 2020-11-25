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
import android.os.Handler;
import android.os.IBinder;
import android.provider.AlarmClock;
import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.app.NotificationManagerCompat;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.services.PersistentService;
import com.github.axet.androidlibrary.widgets.RemoteNotificationCompat;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.activities.MainActivity;
import com.github.axet.hourlyreminder.alarms.Alarm;
import com.github.axet.hourlyreminder.alarms.Reminder;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.app.SoundConfig;
import com.github.axet.hourlyreminder.app.WakeScreen;

/**
 * System Alarm Manager notifies this service to create/stop alarms.
 * <p/>
 * All Alarm notifications clicks routed to this service.
 */
public class AlarmService extends PersistentService implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = AlarmService.class.getSimpleName();

    // upcoming notification alarm action. Triggers notification upcoming.
    public static final String NOTIFICATION = AlarmService.class.getCanonicalName() + ".NOTIFICATION";
    // cancel alarm
    public static final String CANCEL = HourlyApplication.class.getCanonicalName() + ".CANCEL";
    // alarm broadcast, triggers sound
    public static final String ALARM = HourlyApplication.class.getCanonicalName() + ".ALARM";
    // reminder broadcast triggers sound
    public static final String REMINDER = HourlyApplication.class.getCanonicalName() + ".REMINDER";

    HourlyApplication.ItemsStorage items;
    Sound sound;
    WakeScreen wake;
    Handler handler = new Handler();
    Runnable stopSelf = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "stopSelf");
            stopSelf();
        }
    };

    static {
        OptimizationPreferenceCompat.setEventServiceIcon(true);
    }

    public static boolean registerNext(Context context) {
        HourlyApplication.ItemsStorage items = HourlyApplication.from(context).items;
        boolean b = items.registerNextAlarm();
        return OptimizationPreferenceCompat.isPersistent(context, HourlyApplication.PREFERENCE_OPTIMIZATION, b);
    }

    public static void registerNextAlarm(Context context) {
        boolean b = registerNext(context);
        if (b)
            OptimizationPreferenceCompat.startService(context, new Intent(context, AlarmService.class));
        else
            context.stopService(new Intent(context, AlarmService.class));
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

        HourlyApplication app = HourlyApplication.from(this);
        sound = new Sound(this);
        items = app.items;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onCreateOptimization() {
        optimization = new OptimizationPreferenceCompat.ServiceReceiver(this, HourlyApplication.NOTIFICATION_PERSISTENT_ICON, HourlyApplication.PREFERENCE_OPTIMIZATION, HourlyApplication.PREFERENCE_NEXT) {
            @Override
            public Notification build(Intent intent) {
                PendingIntent main = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

                RemoteNotificationCompat.Builder builder = new RemoteNotificationCompat.Low(context, R.layout.notification_alarm);

                builder.setViewVisibility(R.id.notification_button, View.GONE);

                builder.setTheme(HourlyApplication.getTheme(context, R.style.AppThemeLight, R.style.AppThemeDark))
                        .setChannel(HourlyApplication.from(context).channelStatus)
                        .setImageViewTint(R.id.icon_circle, builder.getThemeColor(R.attr.colorButtonNormal))
                        .setTitle(getString(R.string.app_name))
                        .setText(getString(R.string.optimization_alive))
                        .setWhen(icon.notification)
                        .setMainIntent(main)
                        .setAdaptiveIcon(R.drawable.ic_launcher_foreground)
                        .setSmallIcon(R.drawable.ic_launcher_notification)
                        .setOngoing(true);

                return builder.build();
            }
        };
        optimization.create();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);

        if (wake != null) {
            wake.close();
            wake = null;
        }

        if (sound != null) {
            sound.close();
            sound = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        items.am.update();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onRestartCommand() {
        super.onRestartCommand();
        registerNext();
    }

    @Override
    public void onStartCommand(Intent intent) {
        super.onStartCommand(intent);
        String action = intent.getAction();
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
                    if (r.getTime() == time && r.enabled)
                        r.setTomorrow();
                }
            }
        }

        items.save();
        registerNext();
    }

    public void registerNext() {
        handler.removeCallbacks(stopSelf);
        boolean b = registerNext(this);
        if (!b) {
            sound.after(new Runnable() {
                @Override
                public void run() {
                    handler.post(stopSelf); // on power safe onStartCommand called twice for Notification and REMINDER in seq
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
                Log.d(TAG, "Sound Alarm " + Alarm.format24(time));
                if (alarm == null)
                    alarm = new FireAlarmService.FireAlarm(a);
                else
                    alarm.merge(a);
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
                                if (rlist == null)
                                    rlist = new Sound.Playlist(rr);
                                else
                                    rlist.merge(rr);
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
            Log.d(TAG, "Reminder " + AlarmManager.formatTime(time) + " a=" + rlist.beep + " s=" + rlist.speech + " r=" + rlist.isRingtone());
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (prefs.getBoolean(HourlyApplication.PREFERENCE_WAKEUP, true)) {
                Log.d(TAG, "Wake screen");
                if (wake == null)
                    wake = new WakeScreen(this);
                wake.wake();
            }
            SoundConfig.Silenced s = sound.playList(rlist, time, new Runnable() {
                @Override
                public void run() {
                    ; // do nothing
                }
            });
            sound.silencedToast(s, time);
        }

        if (alarm != null || rlist != null)
            items.save();
        else
            Log.d(TAG, "Time ignored: " + AlarmManager.formatTime(time)); // double fire, ignore second alarm
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
                    .setAdaptiveIcon(R.drawable.ic_launcher_foreground)
                    .setSmallIcon(R.drawable.ic_launcher_notification);

            nm.notify(HourlyApplication.NOTIFICATION_MISSED_ICON, builder.build());
        }
    }
}
