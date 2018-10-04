package com.github.axet.hourlyreminder.services;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
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
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.github.axet.androidlibrary.widgets.RemoteNotificationCompat;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.activities.AlarmActivity;
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

public class FireAlarmService extends Service implements SensorEventListener {
    public static final String TAG = FireAlarmService.class.getSimpleName();

    public static final String FIRE_ALARM = FireAlarmService.class.getCanonicalName() + ".FIRE_ALARM";

    // notification click -> show activity broadcast
    public static final String SHOW_ACTIVITY = FireAlarmService.class.getCanonicalName() + ".SHOW_ACTIVITY";

    public static final int STATE_INIT = 0;
    public static final int STATE_UP = 1;
    public static final int STATE_SIDE = 2;
    public static final int STATE_DOWN = 3;

    FireAlarmReceiver receiver;
    Sound sound;
    Handler handle = new Handler();
    Runnable alive;
    boolean alarmActivity = false; // if service crashed, activity will be closed. ok to have var.
    Sound.Silenced silenced = Sound.Silenced.NONE;

    int state = STATE_INIT;
    float mGZ;
    float maxy;
    float maxz;
    int mGZcount;
    SensorManager sm;

    PhoneStateChangeListener pscl;

    class PhoneStateChangeListener extends PhoneStateListener {
        public boolean wasRinging;

        @Override
        public void onCallStateChanged(int s, String incomingNumber) {
            switch (s) {
                // incoming call ringing
                case TelephonyManager.CALL_STATE_RINGING:
                    wasRinging = true;
                    break;
                // answered
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    wasRinging = true;
                    // stop current alarm
                    if (sound != null) {
                        sound.playerClose();
                    }
                    break;
                // switch to idle state: no call, no ringing
                case TelephonyManager.CALL_STATE_IDLE:
                    wasRinging = false;
                    break;
            }
        }
    }

    public class FireAlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            Log.d(FireAlarmReceiver.class.getSimpleName(), "FireAlarmReceiver " + a);
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
                FireAlarm alarm = new FireAlarm(intent.getStringExtra("state"));
                showAlarmActivity(alarm, silenced);
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

    public static void activateAlarm(Context context, FireAlarm a) {
        context.startService(new Intent(context, FireAlarmService.class)
                .setAction(FIRE_ALARM)
                .putExtra("state", a.save().toString()));
    }

    public static void startIfActive(Context context) {
        context.stopService(new Intent(context, FireAlarmService.class));
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (!shared.getString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, "").isEmpty()) {
            context.startService(new Intent(context, FireAlarmService.class));
        }
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
            AlarmService.snooze(context, alarm);
            dismissActiveAlarm(context);
        }
    }

    public FireAlarmService() {
    }

    @Override
    public void onCreate() {
        setTheme(HourlyApplication.getTheme(this, R.style.AppThemeLight, R.style.AppThemeDark));
        super.onCreate();
        Log.d(TAG, "onCreate");
        sound = new Sound(this);
    }

    public FireAlarm getAlarm(Intent intent) {
        FireAlarm a;

        String json = intent.getStringExtra("state");
        if (json == null || json.isEmpty())
            return null;

        a = new FireAlarm(json);

        return a;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        receiver = new FireAlarmReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(SHOW_ACTIVITY);
        registerReceiver(receiver, filter);

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
            alarm = getAlarm(intent);

            String json = shared.getString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, "");

            if (alarm == null) { // started without alarm, read stored alarm
                if (json.isEmpty()) // service intent started, after alarm been cleared by dismissActiveAlarm()
                    return START_NOT_STICKY;
                alarm = new FireAlarm(json);
            } else { // alarm loaded, does it interference with current running alarm?
                if (!json.isEmpty()) { // yep, we are already firing the alarm, show missed
                    FireAlarm a = new FireAlarm(json);
                    if (!a.contains(alarm)) // it is same alaram currently playing?
                        AlarmService.showNotificationMissedConf(this, a.settime); // dismiss after conflict, not time based; snooze = off
                }
            }

            SharedPreferences.Editor editor = shared.edit();
            editor.putString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, alarm.save().toString());
            editor.commit();
        }

        Log.d(TAG, "time=" + Alarm.format24(alarm.settime));

        long fire = System.currentTimeMillis();
        if (!alive(alarm, fire, 1000 * 60)) {
            stopSelf();
            AlarmService.showNotificationMissed(this, alarm.settime, alarm.isSnoozed(fire));
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

        showNotificationAlarm(alarm);

        // do we have silence alarm?
        silenced = sound.playAlarm(alarm, 1000, alive);
        sound.silencedToast(silenced, alarm.settime);

        showAlarmActivity(alarm, silenced);

        return super.onStartCommand(intent, flags, startId);
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

    boolean alive(final FireAlarm alarm, final long fire, long delay) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        if (!AlarmService.dismiss(this, alarm.settime, alarm.isSnoozed(fire))) { // do not check snooze on first run
            handle.removeCallbacks(alive);
            alive = new Runnable() {
                @Override
                public void run() {
                    if (alive != this) // can be called from handler or standalone, one time trigger
                        return;
                    alive = null;
                    if (snooze(fire)) {
                        AlarmService.snooze(FireAlarmService.this, alarm);
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
                        AlarmService.showNotificationMissed(FireAlarmService.this, alarm.settime, alarm.isSnoozed(fire));
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
        AlarmActivity.showAlarmActivity(this, alarm, silenced);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public class Binder extends android.os.Binder {
        public FireAlarmService getService() {
            return FireAlarmService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(FireAlarmService.class.getSimpleName(), "onDestory");

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        if (sound != null) {
            sound.close();
            sound = null;
        }

        showNotificationAlarm(null);

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
            AlarmActivity.closeAlarmActivity(this);
        }

        if (alive != null) {
            handle.removeCallbacks(alive);
            alive = null;
        }

        if (sm != null)
            sm.unregisterListener(this);
    }

    // alarm dismiss button
    @SuppressLint("RestrictedApi")
    public void showNotificationAlarm(FireAlarm alarm) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);

        if (alarm == null) {
            nm.cancel(HourlyApplication.NOTIFICATION_ALARM_ICON);
        } else {
            PendingIntent button = PendingIntent.getService(this, 0,
                    new Intent(this, AlarmService.class).setAction(AlarmService.DISMISS).putExtra("alarm", alarm.save().toString()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

            PendingIntent main = PendingIntent.getBroadcast(this, 0,
                    new Intent(SHOW_ACTIVITY).putExtra("state", alarm.save().toString()),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            String text = Alarm.format2412(this, alarm.settime);

            RemoteNotificationCompat.Builder builder = new RemoteNotificationCompat.Builder(this, HourlyApplication.getTheme(this, R.layout.notification_alarm_light, R.layout.notification_alarm_dark));

            builder.view.setOnClickPendingIntent(R.id.notification_button, button);

            builder.setTheme(HourlyApplication.getTheme(this, R.style.AppThemeLight, R.style.AppThemeDark))
                    .setImageViewTint(R.id.icon_circle, R.attr.colorButtonNormal)
                    .setTitle(getString(R.string.Alarm))
                    .setText(text)
                    .setMainIntent(main)
                    .setChannel(((HourlyApplication) getApplication()).channelAlarms)
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_notifications_black_24dp);

            nm.notify(HourlyApplication.NOTIFICATION_ALARM_ICON, builder.build());
        }
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

