package com.github.axet.hourlyreminder.app;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.SharedPreferencesCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.app.MainApplication;
import com.github.axet.androidlibrary.widgets.NotificationChannelCompat;
import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.RemoteNotificationCompat;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.activities.MainActivity;
import com.github.axet.hourlyreminder.alarms.Alarm;
import com.github.axet.hourlyreminder.alarms.Reminder;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.alarms.Week;
import com.github.axet.hourlyreminder.alarms.WeekTime;
import com.github.axet.hourlyreminder.services.AlarmService;
import com.github.axet.hourlyreminder.services.FireAlarmService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class HourlyApplication extends MainApplication {
    public static final int NOTIFICATION_UPCOMING_ICON = 0;
    public static final int NOTIFICATION_ALARM_ICON = 1;
    public static final int NOTIFICATION_MISSED_ICON = 2;
    public static final int NOTIFICATION_FALLBACK_ICON = 3;
    public static final int NOTIFICATION_PERSISTENT_ICON = 4;

    public static final String ALARMINFO = "alarminfo";

    public static final String PREFERENCE_VERSION = "version";

    public static final String PREFERENCE_OPTIMIZATION = "optimization";
    public static final String PREFERENCE_ALARM = "alarm"; // exact timing. use alarm type for reminders
    public static final String PREFERENCE_ALARMS_PREFIX = "alarm_";

    public static final String PREFERENCE_REMINDERS_PREFIX = "reminders_";

    public static final String PREFERENCE_BEEP_CUSTOM = "beep_custom";

    // reminders <=1.5.9
    public static final String PREFERENCE_ENABLED = "enabled";
    public static final String PREFERENCE_HOURS = "hours";
    public static final String PREFERENCE_DAYS = "weekdays";
    public static final String PREFERENCE_REPEAT = "repeat";
    public static final String PREFERENCE_BEEP = "beep";
    public static final String PREFERENCE_CUSTOM_SOUND = "custom_sound";
    public static final String PREFERENCE_CUSTOM_SOUND_OFF = "off";
    public static final String PREFERENCE_RINGTONE = "ringtone";
    public static final String PREFERENCE_SOUND = "sound";
    public static final String PREFERENCE_SPEAK = "speak";

    public static final String PREFERENCE_VOLUME = "volume";
    public static final String PREFERENCE_INCREASE_VOLUME = "increasing_volume";
    public static final String PREFERENCE_NOTIFICATIONS = "notifications";

    public static final String PREFERENCE_THEME = "theme";
    public static final String PREFERENCE_SPEAK_AMPM = "speak_ampm";

    public static final String PREFERENCE_MUSICSILENCE = "musicsilence";
    public static final String PREFERENCE_CALLSILENCE = "callsilence";
    public static final String PREFERENCE_PHONESILENCE = "phonesilence";

    public static final String PREFERENCE_WEEKSTART = "weekstart";

    public static final String PREFERENCE_VIBRATE = "vibrate";

    public static final String PREFERENCE_LAST_PATH = "lastpath";

    public static final String PREFERENCE_LANGUAGE = "language";

    public static final String PREFERENCE_ACTIVE_ALARM = "active_alarm";

    public static final String PREFERENCE_SNOOZE_AFTER = "snooze_after";
    public static final String PREFERENCE_SNOOZE_DELAY = "snooze_time";

    public static final String PREFERENCE_WAKEUP = "wakeup";

    public static final String PREFERENCE_SPEAK_CUSTOM = "speak_custom";

    public static final String PREFERENCE_NEXT = "next";

    public static final String PREFERENCE_FLASH = "flash";

    public NotificationChannelCompat channelStatus;
    public NotificationChannelCompat channelAlarms;
    public NotificationChannelCompat channelErrors;
    public NotificationChannelCompat channelUpcoming;

    public ItemsStorage items;
    public Sound sound;

    public static HourlyApplication from(Context context) {
        return (HourlyApplication) MainApplication.from(context);
    }

    public static void registerNext(Context context) {
        ItemsStorage items = HourlyApplication.from(context).items;
        boolean b = items.registerNextAlarm();
        OptimizationPreferenceCompat.State state = OptimizationPreferenceCompat.getState(context, HourlyApplication.PREFERENCE_OPTIMIZATION);
        if (Build.VERSION.SDK_INT < 26 && b || state.icon) // always running service for <API26
            AlarmService.start(context);
        else
            AlarmService.stop(context);
    }

    public static void toastAlarmSet(Context context, WeekTime a) {
        if (!a.enabled) {
            Toast.makeText(context, context.getString(R.string.alarm_disabled), Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar cur = Calendar.getInstance();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(a.getTime());

        long diff = cal.getTimeInMillis() - cur.getTimeInMillis();

        String str = formatLeftExact(context, diff);

        Toast.makeText(context, context.getString(R.string.alarm_set_for, str), Toast.LENGTH_SHORT).show();
    }

    public static int getTheme(Context context, int light, int dark) {
        return MainApplication.getTheme(context, PREFERENCE_THEME, light, dark, context.getString(R.string.Theme_Dark));
    }

    public static String getQuantityString(Context context, Locale locale, int id, int n, Object... formatArgs) {
        Resources res = context.getResources();
        Configuration conf = res.getConfiguration();
        Locale savedLocale = conf.locale;
        if (Build.VERSION.SDK_INT >= 17)
            conf.setLocale(locale);
        else
            conf.locale = locale;
        res.updateConfiguration(conf, null);

        String str = res.getQuantityString(id, n, formatArgs);

        if (Build.VERSION.SDK_INT >= 17)
            conf.setLocale(savedLocale);
        else
            conf.locale = savedLocale;
        res.updateConfiguration(conf, null);

        return str;
    }

    @TargetApi(17)
    public static Resources getStringNewConfig(Context context, Locale locale, int id, Object... formatArgs) { // this method fails, for locale "ru_RU" and requested string in "ru"
        Configuration conf = context.getResources().getConfiguration();
        conf = new Configuration(conf);
        conf.setLocale(locale);
        Context localizedContext = context.createConfigurationContext(conf);
        return localizedContext.getResources();
    }

    public static String getStringUpdateConfig(Context context, Locale locale, int id, Object... formatArgs) { // this method fails, for locale "ru_RU" and requested string in "ru"
        Resources res = context.getResources();
        Configuration conf = res.getConfiguration();
        Locale savedLocale = conf.locale;
        if (Build.VERSION.SDK_INT >= 17)
            conf.setLocale(locale);
        else
            conf.locale = locale;
        res.updateConfiguration(conf, null);

        String str;
        if (formatArgs.length == 0)
            str = res.getString(id);
        else
            str = res.getString(id, formatArgs);

        if (Build.VERSION.SDK_INT >= 17)
            conf.setLocale(savedLocale);
        else
            conf.locale = savedLocale;
        res.updateConfiguration(conf, null);

        return str;
    }

    public static String getStringNewRes(Context context, Locale locale, int id, Object... formatArgs) {
        Resources res;

        Configuration conf = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= 17)
            conf.setLocale(locale);
        else
            conf.locale = locale;
        res = new Resources(context.getAssets(), context.getResources().getDisplayMetrics(), conf);

        String str;
        if (formatArgs.length == 0)
            str = res.getString(id);
        else
            str = res.getString(id, formatArgs);

        new Resources(context.getAssets(), context.getResources().getDisplayMetrics(), context.getResources().getConfiguration()); // restore side effect

        return str;
    }

    public static String getString(Context context, Locale locale, int id, Object... formatArgs) {
        return getStringNewRes(context, locale, id, formatArgs);
    }

    public static String getQuantityString(Context context, int id, int n, Object... formatArgs) {
        Resources res = context.getResources();
        String str = res.getQuantityString(id, n, formatArgs);
        return str;
    }

    // night/am/mid/pm hour string
    public static String getHour4String(Context context, int hour) {
        Resources res = context.getResources();
        Configuration conf = res.getConfiguration();
        Locale locale = conf.locale;
        return getHour4String(context, locale, hour);
    }

    // night/am/mid/pm hour string
    public static String getHour4String(Context context, Locale locale, int hour) {
        switch (hour) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
                return getString(context, locale, R.string.day_4_night);
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
                return getString(context, locale, R.string.day_4_am);
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
                return getString(context, locale, R.string.day_4_mid);
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
                return getString(context, locale, R.string.day_4_pm);
        }
        throw new RuntimeException("bad hour");
    }

    // am/pm hour string
    public static String getHour2String(Context context, int hour) {
        Resources res = context.getResources();
        Configuration conf = res.getConfiguration();
        Locale locale = conf.locale;
        return getHour2String(context, locale, hour);
    }

    // am/pm hour string
    public static String getHour2String(Context context, Locale locale, int hour) {
        switch (hour) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
            case 11:
                return getString(context, locale, R.string.day_am);
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
                return getString(context, locale, R.string.day_pm);
        }
        throw new RuntimeException("bad hour");
    }

    public static Set<String> getStringSet(SharedPreferences shared, String name, Set<String> def) {
        if (Build.VERSION.SDK_INT < 11)
            return def; // ignore this app no longer uses StringSets
        else
            return shared.getStringSet(name, def);
    }

    // create list for hour reminders. 'time' list for reminder (hronological order)
    public static TreeSet<Long> generateReminders(List<ReminderSet> reminders) {
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
    public static TreeSet<Long> generateAlarms(List<Alarm> alarms) {
        TreeSet<Long> aa = new TreeSet<>();
        for (Alarm a : alarms) {
            if (a.enabled)
                aa.add(a.getTime());
        }
        return aa;
    }

    public class ItemsStorage {
        public AlarmManager am = new AlarmManager(HourlyApplication.this);
        public List<Alarm> alarms;
        public List<ReminderSet> reminders;

        public ItemsStorage() {
            load();
        }

        public void load() {
            alarms = loadAlarms();
            reminders = loadReminders();
        }

        public List<Alarm> loadAlarms() {
            ArrayList<Alarm> alarms = new ArrayList<>();

            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(HourlyApplication.this);
            int c = shared.getInt(PREFERENCE_ALARMS_PREFIX + "count", -1);
            if (c == -1) // <=1.4.4
                c = shared.getInt("Alarm_" + "Count", -1);
            if (c == -1) { // default alarms list
                Set<Long> ids = new TreeSet<>();

                Alarm a;
                a = new Alarm(HourlyApplication.this);
                a.setTime(9, 0);
                a.weekdaysCheck = true;
                a.speech = true;
                a.beep = true;
                a.ringtone = true;
                a.setWeekDaysValues(Week.WEEKDAY);
                alarms.add(a);
                while (ids.contains(a.id)) {
                    a.id++;
                }
                ids.add(a.id);

                a = new Alarm(HourlyApplication.this);
                a.setTime(10, 0);
                a.weekdaysCheck = true;
                a.speech = true;
                a.beep = true;
                a.ringtone = true;
                a.setWeekDaysValues(Week.WEEKEND);
                alarms.add(a);
                while (ids.contains(a.id)) {
                    a.id++;
                }
                ids.add(a.id);

                a = new Alarm(HourlyApplication.this);
                a.setTime(10, 30);
                a.weekdaysCheck = false;
                a.speech = true;
                a.beep = true;
                a.ringtone = true;
                alarms.add(a);
                while (ids.contains(a.id)) {
                    a.id++;
                }
                ids.add(a.id);
            }

            Set<Long> ids = new TreeSet<>();

            for (int i = 0; i < c; i++) {
                try {
                    String json = shared.getString(PREFERENCE_ALARMS_PREFIX + i, "");
                    if (json.isEmpty()) { // <=1.4.4
                        JSONObject o = new JSONObject();
                        String prefix = "Alarm_" + i + "_";
                        o.put("id", shared.getLong(prefix + "Id", System.currentTimeMillis()));
                        o.put("time", shared.getLong(prefix + "Time", 0));
                        o.put("enable", shared.getBoolean(prefix + "Enable", false));
                        o.put("weekdays", shared.getBoolean(prefix + "WeekDays", false));
                        o.put("weekdays_values", new JSONArray(getStringSet(shared, prefix + "WeekDays_Values", null)));
                        o.put("ringtone", shared.getBoolean(prefix + "Ringtone", false));
                        o.put("ringtone_value", shared.getString(prefix + "Ringtone_Value", ""));
                        o.put("beep", shared.getBoolean(prefix + "Beep", false));
                        o.put("speech", shared.getBoolean(prefix + "Speech", false));
                        json = o.toString();
                    }
                    Alarm a = new Alarm(HourlyApplication.this, json);

                    while (ids.contains(a.id)) {
                        a.id++;
                    }
                    ids.add(a.id);

                    alarms.add(a);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            return alarms;
        }

        public void saveAlarms(SharedPreferences.Editor edit, List<Alarm> alarms) {
            edit.putInt(PREFERENCE_ALARMS_PREFIX + "count", alarms.size());

            Set<Long> ids = new TreeSet<>();

            for (int i = 0; i < alarms.size(); i++) {
                Alarm a = alarms.get(i);

                while (ids.contains(a.id)) {
                    a.id++;
                }
                ids.add(a.id);

                edit.putString(PREFERENCE_ALARMS_PREFIX + i, a.save().toString());
            }
        }

        public void saveReminders(SharedPreferences.Editor edit, List<ReminderSet> reminders) {
            edit.putInt(PREFERENCE_REMINDERS_PREFIX + "count", reminders.size());

            Set<Long> ids = new TreeSet<>();

            for (int i = 0; i < reminders.size(); i++) {
                ReminderSet a = reminders.get(i);

                while (ids.contains(a.id)) {
                    a.id++;
                }
                ids.add(a.id);

                edit.putString(PREFERENCE_REMINDERS_PREFIX + i, a.save().toString());
            }
        }

        public void saveAlarms() {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(HourlyApplication.this);
            SharedPreferences.Editor edit = shared.edit();
            saveAlarms(edit, alarms);
            edit.commit();
            registerNext(HourlyApplication.this);
        }

        public void saveReminders() {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(HourlyApplication.this);
            SharedPreferences.Editor edit = shared.edit();
            saveReminders(edit, reminders);
            edit.commit();
            registerNext(HourlyApplication.this);
        }

        public List<ReminderSet> loadReminders() {
            ArrayList<ReminderSet> list = new ArrayList<>();

            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(HourlyApplication.this);

            int count = shared.getInt(PREFERENCE_REMINDERS_PREFIX + "count", -1);

            if (count == -1) { // <=1.5.9 or new installed app
                boolean enabled = shared.getBoolean(PREFERENCE_ENABLED, false);
                int repeat = Integer.parseInt(shared.getString(PREFERENCE_REPEAT, "60"));
                Set<String> hours = getStringSet(shared, PREFERENCE_HOURS, ReminderSet.DEF_HOURS);
                Set<String> days = getStringSet(shared, HourlyApplication.PREFERENCE_DAYS, ReminderSet.getWeekDaysProperty(ReminderSet.DEF_DAYS));

                boolean c = !shared.getString(HourlyApplication.PREFERENCE_CUSTOM_SOUND, HourlyApplication.PREFERENCE_CUSTOM_SOUND_OFF).equals(HourlyApplication.PREFERENCE_CUSTOM_SOUND_OFF);
                boolean s = shared.getBoolean(HourlyApplication.PREFERENCE_SPEAK, true);
                boolean b = shared.getBoolean(HourlyApplication.PREFERENCE_BEEP, true);

                ReminderSet rs = new ReminderSet(HourlyApplication.this, hours, repeat);
                rs.enabled = enabled;
                rs.speech = s;
                rs.beep = b;
                rs.ringtone = c;
                rs.weekdaysCheck = true;
                rs.setWeekDaysProperty(days);

                String custom = shared.getString(HourlyApplication.PREFERENCE_CUSTOM_SOUND, "");
                if (custom.equals("ringtone")) {
                    String uri = shared.getString(HourlyApplication.PREFERENCE_RINGTONE, null);
                    if (uri == null || uri.isEmpty()) {
                        rs.ringtoneValue = ReminderSet.DEFAULT_NOTIFICATION;
                    } else {
                        Uri u;
                        if (uri.startsWith(ContentResolver.SCHEME_CONTENT)) {
                            u = Uri.parse(uri);
                        } else if (uri.startsWith(ContentResolver.SCHEME_FILE)) {
                            u = Uri.parse(uri);
                        } else {
                            u = Uri.fromFile(new File(uri));
                        }
                        rs.ringtoneValue = u;
                    }
                } else if (custom.equals("sound")) {
                    String uri = shared.getString(HourlyApplication.PREFERENCE_SOUND, null);
                    if (uri == null || uri.isEmpty()) {
                        rs.ringtoneValue = ReminderSet.DEFAULT_NOTIFICATION;
                    } else {
                        Uri u;
                        if (uri.startsWith(ContentResolver.SCHEME_CONTENT)) {
                            u = Uri.parse(uri);
                        } else if (uri.startsWith(ContentResolver.SCHEME_FILE)) {
                            u = Uri.parse(uri);
                        } else {
                            u = Uri.fromFile(new File(uri));
                        }
                        rs.ringtoneValue = u;
                    }
                }
                list.add(rs);
            } else {
                Set<Long> ids = new TreeSet<>();

                for (int i = 0; i < count; i++) {
                    String json = shared.getString(PREFERENCE_REMINDERS_PREFIX + i, "");
                    ReminderSet a = new ReminderSet(HourlyApplication.this, json);

                    while (ids.contains(a.id)) {
                        a.id++;
                    }
                    ids.add(a.id);

                    list.add(a);
                }
            }

            return list;
        }

        public void save() {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(HourlyApplication.this);
            SharedPreferences.Editor edit = shared.edit();
            saveAlarms(edit, alarms);
            saveReminders(edit, reminders);
            edit.commit();
        }

        // register alarm event for next one.
        //
        // scan all alarms and hourly reminders and register net one
        //
        public boolean registerNextAlarm() {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(HourlyApplication.this);

            TreeSet<Long> all = new TreeSet<>();
            TreeSet<Long> reminders;
            TreeSet<Long> alarms;

            Calendar cur = Calendar.getInstance();

            // check hourly reminders
            reminders = generateReminders(this.reminders);
            all.addAll(reminders);

            // check alarms
            alarms = generateAlarms(this.alarms);
            all.addAll(alarms);

            Intent alarmIntent = new Intent(HourlyApplication.this, AlarmService.class).setAction(AlarmService.ALARM);
            Intent reminderIntent = new Intent(HourlyApplication.this, AlarmService.class).setAction(AlarmService.REMINDER);

            if (all.isEmpty()) {
                OptimizationPreferenceCompat.setKillCheck(HourlyApplication.this, 0, HourlyApplication.PREFERENCE_NEXT);
                updateNotificationUpcomingAlarm(0);
                return false;
            } else {
                long time = all.first();
                OptimizationPreferenceCompat.setKillCheck(HourlyApplication.this, time, HourlyApplication.PREFERENCE_NEXT);
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
                    a = am.setAlarm(time, reminderIntent, new Intent(HourlyApplication.this, MainActivity.class).setAction(MainActivity.SHOW_REMINDERS_PAGE).putExtra(ALARMINFO, true));
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

                AlarmManager.Alarm a = am.setAlarm(time, alarmIntent, new Intent(HourlyApplication.this, MainActivity.class).setAction(MainActivity.SHOW_ALARMS_PAGE));
                huaweiLock(time, a); // exact on time lock enabled always for alarms
            }

            return true;
        }

        void huaweiLock(long time, AlarmManager.Alarm a) { // support for huawei trash phones
            if (!OptimizationPreferenceCompat.isHuawei(HourlyApplication.this))
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
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(HourlyApplication.this);

            Intent upcomingIntent = new Intent(HourlyApplication.this, AlarmService.class).setAction(AlarmService.NOTIFICATION).putExtra("time", time);
            if (time == 0) {
                am.cancel(upcomingIntent);
                showNotificationUpcoming(0);
            } else {
                Calendar cur = Calendar.getInstance();

                int sec = upcomingSec(time);
                Calendar cal = upcomingTime(time, sec);

                if (cur.after(cal)) { // we already 15 before alarm, show notification_upcoming
                    am.cancel(upcomingIntent);
                    showNotificationUpcoming(time);
                } else {
                    showNotificationUpcoming(0);
                    long time15 = cal.getTimeInMillis(); // time to wait before show notification_upcoming
                    if (shared.getBoolean(HourlyApplication.PREFERENCE_ALARM, true)) {
                        Intent showIntent = new Intent(HourlyApplication.this, MainActivity.class);
                        if (isAlarm(time))
                            showIntent.setAction(MainActivity.SHOW_ALARMS_PAGE);
                        else
                            showIntent.setAction(MainActivity.SHOW_REMINDERS_PAGE).putExtra(ALARMINFO, true);
                        am.setAlarm(time15, upcomingIntent, time, showIntent);
                    } else {
                        if (Build.VERSION.SDK_INT >= 23 && sec < 15 * 60) { // 15 min interval
                            am.checkPost(time15, upcomingIntent); // post intent, do not create alarm
                        } else {
                            am.setExact(time15, upcomingIntent);
                        }
                    }
                }
            }
        }

        // show upcoming alarm notification
        //
        // time - 0 cancel notification
        // time - upcoming alarm time, show text.
        @SuppressLint("RestrictedApi")
        public void showNotificationUpcoming(long time) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(HourlyApplication.this);
            if (!prefs.getBoolean(HourlyApplication.PREFERENCE_NOTIFICATIONS, true))
                return;

            NotificationManagerCompat nm = NotificationManagerCompat.from(HourlyApplication.this);

            if (time == 0) {
                nm.cancel(HourlyApplication.NOTIFICATION_UPCOMING_ICON);
            } else {
                PendingIntent button = PendingIntent.getService(HourlyApplication.this, 0,
                        new Intent(HourlyApplication.this, AlarmService.class).setAction(AlarmService.CANCEL).putExtra("time", time),
                        PendingIntent.FLAG_UPDATE_CURRENT);

                String action = MainActivity.SHOW_REMINDERS_PAGE;

                String subject = getString(R.string.UpcomingChime);
                if (isAlarm(time)) {
                    subject = getString(R.string.UpcomingAlarm);
                    action = MainActivity.SHOW_ALARMS_PAGE;
                }

                PendingIntent main = PendingIntent.getActivity(HourlyApplication.this, 0,
                        new Intent(HourlyApplication.this, MainActivity.class).setAction(action).putExtra("time", time),
                        PendingIntent.FLAG_UPDATE_CURRENT);

                String text = Alarm.format2412ap(HourlyApplication.this, time);
                for (Alarm a : alarms) {
                    if (a.getTime() == time) {
                        if (a.isSnoozed()) {
                            text += " (" + getString(R.string.snoozed) + ": " + a.format2412ap() + ")";
                        }
                    }
                }

                RemoteNotificationCompat.Builder builder = new RemoteNotificationCompat.Builder(HourlyApplication.this, R.layout.notification_alarm);

                builder.setOnClickPendingIntent(R.id.notification_button, button);
                builder.setTextViewText(R.id.notification_button, getString(R.string.Cancel));

                builder.setTheme(HourlyApplication.getTheme(HourlyApplication.this, R.style.AppThemeLight, R.style.AppThemeDark))
                        .setChannel(channelUpcoming)
                        .setImageViewTint(R.id.icon_circle, R.attr.colorButtonNormal)
                        .setMainIntent(main)
                        .setTitle(subject)
                        .setText(text)
                        .setOngoing(true)
                        .setSmallIcon(R.drawable.ic_notifications_black_24dp);

                nm.notify(HourlyApplication.NOTIFICATION_UPCOMING_ICON, builder.build());
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

        int upcomingSec(long time) {
            SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(HourlyApplication.this);
            int repeat = getRepeat(time) * 60; // make seconds
            int sec;
            if (Build.VERSION.SDK_INT >= 23 && !shared.getBoolean(HourlyApplication.PREFERENCE_ALARM, true)) { // 15 min interval
                sec = repeat / 4; // 60 / 4 = 15min
            } else {
                sec = repeat / 12; // 60 / 12 = 5min
            }
            return sec;
        }

        Calendar upcomingTime(long time) {
            int sec = upcomingSec(time);
            return upcomingTime(time, sec);
        }

        Calendar upcomingTime(long time, int sec) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(time);
            cal.add(Calendar.SECOND, -sec);
            return cal;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        channelStatus = new NotificationChannelCompat(this, "status", "Status", NotificationManagerCompat.IMPORTANCE_LOW);
        channelAlarms = new NotificationChannelCompat(this, "alarms", "Alarms", NotificationManagerCompat.IMPORTANCE_LOW);
        channelErrors = new NotificationChannelCompat(this, "errors", "System Errors", NotificationManagerCompat.IMPORTANCE_MAX);
        channelUpcoming = new NotificationChannelCompat(this, "upcoming", "Upcoming", NotificationManagerCompat.IMPORTANCE_LOW);

        switch (getVersion(PREFERENCE_VERSION, R.xml.pref_settings)) {
            case -1:
                SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor edit = shared.edit();
                edit.putInt(PREFERENCE_VERSION, 2);
                SharedPreferencesCompat.EditorCompat.getInstance().apply(edit);
                break;
            case 0:
            case 1:
                version1to2();
                break;
        }

        OptimizationPreferenceCompat.ICON = true;

        items = new ItemsStorage();
        sound = new Sound(this);

        FireAlarmService.startIfActive(this);
    }

    void version1to2() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = shared.edit();
        int old = Integer.valueOf(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_AFTER, "0")) * 60;
        edit.putString(HourlyApplication.PREFERENCE_SNOOZE_AFTER, Integer.toString(old));
        SharedPreferencesCompat.EditorCompat.getInstance().apply(edit);
    }
}
