package com.github.axet.hourlyreminder.services;

import android.app.Notification;
import android.app.NotificationManager;
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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.activities.AlarmActivity;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.basics.Alarm;

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

    FireAlarmReceiver receiver = new FireAlarmReceiver();
    Sound sound;
    Handler handle = new Handler();
    Runnable alive;
    boolean alarmActivity = false; // if service crashed, activity willbe closed. ok to have var.
    Sound.Silenced silenced = Sound.Silenced.NONE;
    float mGZ;
    int mEventCountSinceGZChanged;
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
                FireAlarm alarm = new FireAlarm(json);
                showAlarmActivity(alarm, silenced);
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

        public void load(String json) {
            try {
                JSONObject o = new JSONObject(json);
                list = new Sound.Playlist(o);
                JSONArray a = o.getJSONArray("ids");
                ids = new ArrayList<>();
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

    public FireAlarmService() {
    }

    @Override
    public void onCreate() {
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
                alarm = new FireAlarm(json);
            } else { // alarm loaded, does it interference with current running alarm?
                if (!json.isEmpty()) { // yep, we already firering alarm, show missed
                    Alarm a = new Alarm(this, json);
                    AlarmService.showNotificationMissed(this, a.getTime());
                }
            }

            SharedPreferences.Editor editor = shared.edit();
            editor.putString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, alarm.save().toString());
            editor.commit();
        }

        Log.d(TAG, "time=" + Alarm.format24(alarm.settime));

        if (!alive(alarm, System.currentTimeMillis())) {
            stopSelf();
            AlarmService.showNotificationMissed(this, alarm.settime);
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
        silenced = sound.playAlarm(alarm);

        showAlarmActivity(alarm, silenced);

        return super.onStartCommand(intent, flags, startId);
    }

    public boolean snooze(long fire) { // false - do not snooze, true - snooze
        Calendar cur = Calendar.getInstance();

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        Integer m = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_AFTER, "0"));

        if (m == 0)
            return false;

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(fire); // we act since fire time
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        cal.add(Calendar.MINUTE, m);

        return cur.after(cal);
    }

    boolean alive(final FireAlarm alarm, final long fire) {
        if (!AlarmService.dismiss(this, alarm.settime)) { // do not check snooze on first run
            alive = new Runnable() {
                @Override
                public void run() {
                    if (snooze(fire)) {
                        AlarmService.snooze(FireAlarmService.this, alarm);
                        stopSelf();
                        return;
                    }
                    if (!alive(alarm, fire)) {
                        AlarmService.showNotificationMissed(FireAlarmService.this, alarm.settime);
                        stopSelf();
                        return;
                    }
                }
            };
            handle.postDelayed(alive, 1000 * 60);
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
        if (shared.getBoolean(HourlyApplication.PREFERENCE_VIBRATE, false)) {
            sound.vibrateStop();
        }

        if (sound != null) {
            sound.close();
            sound = null;
        }

        showNotificationAlarm(null);

        unregisterReceiver(receiver);

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
    public void showNotificationAlarm(FireAlarm alarm) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(HourlyApplication.PREFERENCE_NOTIFICATIONS, true))
            return;

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (alarm == null) {
            notificationManager.cancel(HourlyApplication.NOTIFICATION_ALARM_ICON);
        } else {
            PendingIntent button = PendingIntent.getService(this, 0,
                    new Intent(this, AlarmService.class).setAction(AlarmService.DISMISS).putExtra("alarm", alarm.save().toString()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

            PendingIntent main = PendingIntent.getBroadcast(this, 0,
                    new Intent(SHOW_ACTIVITY).putExtra("state", alarm.save().toString()),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            String text = Alarm.format2412(this, alarm.settime);

            RemoteViews view = new RemoteViews(getPackageName(), HourlyApplication.getTheme(getBaseContext(), R.layout.notification_alarm_light, R.layout.notification_alarm_dark));
            view.setOnClickPendingIntent(R.id.notification_base, main);
            view.setOnClickPendingIntent(R.id.notification_button, button);
            view.setTextViewText(R.id.notification_text, text);

            Notification.Builder builder = new Notification.Builder(this)
                    .setOngoing(true)
                    .setContentTitle(getString(R.string.Alarm))
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                    .setContent(view);

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(Notification.VISIBILITY_PUBLIC);

            notificationManager.notify(HourlyApplication.NOTIFICATION_ALARM_ICON, builder.build());
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_ACCELEROMETER) {
            float gz = event.values[2];
            if (mGZ == 0) {
                mGZ = gz;
            } else {
                if ((mGZ * gz) < 0) {
                    mEventCountSinceGZChanged++;
                    if (mEventCountSinceGZChanged >= 10) {
                        mGZ = gz;
                        mEventCountSinceGZChanged = 0;
                        if (gz > 0) {
                            Log.d(TAG, "now screen is facing up.");
                        } else if (gz < 0) {
                            Log.d(TAG, "now screen is facing down.");
                            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
                            String json = shared.getString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, "");
                            if (!json.isEmpty()) {
                                FireAlarm alarm = new FireAlarm(json);
                                AlarmService.snooze(this, alarm);
                                dismissActiveAlarm(this);
                            }
                        }
                    }
                } else {
                    if (mEventCountSinceGZChanged > 0) {
                        mGZ = gz;
                        mEventCountSinceGZChanged = 0;
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}

