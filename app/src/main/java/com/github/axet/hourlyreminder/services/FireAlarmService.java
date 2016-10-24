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
import android.view.View;
import android.widget.RemoteViews;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.activities.AlarmActivity;
import com.github.axet.hourlyreminder.activities.MainActivity;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.basics.Alarm;

import java.util.Calendar;
import java.util.List;
import java.util.TreeSet;

public class FireAlarmService extends Service implements SensorEventListener {
    public static final String TAG = FireAlarmService.class.getSimpleName();

    public static final String FIRE_ALARM = FireAlarmService.class.getCanonicalName() + ".FIRE_ALARM";

    // notification click -> show activity broadcast
    public static final String SHOW_ACTIVITY = FireAlarmService.class.getCanonicalName() + ".SHOW_ACTIVITY";

    // minutes
    public static final int ALARM_AUTO_OFF = 15; // if no auto snooze enabled wait 15 min
    public static final int ALARM_SNOOZE_AUTO_OFF = 45; // if no auto snooze enabled wait 45 min

    FireAlarmReceiver receiver = new FireAlarmReceiver();
    Sound sound;
    Handler handle = new Handler();
    Runnable alive;
    boolean alarmActivity = false; // if service crashed, activity willbe closed. ok to have var.
    Sound.Silenced silenced = Sound.Silenced.NONE;
    float mGZ;
    int mEventCountSinceGZChanged;
    Alarm alarm;
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
                long time = intent.getLongExtra("time", 0);
                showAlarmActivity(time, silenced);
            }
            if (a.equals(Intent.ACTION_SCREEN_OFF)) {
                // do nothing. do not annoy user. he will see alarm screen on next screen on event.
            }
            if (a.equals(SHOW_ACTIVITY)) {
                long time = intent.getLongExtra("time", 0);
                showAlarmActivity(time, silenced);
            }
        }
    }

    public static void activateAlarm(Context context, Alarm a) {
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

    public Alarm getAlarm(Intent intent) {
        Alarm a;

        String json = intent.getStringExtra("state");
        if (json == null || json.isEmpty())
            return null;

        a = new Alarm(this, json);

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

        if (intent == null) {
            Log.d(TAG, "onStartCommand restart");
            String json = shared.getString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, "");
            if (json.isEmpty())
                return START_NOT_STICKY;

            alarm = new Alarm(this, json);
        } else {
            alarm = getAlarm(intent);

            String json = shared.getString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, "");

            if (alarm == null) { // started without alarm, read stored alarm
                alarm = new Alarm(this, json);
            } else { // alarm loaded, does it interference with current running alarm?
                if (!json.isEmpty()) { // yep, we already firering alarm, show missed
                    Alarm a = new Alarm(this, json);
                    showNotificationMissed(this, a);
                }
            }

            SharedPreferences.Editor editor = shared.edit();
            editor.putString(HourlyApplication.PREFERENCE_ACTIVE_ALARM, alarm.save().toString());
            editor.commit();
        }

        Log.d(TAG, "id=" + alarm.id + ", time=" + Alarm.format(alarm.time));

        if (!alive(alarm)) {
            stopSelf();
            showNotificationMissed(this, alarm);
            return START_NOT_STICKY;
        }

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm != null) {
            Sensor a = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (a != null)
                sm.registerListener(this, a, SensorManager.SENSOR_DELAY_GAME);
        }

        showNotificationAlarm(alarm.time);

        // do we have silence alarm?
        silenced = sound.playAlarm(alarm);

        showAlarmActivity(alarm.time, silenced);

        return super.onStartCommand(intent, flags, startId);
    }

    static boolean dismiss(Context context, final Alarm a) { // do we have to dismiss (due timeout) alarm?
        Calendar cur = Calendar.getInstance();
        return dismiss(context, cur, a);
    }

    static boolean dismiss(Context context, Calendar cur, final Alarm a) { // do we have to dismiss (due timeout) alarm?
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(a.time); // dismiss checks for alarm scheduled time (original hour/min)
        cal.set(Calendar.HOUR_OF_DAY, a.getHour());
        cal.set(Calendar.MINUTE, a.getMin());
        return dismiss(context, cur, cal.getTimeInMillis());
    }

    static boolean dismiss(Context context, Calendar cur, long time) { // do we have to dismiss (due timeout) alarm?
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        Integer m = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_AFTER, "0"));
        int auto = ALARM_AUTO_OFF;
        if (m > 0)
            auto = ALARM_SNOOZE_AUTO_OFF;

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.add(Calendar.MINUTE, auto);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        return cur.after(cal);
    }

    public boolean snooze(final Alarm a) { // false - do not snooze, true - snooze
        Calendar cur = Calendar.getInstance();

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        Integer m = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_AFTER, "0"));

        if (m == 0)
            return false;

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(a.time); // snooze check alarm fire time (not hours/mins)
        cal.add(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        return cur.after(cal);
    }

    boolean alive(final Alarm a) {
        if (!dismiss(this, a)) { // do not check snooze on first run
            alive = new Runnable() {
                @Override
                public void run() {
                    if (snooze(a)) {
                        snooze(FireAlarmService.this, a.time);
                        stopSelf();
                        return;
                    }
                    if (!alive(a)) {
                        showNotificationMissed(FireAlarmService.this, a);
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

    public void showAlarmActivity(long time, Sound.Silenced silenced) {
        alarmActivity = true;
        AlarmActivity.showAlarmActivity(this, time, silenced);
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

        showNotificationAlarm(0);

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

    // show notification about missed alarm
    public static void showNotificationMissed(Context context, Alarm a) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);

        if (a == null) {
            notificationManager.cancel(HourlyApplication.NOTIFICATION_MISSED_ICON);
        } else {
            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
            Integer m = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_SNOOZE_AFTER, "0"));
            int auto = ALARM_AUTO_OFF;
            if (m > 0)
                auto = ALARM_SNOOZE_AUTO_OFF;

            final Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, a.getHour());
            cal.set(Calendar.MINUTE, a.getMin());
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.MINUTE, auto);
            long time = cal.getTimeInMillis();

            PendingIntent main = PendingIntent.getActivity(context, 0,
                    new Intent(context, MainActivity.class).setAction(MainActivity.SHOW_ALARMS_PAGE).putExtra("time", time),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            String text = context.getString(R.string.AlarmMissedAfter, a.format(), auto);

            RemoteViews view = new RemoteViews(context.getPackageName(), HourlyApplication.getTheme(context, R.layout.notification_alarm_light, R.layout.notification_alarm_dark));
            view.setOnClickPendingIntent(R.id.notification_base, main);
            view.setTextViewText(R.id.notification_subject, context.getString(R.string.AlarmMissed));
            view.setTextViewText(R.id.notification_text, text);
            view.setViewVisibility(R.id.notification_button, View.GONE);

            Notification.Builder builder = new Notification.Builder(context)
                    .setContentTitle(context.getString(R.string.Alarm))
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                    .setContent(view);

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(Notification.VISIBILITY_PUBLIC);

            notificationManager.notify(HourlyApplication.NOTIFICATION_MISSED_ICON, builder.build());
        }
    }

    // alarm dismiss button
    public void showNotificationAlarm(long time) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(HourlyApplication.PREFERENCE_NOTIFICATIONS, true))
            return;

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (time == 0) {
            notificationManager.cancel(HourlyApplication.NOTIFICATION_ALARM_ICON);
        } else {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(time);
            int hour = c.get(Calendar.HOUR_OF_DAY);
            int min = c.get(Calendar.MINUTE);

            PendingIntent button = PendingIntent.getService(this, 0,
                    new Intent(this, AlarmService.class).setAction(AlarmService.DISMISS).putExtra("time", time),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

            PendingIntent main = PendingIntent.getBroadcast(this, 0,
                    new Intent(SHOW_ACTIVITY).putExtra("time", time),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            String text = String.format("%02d:%02d", hour, min);

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

    public static void snooze(Context context, long time) {
        List<Alarm> list = HourlyApplication.loadAlarms(context);

        // create old list, we need to check conflicts with old alarms only, not shifted
        TreeSet<Long> alarms = new TreeSet<>();
        for (Alarm a : list) {
            if (a.enabled)
                alarms.add(a.time);
        }

        for (Alarm a : list) {
            if (a.time == time) { // can be disabled
                boolean b = a.enabled;
                a.snooze();

                if (!alarms.isEmpty() && a.time >= alarms.first()) { // did we hit another enabled alarm? stop snooze
                    FireAlarmService.showNotificationMissed(context, a);
                    a.setEnable(b); // enable && setNext
                } else {
                    final Calendar cur = Calendar.getInstance();
                    cur.setTimeInMillis(a.time);
                    if (dismiss(context, cur, a)) { // outdated by snooze timeout?
                        FireAlarmService.showNotificationMissed(context, a);
                        a.setEnable(b); // enable && setNext
                    }
                }
            }
        }
        HourlyApplication.saveAlarms(context, list);
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
                            snooze(this, alarm.time);
                            dismissActiveAlarm(this);
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

