package com.github.axet.hourlyreminder.services;

import android.app.Notification;
import android.app.NotificationManager;
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
import android.support.v4.app.NotificationCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.activities.MainActivity;
import com.github.axet.hourlyreminder.alarms.Alarm;
import com.github.axet.hourlyreminder.alarms.Reminder;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.app.SoundConfig;

import java.util.Calendar;
import java.util.List;
import java.util.TreeSet;

/**
 * System Alarm Manager notifies this service to create/stop alarms.
 * <p/>
 * All Alarm notifications clicks routed to this service.
 */
public class AlarmService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = AlarmService.class.getSimpleName();

    // upcoming notification alarm action. Triggers notification upcoming.
    public static final String REGISTER = AlarmService.class.getCanonicalName() + ".REGISTER";
    // upcoming notification alarm action. Triggers notification upcoming.
    public static final String NOTIFICATION = AlarmService.class.getCanonicalName() + ".NOTIFICATION";
    // snooze
    public static final String SNOOZE = AlarmService.class.getCanonicalName() + ".SNOOZE";
    // cancel alarm
    public static final String CANCEL = HourlyApplication.class.getCanonicalName() + ".CANCEL";
    // alarm broadcast, triggers sound
    public static final String ALARM = HourlyApplication.class.getCanonicalName() + ".ALARM";
    // reminder broadcast triggers sound
    public static final String REMINDER = HourlyApplication.class.getCanonicalName() + ".REMINDER";
    // dismiss current alarm action
    public static final String DISMISS = HourlyApplication.class.getCanonicalName() + ".DISMISS";

    public static final String ALARMINFO = "alarminfo";

    // minutes
    public static final int ALARM_AUTO_OFF = 15; // if no auto snooze enabled wait 15 min
    public static final int ALARM_SNOOZE_AUTO_OFF = 45; // if auto snooze enabled or manually snoozed wait 45 min

    public static void start(Context context) {
        Intent intent = new Intent(context, AlarmService.class);
        intent.setAction(REGISTER);
        context.startService(intent);
    }

    public static void startClock(Context context) { // https://stackoverflow.com/questions/3590955
        PackageManager packageManager = context.getPackageManager();
        Intent alarmClockIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        alarmClockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        String clockImpls[][] = {
                {"HTC Alarm Clock", "com.htc.android.worldclock", "com.htc.android.worldclock.WorldClockTabControl"},
                {"Standar Alarm Clock", "com.android.deskclock", "com.android.deskclock.AlarmClock"},
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
            } catch (PackageManager.NameNotFoundException e) {
                ;
            }
        }

        Intent openClockIntent = new Intent(AlarmClock.ACTION_SET_ALARM);
        openClockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(openClockIntent);
    }

    public static void snooze(Context context, FireAlarmService.FireAlarm a) {
        Intent intent = new Intent(context, AlarmService.class);
        intent.setAction(SNOOZE);
        intent.putExtra("state", a.save().toString());
        context.startService(intent);

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        Integer min = Integer.valueOf(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_DELAY, "10"));
        Toast.makeText(context, context.getString(R.string.snoozed_for) + " " + HourlyApplication.formatLeftExact(context, min * 60 * 1000), Toast.LENGTH_LONG).show();
    }

    Sound sound;
    List<Alarm> alarms;
    List<ReminderSet> reminders;
    PowerManager.WakeLock wl;
    PowerManager.WakeLock wlCpu;
    Handler handler = new Handler();
    Runnable wakeClose = new Runnable() {
        @Override
        public void run() {
            wakeClose();
        }
    };
    AlarmManager am = new AlarmManager(this);
    OptimizationPreferenceCompat.ServiceReceiver optimization;

    public AlarmService() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        optimization = new OptimizationPreferenceCompat.ServiceReceiver(this, getClass(), HourlyApplication.PREFERENCE_OPTIMIZATION) {
            @Override
            public void check() {
            }
        };
        sound = new Sound(this);
        alarms = HourlyApplication.loadAlarms(this);
        reminders = HourlyApplication.loadReminders(this);

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

        if (sound != null) {
            sound.close();
            sound = null;
        }

        if (optimization != null) {
            optimization.close();
            optimization = null;
        }

        am.close();

        wakeClose();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        am.update();
        if (optimization.onStartCommand(intent, flags, startId)) {
            Log.d(TAG, "onStartCommand restart"); // crash fail
            alarms = HourlyApplication.loadAlarms(this);
            reminders = HourlyApplication.loadReminders(this);
            registerNextAlarm();
        }

        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand " + action);
            if (action != null) {
                if (action.equals(NOTIFICATION)) {
                    long time = intent.getLongExtra("time", 0);
                    showNotificationUpcoming(time);
                    registerNextAlarm();
                } else if (action.equals(CANCEL)) {
                    long time = intent.getLongExtra("time", 0);
                    tomorrow(time);
                } else if (action.equals(DISMISS)) {
                    FireAlarmService.dismissActiveAlarm(this);
                } else if (action.equals(ALARM) || action.equals(REMINDER)) {
                    long time = intent.getLongExtra("time", 0);
                    soundAlarm(time);
                } else if (action.equals(REGISTER)) {
                    alarms = HourlyApplication.loadAlarms(this);
                    reminders = HourlyApplication.loadReminders(this);
                    registerNextAlarm();
                } else if (action.equals(SNOOZE)) {
                    FireAlarmService.FireAlarm a = new FireAlarmService.FireAlarm(intent.getStringExtra("state"));
                    snooze(a.ids);
                }
            } else {
                registerNextAlarm();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    // create list for hour reminders. 'time' list for reminder (hronological order)
    public TreeSet<Long> generateReminders(Calendar cur) {
        TreeSet<Long> alarms = new TreeSet<>();

        for (ReminderSet rr : reminders) {
            if (rr.enabled) {
                for (Reminder r : rr.list) {
                    if (r.enabled)
                        alarms.add(r.getTime());
                }
            }
        }

        return alarms;
    }

    // create list for alarms. 'time' list for alarms (hronological order)
    public TreeSet<Long> generateAlarms() {
        TreeSet<Long> alarms = new TreeSet<>();

        for (Alarm a : this.alarms) {
            if (a.enabled)
                alarms.add(a.getTime());
        }

        return alarms;
    }

    // cancel alarm 'time' by set it time for day+1 (same hour:min)
    public void tomorrow(long time) {
        for (Alarm a : alarms) {
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

        for (ReminderSet rr : reminders) {
            if (rr.enabled) {
                for (Reminder r : rr.list) {
                    if (r.getTime() == time && r.enabled) {
                        r.setTomorrow();
                    }
                }
            }
        }

        HourlyApplication.save(this, alarms, reminders);
        registerNextAlarm();
    }

    // register alarm event for next one.
    //
    // scan all alarms and hourly reminders and register net one
    //
    public void registerNextAlarm() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        TreeSet<Long> all = new TreeSet<>();
        TreeSet<Long> reminders;
        TreeSet<Long> alarms;

        Calendar cur = Calendar.getInstance();

        // check hourly reminders
        reminders = generateReminders(cur);
        all.addAll(reminders);

        // check alarms
        alarms = generateAlarms();
        all.addAll(alarms);

        Intent alarmIntent = new Intent(this, AlarmService.class).setAction(ALARM);
        Intent reminderIntent = new Intent(this, AlarmService.class).setAction(REMINDER);

        if (all.isEmpty()) {
            OptimizationPreferenceCompat.setKillCheck(this, 0, HourlyApplication.PREFERENCE_NEXT);
            updateNotificationUpcomingAlarm(0);
        } else {
            long time = all.first();
            OptimizationPreferenceCompat.setKillCheck(this, time, HourlyApplication.PREFERENCE_NEXT);
            updateNotificationUpcomingAlarm(time);
        }

        if (reminders.isEmpty()) {
            am.cancel(reminderIntent);
        } else {
            long time = reminders.first();

            reminderIntent.putExtra("time", time);

            Log.d(TAG, "Current: " + AlarmManager.formatTime(cur.getTimeInMillis()) + "; SetReminder: " + AlarmManager.formatTime(time));

            AlarmManager.Alarm a;
            if (shared.getBoolean(HourlyApplication.PREFERENCE_ALARM, true)) {
                a = am.setAlarm(time, reminderIntent, new Intent(this, MainActivity.class).setAction(MainActivity.SHOW_REMINDERS_PAGE).putExtra(ALARMINFO, true));
            } else {
                a = am.setExact(time, reminderIntent);
            }
            if (shared.getBoolean(HourlyApplication.PREFERENCE_ALARM, true)) // exact on time lock enabled only for reminders
                huaweiLock(time, a);
        }

        if (alarms.isEmpty()) {
            am.cancel(alarmIntent);
        } else {
            long time = alarms.first();

            alarmIntent.putExtra("time", time);

            Log.d(TAG, "Current: " + AlarmManager.formatTime(cur.getTimeInMillis()) + "; SetAlarm: " + AlarmManager.formatTime(time));

            AlarmManager.Alarm a = am.setAlarm(time, alarmIntent, new Intent(this, MainActivity.class).setAction(MainActivity.SHOW_ALARMS_PAGE));
            huaweiLock(time, a); // exact on time lock enabled always for alarms
        }
    }

    void huaweiLock(long time, AlarmManager.Alarm a) { // support for huawei trash phones
        if (!OptimizationPreferenceCompat.isHuawei(this))
            return;
        Calendar cur = Calendar.getInstance();
        Calendar upcoming = upcomingTime(time);
        if (cur.after(upcoming))
            a.wakeLock();
    }

    // register notification_upcoming alarm event for 'time' - 15min.
    //
    // service will call showNotificationUpcoming(time)
    //
    void updateNotificationUpcomingAlarm(long time) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        Intent upcomingIntent = new Intent(this, AlarmService.class).setAction(NOTIFICATION).putExtra("time", time);
        if (time == 0) {
            am.cancel(upcomingIntent);
            showNotificationUpcoming(0);
        } else {
            Calendar cur = Calendar.getInstance();

            Calendar cal = upcomingTime(time);

            if (cur.after(cal)) { // we already 15 before alarm, show notification_upcoming
                am.cancel(upcomingIntent);
                showNotificationUpcoming(time);
            } else {
                showNotificationUpcoming(0);
                long time15 = cal.getTimeInMillis(); // time to wait before show notification_upcoming
                if (shared.getBoolean(HourlyApplication.PREFERENCE_ALARM, true)) {
                    am.setAlarm(time15, upcomingIntent, time, new Intent(this, MainActivity.class).setAction(isAlarm(time) ? MainActivity.SHOW_ALARMS_PAGE : MainActivity.SHOW_REMINDERS_PAGE).putExtra(ALARMINFO, true));
                } else {
                    am.setExact(time15, upcomingIntent);
                }
            }
        }
    }

    boolean isAlarm(long time) {
        for (Alarm a : alarms) {
            if (a.getTime() == time && a.getEnable())
                return true;
        }
        return false;
    }

    boolean isReminder(long time) {
        for (ReminderSet rr : reminders) {
            if (rr.enabled) {
                for (Reminder r : rr.list) {
                    if (r.getTime() == time && r.enabled) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    int getRepeat(long time) {
        TreeSet<Integer> rep = new TreeSet<>();
        rep.add(60); // default 60 minutes == 15 minutes before alarm
        for (ReminderSet rr : reminders) {
            if (rr.enabled) {
                for (Reminder r : rr.list) {
                    if (r.getTime() == time && r.enabled) {
                        if (rr.repeat > 0) // negative means once per hour == 60 (already in the list), skip it
                            rep.add(rr.repeat); // add 15 or 5 or 30
                    }
                }
            }
        }
        return rep.first(); // sorted smallest first
    }

    Calendar upcomingTime(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);

        int repeat = getRepeat(time) * 60; // make seconds
        int sec = repeat / 12; // 60 / 4 = 15min; 60 / 12 = 5min
        cal.add(Calendar.SECOND, -sec);

        return cal;
    }

    // show upcoming alarm notification
    //
    // time - 0 cancel notification
    // time - upcoming alarm time, show text.
    public void showNotificationUpcoming(long time) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(HourlyApplication.PREFERENCE_NOTIFICATIONS, true))
            return;

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (time == 0) {
            notificationManager.cancel(HourlyApplication.NOTIFICATION_UPCOMING_ICON);
        } else {
            PendingIntent button = PendingIntent.getService(this, 0,
                    new Intent(this, AlarmService.class).setAction(CANCEL).putExtra("time", time),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            PendingIntent main = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class).setAction(MainActivity.SHOW_ALARMS_PAGE).putExtra("time", time),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            String subject = getString(R.string.UpcomingChime);
            if (isAlarm(time))
                subject = getString(R.string.UpcomingAlarm);
            String text = Alarm.format2412ap(this, time);
            for (Alarm a : alarms) {
                if (a.getTime() == time) {
                    if (a.isSnoozed()) {
                        text += " (" + getString(R.string.snoozed) + ": " + a.format2412ap() + ")";
                    }
                }
            }

            RemoteViews view = new RemoteViews(getPackageName(), HourlyApplication.getTheme(getBaseContext(), R.layout.notification_alarm_light, R.layout.notification_alarm_dark));
            view.setOnClickPendingIntent(R.id.notification_button, button);
            view.setOnClickPendingIntent(R.id.notification_base, main);
            view.setTextViewText(R.id.notification_subject, subject);
            view.setTextViewText(R.id.notification_text, text);
            view.setTextViewText(R.id.notification_button, getString(R.string.Cancel));

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setContentTitle(subject)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                    .setContent(view);

            if (Build.VERSION.SDK_INT < 11)
                builder.setContentIntent(main);

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            notificationManager.notify(HourlyApplication.NOTIFICATION_UPCOMING_ICON, builder.build());
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
        for (Alarm a : alarms) { // here can be two alarms with same time
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
        for (final ReminderSet rr : reminders) {
            if (rr.enabled && rr.last < time) {
                for (Reminder r : rr.list) {
                    if (r.isSoundAlarm(time) && r.enabled) {
                        // calling setNext is more safe. if this alarm have to fire today we will reset it
                        // to the same time. if it is already past today's time (as we expect) then it will
                        // be set for tomorrow.
                        //
                        // also safe if we moved to another timezone.
                        r.setNext();
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

        if (alarm != null) {
            FireAlarmService.activateAlarm(this, alarm);
        }

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
            HourlyApplication.save(this, alarms, reminders);
            registerNextAlarm();
        } else {
            Log.d(TAG, "Time ignored: " + time);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged " + key);

        // do not update on pref change for alarms, too slow. use direct call from AlarmFragment
//        if (key.startsWith(HourlyApplication.PREFERENCE_ALARMS_PREFIX)) {
//            alarms = HourlyApplication.loadAlarms(this);
//            registerNextAlarm();
//        }

        // reset reminders on special events
        if (key.equals(HourlyApplication.PREFERENCE_ALARM)) {
            registerNextAlarm();
        }
    }

    static boolean dismiss(Context context, long settime, boolean snoozed) { // do we have to dismiss (due timeout) alarm?
        Calendar cur = Calendar.getInstance();
        return dismiss(context, cur, settime, snoozed);
    }

    static boolean dismiss(Context context, Calendar cur, long settime, boolean snoozed) { // do we have to dismiss (due timeout) alarm?
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        Integer sec = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_AFTER, "0")); // snooze auto seconds
        int auto = ALARM_AUTO_OFF;
        if (sec > 0 || snoozed)
            auto = ALARM_SNOOZE_AUTO_OFF;

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(settime);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        cal.add(Calendar.MINUTE, auto);

        return cur.after(cal);
    }

    public void snooze(List<Long> ids) {
        Context context = this;

        // create old list, we need to check conflicts with old alarms only, not shifted
        TreeSet<Long> old = new TreeSet<>();
        for (Alarm a : alarms) {
            if (a.enabled)
                old.add(a.getTime());
        }

        for (Alarm a : alarms) {
            if (ids.contains(a.id)) {
                boolean b = a.enabled;
                a.snooze(); // auto enable
                if (!old.isEmpty() && a.getTime() >= old.first()) { // did we hit another enabled alarm? stop snooze
                    showNotificationMissed(context, a.getSetTime(), a.isSnoozed());
                    a.setEnable(b); // restore enable state && setNext
                } else {
                    final Calendar cur = Calendar.getInstance();
                    cur.setTimeInMillis(a.getTime());
                    if (dismiss(context, cur, a.getSetTime(), a.isSnoozed())) { // outdated by snooze timeout?
                        showNotificationMissed(context, a.getSetTime(), a.isSnoozed());
                        a.setEnable(b); // restore enable state && setNext
                    }
                }
            }
        }

        HourlyApplication.save(this, alarms, reminders);
        registerNextAlarm();
    }

    // show notification about missed alarm
    public static void showNotificationMissed(Context context, long settime, boolean snoozed) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);

        if (settime == 0) {
            notificationManager.cancel(HourlyApplication.NOTIFICATION_MISSED_ICON);
        } else {
            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
            Integer sec = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_AFTER, "0")); // snooze auto seconds
            int auto = ALARM_AUTO_OFF;
            if (sec > 0 || snoozed)
                auto = ALARM_SNOOZE_AUTO_OFF;

            PendingIntent main = PendingIntent.getActivity(context, 0,
                    new Intent(context, MainActivity.class).setAction(MainActivity.SHOW_ALARMS_PAGE).putExtra("time", settime),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            String text = context.getString(R.string.AlarmMissedAfter, Alarm.format2412ap(context, settime), auto);

            RemoteViews view = new RemoteViews(context.getPackageName(), HourlyApplication.getTheme(context, R.layout.notification_alarm_light, R.layout.notification_alarm_dark));
            view.setOnClickPendingIntent(R.id.notification_base, main);
            view.setTextViewText(R.id.notification_subject, context.getString(R.string.AlarmMissed));
            view.setTextViewText(R.id.notification_text, text);
            view.setViewVisibility(R.id.notification_button, View.GONE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                    .setContentTitle(context.getString(R.string.Alarm))
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                    .setContent(view);

            if (Build.VERSION.SDK_INT < 11)
                builder.setContentIntent(main);

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(Notification.VISIBILITY_PUBLIC);

            notificationManager.notify(HourlyApplication.NOTIFICATION_MISSED_ICON, builder.build());
        }
    }

    public static void showNotificationMissedConf(Context context, long settime) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);

        if (settime == 0) {
            notificationManager.cancel(HourlyApplication.NOTIFICATION_MISSED_ICON);
        } else {
            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

            PendingIntent main = PendingIntent.getActivity(context, 0,
                    new Intent(context, MainActivity.class).setAction(MainActivity.SHOW_ALARMS_PAGE).putExtra("time", settime),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            String text = context.getString(R.string.AlarmMissedConflict, Alarm.format2412ap(context, settime));

            RemoteViews view = new RemoteViews(context.getPackageName(), HourlyApplication.getTheme(context, R.layout.notification_alarm_light, R.layout.notification_alarm_dark));
            view.setOnClickPendingIntent(R.id.notification_base, main);
            view.setTextViewText(R.id.notification_subject, context.getString(R.string.AlarmMissed));
            view.setTextViewText(R.id.notification_text, text);
            view.setViewVisibility(R.id.notification_button, View.GONE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                    .setContentTitle(context.getString(R.string.Alarm))
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                    .setContent(view);

            if (Build.VERSION.SDK_INT < 11)
                builder.setContentIntent(main);

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(Notification.VISIBILITY_PUBLIC);

            notificationManager.notify(HourlyApplication.NOTIFICATION_MISSED_ICON, builder.build());
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
}
