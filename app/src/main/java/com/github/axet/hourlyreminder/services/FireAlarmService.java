package com.github.axet.hourlyreminder.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;

import com.github.axet.androidlibrary.app.NotificationManagerCompat;
import com.github.axet.androidlibrary.services.PersistentService;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.RemoteNotificationCompat;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.activities.AlarmActivity;
import com.github.axet.hourlyreminder.activities.MainActivity;
import com.github.axet.hourlyreminder.alarms.Alarm;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TreeSet;

public class FireAlarmService extends PersistentService implements SensorEventListener {
    public static final String TAG = FireAlarmService.class.getSimpleName();

    public static final String FIRE_ALARM = FireAlarmService.class.getCanonicalName() + ".FIRE_ALARM";

    public static final String DISMISS = HourlyApplication.class.getCanonicalName() + ".DISMISS"; // dismiss current alarm action
    public static final String SNOOZE = AlarmService.class.getCanonicalName() + ".SNOOZE"; // snooze

    public static final String SHOW_ACTIVITY = FireAlarmService.class.getCanonicalName() + ".SHOW_ACTIVITY"; // notification click
    public static final String RESUME_ACTIVITY = FireAlarmService.class.getCanonicalName() + ".RESUME_ACTIVITY"; // onResume MainActivity

    public static final int STATE_INIT = 0;
    public static final int STATE_UP = 1;
    public static final int STATE_SIDE = 2;
    public static final int STATE_DOWN = 3;

    public static final int ALARM_AUTO_OFF = 15; // if no auto snooze enabled wait 15 min
    public static final int ALARM_SNOOZE_AUTO_OFF = 45; // if auto snooze enabled or manually snoozed wait 45 min

    HourlyApplication.ItemsStorage items;
    FireAlarmReceiver receiver;
    Sound sound;
    Handler handle = new Handler();
    Runnable alive;
    boolean alarmActivity = false; // if service crashed, activity will be closed. ok to have var.
    Sound.Silenced silenced = Sound.Silenced.NONE;
    Notification notification;

    int state = STATE_INIT;
    int mGZcount;
    SensorManager sm;

    PhoneStateChangeListener pscl;

    OptimizationPreferenceCompat.NotificationIcon icon;

    public static void snooze(Context context, FireAlarmService.FireAlarm a) {
        Intent intent = new Intent(context, FireAlarmService.class).setAction(SNOOZE).putExtra("alarm", a.save().toString());
        start(context, intent);

        SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(context);
        Integer min = Integer.valueOf(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_DELAY, "10"));
        Toast.makeText(context, context.getString(R.string.snoozed_for) + " " + HourlyApplication.formatLeftExact(context, min * 60 * 1000), Toast.LENGTH_LONG).show();
    }

    // show notification about missed alarm
    @SuppressLint("RestrictedApi")
    public static void showNotificationMissed(Context context, long settime, boolean snoozed) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);

        if (settime == 0) {
            nm.cancel(HourlyApplication.NOTIFICATION_MISSED_ICON);
        } else {
            final SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(context);
            Integer sec = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_AFTER, "0")); // snooze auto seconds
            int auto = ALARM_AUTO_OFF;
            if (sec > 0 || snoozed)
                auto = ALARM_SNOOZE_AUTO_OFF;

            PendingIntent main = PendingIntent.getActivity(context, 0,
                    new Intent(context, MainActivity.class).setAction(MainActivity.SHOW_ALARMS_PAGE).putExtra("time", settime),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            String text = context.getString(R.string.AlarmMissedAfter, Alarm.format2412ap(context, settime), auto);

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

    public static boolean dismiss(Context context, long settime, boolean snoozed) { // do we have to dismiss (due timeout) alarm?
        Calendar cur = Calendar.getInstance();
        return dismiss(context, cur, settime, snoozed);
    }

    public static boolean dismiss(Context context, Calendar cur, long settime, boolean snoozed) { // do we have to dismiss (due timeout) alarm?
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

    public static void activateAlarm(Context context, FireAlarm a) {
        start(context, new Intent(context, FireAlarmService.class).setAction(FIRE_ALARM).putExtra("alarm", a.save().toString()));
    }

    public static void onResume(Context context) {
        context.sendBroadcast(new Intent(RESUME_ACTIVITY));
    }

    public static void startIfActive(Context context) {
        context.stopService(new Intent(context, FireAlarmService.class));
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (!shared.getString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, "").isEmpty())
            start(context, new Intent(context, FireAlarmService.class));
    }

    public static void dismissActiveAlarm(Context context) {
        context.stopService(new Intent(context, FireAlarmService.class));
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        edit.remove(HourlyApplication.PREFERENCE_ACTIVE_ALARM);
        edit.commit();
    }

    public static void snoozeActiveAlarm(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String json = shared.getString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, "");
        if (!json.isEmpty()) {
            FireAlarm alarm = new FireAlarm(json);
            FireAlarmService.snooze(context, alarm);
            dismissActiveAlarm(context);
        }
    }

    public static FireAlarm getAlarm(Intent intent) {
        String json = intent.getStringExtra("alarm");
        if (json == null || json.isEmpty())
            return null;
        return new FireAlarm(json);
    }

    class PhoneStateChangeListener extends PhoneStateListener {
        public boolean wasRinging;

        @Override
        public void onCallStateChanged(int s, String incomingNumber) {
            switch (s) {
                case TelephonyManager.CALL_STATE_RINGING: // incoming call ringing
                    wasRinging = true;
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK: // answered
                    wasRinging = true;
                    if (sound != null)
                        sound.playerClose(); // stop current alarm
                    break;
                case TelephonyManager.CALL_STATE_IDLE: // switch to idle state: no call, no ringing
                    wasRinging = false;
                    break;
            }
        }
    }

    public class FireAlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            Log.d(TAG, "FireAlarmReceiver " + a);
            if (a.equals(Intent.ACTION_SCREEN_ON)) {
                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
                String json = shared.getString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, "");
                if (!json.isEmpty()) {
                    FireAlarm alarm = new FireAlarm(json);
                    showAlarmActivity(alarm, silenced);
                }
            }
            if (a.equals(Intent.ACTION_SCREEN_OFF)) {
                // do nothing. do not annoy user. he will see alarm screen on next screen on event.
            }
            if (a.equals(SHOW_ACTIVITY)) {
                FireAlarm alarm = new FireAlarm(intent.getStringExtra("alarm"));
                showAlarmActivity(alarm, silenced);
            }
            if (a.equals(RESUME_ACTIVITY)) {
                final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
                String json = shared.getString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, "");
                if (!json.isEmpty()) {
                    FireAlarm alarm = new FireAlarm(json);
                    showAlarmActivity(alarm, silenced);
                }
            }
        }
    }

    public static class FireAlarm {
        public long settime;
        public Sound.Playlist list;
        public List<Long> ids;

        public FireAlarm() {
        }

        public FireAlarm(String json) {
            load(json);
        }

        public FireAlarm(Alarm a) {
            settime = a.getSetTime();
            list = new Sound.Playlist(a);
            ids = new ArrayList<>();
            ids.add(a.id);
        }

        public void merge(Alarm a) {
            list.merge(a);
            // snoozed alarms does not cross, getSetTime always the same/correct
            // for all a.getTime() == time
            settime = a.getSetTime();
            ids.add(a.id);
        }

        public void merge(ReminderSet rs) {
            list.withAlarm(rs);
        }

        public void load(String json) {
            try {
                JSONObject o = new JSONObject(json);
                list = new Sound.Playlist(o);
                ids = new ArrayList<>();
                JSONArray a = o.getJSONArray("ids");
                for (int i = 0; i < a.length(); i++)
                    ids.add(a.getLong(i));
                settime = o.getLong("settime");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public JSONObject save() {
            JSONObject o = list.save();
            try {
                o.put("ids", new JSONArray(ids));
                o.put("settime", settime);
                return o;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean isSnoozed(long fire) { // not checking timezone shifts
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(settime);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int min = cal.get(Calendar.MINUTE);

            Calendar cal2 = Calendar.getInstance();
            cal2.setTimeInMillis(fire);
            int hour2 = cal2.get(Calendar.HOUR_OF_DAY);
            int min2 = cal2.get(Calendar.MINUTE);
            return hour != hour2 || min != min2;
        }

        public boolean contains(FireAlarm a) {
            for (Long id : ids) {
                if (a.ids.contains(id))
                    return true;
            }
            return false;
        }
    }

    public FireAlarmService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sound = new Sound(this);

        items = HourlyApplication.from(this).items;

        receiver = new FireAlarmReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(SHOW_ACTIVITY);
        filter.addAction(RESUME_ACTIVITY);
        registerReceiver(receiver, filter);

        icon = new OptimizationPreferenceCompat.NotificationIcon(this, HourlyApplication.NOTIFICATION_ALARM_ICON) {
            @Override
            public Notification build(Intent intent) {
                String json = intent.getStringExtra("alarm");
                String text = intent.getStringExtra("text");

                PendingIntent button = PendingIntent.getService(context, 0,
                        new Intent(context, FireAlarmService.class).setAction(FireAlarmService.DISMISS).putExtra("alarm", json),
                        PendingIntent.FLAG_UPDATE_CURRENT);

                PendingIntent main = PendingIntent.getBroadcast(context, 0,
                        new Intent(SHOW_ACTIVITY).putExtra("alarm", json),
                        PendingIntent.FLAG_UPDATE_CURRENT);

                RemoteNotificationCompat.Builder builder = new RemoteNotificationCompat.Builder(context, R.layout.notification_alarm);

                builder.setOnClickPendingIntent(R.id.notification_button, button);

                builder.setTheme(HourlyApplication.getTheme(context, R.style.AppThemeLight, R.style.AppThemeDark))
                        .setChannel(HourlyApplication.from(context).channelAlarms)
                        .setImageViewTint(R.id.icon_circle, builder.getThemeColor(R.attr.colorButtonNormal))
                        .setTitle(getString(R.string.Alarm))
                        .setText(text)
                        .setWhen(notification)
                        .setMainIntent(main)
                        .setAdaptiveIcon(R.drawable.ic_launcher_foreground)
                        .setSmallIcon(R.drawable.ic_launcher_notification)
                        .setOngoing(true);

                return builder.build();
            }
        };
        icon.create();
    }

    @Override
    public void onCreateOptimization() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        if (shared.getBoolean(HourlyApplication.PREFERENCE_CALLSILENCE, false)) {
            pscl = new PhoneStateChangeListener();
            TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(pscl, PhoneStateListener.LISTEN_CALL_STATE);
        }

        FireAlarm alarm;

        if (intent == null) {
            Log.d(TAG, "onStartCommand restart");
            String json = shared.getString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, "");
            if (json.isEmpty())
                return START_NOT_STICKY;
            alarm = new FireAlarm(json);
        } else {
            String a = intent.getAction();
            if (a != null && a.equals(DISMISS)) {
                FireAlarmService.dismissActiveAlarm(this);
                return START_NOT_STICKY;
            } else if (a != null && a.equals(SNOOZE)) {
                FireAlarmService.FireAlarm f = new FireAlarmService.FireAlarm(intent.getStringExtra("alarm"));
                snooze(f.ids);
                return START_NOT_STICKY;
            } else {
                alarm = getAlarm(intent); // FIRE_ALARM

                String json = shared.getString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, "");

                if (alarm == null) { // started without alarm, read stored alarm
                    if (json.isEmpty()) // service intent started, after alarm been cleared by dismissActiveAlarm()
                        return START_NOT_STICKY;
                    alarm = new FireAlarm(json);
                } else { // alarm loaded, does it interference with current running alarm?
                    if (!json.isEmpty()) { // yep, we are already firing the alarm, show missed
                        FireAlarm f = new FireAlarm(json);
                        if (!f.contains(alarm)) // it is same alaram currently playing?
                            AlarmService.showNotificationMissedConf(this, f.settime); // dismiss after conflict, not time based; snooze = off
                    }
                }

                SharedPreferences.Editor editor = shared.edit();
                editor.putString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, alarm.save().toString());
                editor.commit();
            }
        }

        Log.d(TAG, "time=" + Alarm.format24(alarm.settime));

        long fire = System.currentTimeMillis();
        if (!alive(alarm, fire, 1000 * 60)) {
            stopSelf();
            showNotificationMissed(this, alarm.settime, alarm.isSnoozed(fire));
            SharedPreferences.Editor editor = shared.edit();
            editor.remove(HourlyApplication.PREFERENCE_ACTIVE_ALARM);
            editor.commit();
            return START_NOT_STICKY;
        }

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm != null) {
            Sensor a = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (a != null)
                sm.registerListener(this, a, SensorManager.SENSOR_DELAY_GAME);
        }

        icon.updateIcon(new Intent().putExtra("alarm", alarm.save().toString()).putExtra("text", Alarm.format2412(this, alarm.settime)));

        silenced = sound.playAlarm(alarm, 1000, alive); // did we silence an alarm?
        sound.silencedToast(silenced, alarm.settime);

        showAlarmActivity(alarm, silenced);

        return START_STICKY;
    }

    public boolean snooze(long fire) { // false - do not snooze, true - snooze
        Calendar cur = Calendar.getInstance();

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        Integer sec = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_AFTER, "0"));

        if (sec == 0)
            return false;

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fire); // we act since fire time
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        cal.add(Calendar.SECOND, sec);

        return cur.after(cal);
    }

    public void snooze(List<Long> ids) {
        Context context = this;

        // create old list, we need to check conflicts with old alarms only, not shifted
        TreeSet<Long> old = new TreeSet<>();
        for (Alarm a : items.alarms) {
            if (a.enabled)
                old.add(a.getTime());
        }

        for (Alarm a : items.alarms) {
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

        items.save();
        items.registerNextAlarm();
    }

    public boolean alive(final FireAlarm alarm, final long fire, long delay) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        if (!dismiss(this, alarm.settime, alarm.isSnoozed(fire))) { // do not check snooze on first run
            handle.removeCallbacks(alive);
            alive = new Runnable() {
                @Override
                public void run() {
                    if (alive != this) // can be called from handler or standalone, one time trigger
                        return;
                    alive = null;
                    if (snooze(fire)) {
                        FireAlarmService.snooze(FireAlarmService.this, alarm);
                        stopSelf();
                        return;
                    }
                    Integer sec = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_AFTER, "0"));
                    if (sec == 0 || sec > 60) // for 'off' or '1 min+' delays check every minute
                        sec = 60;
                    else
                        sec = sec - 1; // for 1-5 sec snooze keep it on time minus initial 1 sec first call delay
                    if (sec <= 0)
                        sec = 1;
                    if (!alive(alarm, fire, sec * 1000)) {
                        showNotificationMissed(FireAlarmService.this, alarm.settime, alarm.isSnoozed(fire));
                        stopSelf();
                        return;
                    }
                }
            };
            handle.postDelayed(alive, delay); // first run 60 secs (let it sound for bit, before immediate snooze if it is snooze time)
            return true;
        }

        return false;
    }

    public void showAlarmActivity(FireAlarm alarm, Sound.Silenced silenced) {
        alarmActivity = true;
        AlarmActivity.start(this, alarm, silenced);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (sound != null) {
            sound.close();
            sound = null;
        }

        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }

        if (pscl != null) {
            TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(pscl, PhoneStateListener.LISTEN_NONE);
            pscl = null;
        }

        if (alarmActivity) {
            alarmActivity = false;
            AlarmActivity.close(this);
        }

        if (alive != null) {
            handle.removeCallbacks(alive);
            alive = null;
        }

        if (sm != null)
            sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_ACCELEROMETER) {
            float gx = event.values[0]; // max value (9.8) - on side
            float gy = event.values[1]; // max value (9.8) - vertical position, min value (0.0) - horizontal position
            float gz = event.values[2]; // max value (9.8) - face up, min value - face down

            float ax = Math.abs(gx);
            float ay = Math.abs(gy);
            float az = Math.abs(gz);

            // face up - 0.0, 0.0, 9.8
            // side - 9.8, 0.0, 0.0
            // face down - 0.0, 0.0, -9.8
            // portrait - 0.0, 9.8, 0.0

            switch (state) {
                case STATE_INIT:
                    mGZcount = 0;
                    if (ax > az || ay > az) { // phone not on horizontal table
                        state = STATE_INIT;
                    } else {
                        if (ax < az * 0.5 && ay < az * 0.5 && gz > 0)
                            state = STATE_UP;
                    }
                    break;
                case STATE_UP:
                    if (ay > az * 0.5) {
                        state = STATE_INIT;
                    } else {
                        if (ay < ax * 0.5 && az < ax * 0.5)
                            state = STATE_SIDE;
                    }
                    break;
                case STATE_SIDE:
                    if (ay > (ax + az) * 0.5 || (az > ax && gz > 0)) {
                        state = STATE_INIT;
                    } else {
                        if (ax < az * 0.5 && ay < az * 0.5 && gz < 0)
                            state = STATE_DOWN;
                    }
                    break;
                case STATE_DOWN:
                    if (ax > az || ay > az) { // phone not on horizontal table
                        state = STATE_INIT;
                    } else {
                        mGZcount++;
                        if (mGZcount >= 10)
                            snoozeActiveAlarm(this);
                    }
                    break;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
