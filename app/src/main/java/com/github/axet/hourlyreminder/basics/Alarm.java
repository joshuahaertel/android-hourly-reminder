package com.github.axet.hourlyreminder.basics;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.github.axet.hourlyreminder.app.HourlyApplication;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class Alarm extends WeekTime {
    public final static Uri DEFAULT_ALARM = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

    public Alarm(Alarm copy) {
        super(copy);
    }

    public Alarm(Context context) {
        super(context);

        ringtone = false;
        ringtoneValue = DEFAULT_ALARM.toString();
        beep = false;

        setTime(9, 0);
    }

    public Alarm(Context context, String json) {
        super(context, json);
    }

    public Alarm(Context context, long time) {
        this(context);

        this.enabled = true;
        this.beep = true;
        this.weekdaysCheck = false;
        this.ringtone = true;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);

        setTime(hour, min);
    }

    // move current alarm +10 mins
    public void snooze() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String min = shared.getString(HourlyApplication.PREFERENCE_SNOOZE_DELAY, "10");
        Integer m = Integer.parseInt(min);

        enabled = true;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.MINUTE, m);
        time = cal.getTimeInMillis();
    }

    public boolean isSnoozed() { // timezone shift not checked
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);
        return hour != this.hour || min != this.min;
    }

    public static String format24(long time) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm");
        return f.format(new Date(time));
    }

    public String format2412() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);
        return WeekSet.format2412(context, cal.getTimeInMillis());
    }

    public String format2412ap() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);
        return format2412ap(context, cal.getTimeInMillis());
    }
}
