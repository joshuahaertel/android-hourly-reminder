package com.github.axet.hourlyreminder.basics;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;

import com.github.axet.hourlyreminder.app.HourlyApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;

public class Alarm extends Week {
    public final static Uri DEFAULT_RING = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

    // unique id
    public long id;
    // alarm with ringtone?
    public boolean ringtone;
    // uri or file
    public String ringtoneValue;
    // beep?
    public boolean beep;
    // speech time?
    public boolean speech;

    public static class CustomComparator implements Comparator<Alarm> {
        @Override
        public int compare(Alarm o1, Alarm o2) {
            int c = new Integer(o1.getHour()).compareTo(o2.getHour());
            if (c != 0)
                return c;
            return new Integer(o1.getMin()).compareTo(o2.getMin());
        }
    }

    public Alarm(Alarm copy) {
        super(copy);

        id = copy.id;
        ringtone = copy.ringtone;
        ringtoneValue = copy.ringtoneValue;
        beep = copy.beep;
        speech = copy.speech;
    }

    public Alarm(Context context) {
        super(context);

        this.id = System.currentTimeMillis();

        enabled = false;
        weekdaysCheck = true;
        weekDaysValues = new ArrayList<>(Arrays.asList(Week.EVERYDAY));
        ringtone = false;
        beep = false;
        speech = true;
        ringtoneValue = DEFAULT_RING.toString();

        setTime(9, 0);
    }

    public Alarm(Context context, String json) {
        this(context);
        try {
            JSONObject o = new JSONObject(json);
            Alarm a = this;
            a.id = o.getLong("id");
            a.time = o.getLong("time");
            try {
                a.hour = o.getInt("hour");
                a.min = o.getInt("min");
            } catch (JSONException e) { // <=1.4.5
                setTime(a.time);
            }
            a.enabled = o.getBoolean("enable");
            a.weekdaysCheck = o.getBoolean("weekdays");
            a.setWeekDaysProperty(o.getJSONArray("weekdays_values"));
            a.ringtone = o.getBoolean("ringtone");
            a.ringtoneValue = o.optString("ringtone_value", null);
            a.beep = o.getBoolean("beep");
            a.speech = o.getBoolean("speech");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
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

    public static String format(long time) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm");
        return f.format(new Date(time));
    }

    public String format() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);
        if (DateFormat.is24HourFormat(context)) {
            SimpleDateFormat f = new SimpleDateFormat("HH:mm");
            return f.format(cal.getTime());
        } else {
            SimpleDateFormat f = new SimpleDateFormat("h:mm");
            return f.format(cal.getTime());
        }
    }

    public void setEnable(boolean e) {
        this.enabled = e;
        if (e)
            setNext();
    }

    public boolean getEnable() {
        return enabled;
    }

    // move current alarm +10 mins
    public void snooze() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String min = shared.getString(HourlyApplication.PREFERENCE_SNOOZE_DELAY, "10");
        Integer m = Integer.parseInt(min);

        enabled = true;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, m);
        time = cal.getTimeInMillis();
    }

    public boolean isSnoozed() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);
        return hour != this.hour || min != this.min;
    }

    public int getHour() {
        return hour;
    }

    public int getMin() {
        return min;
    }

    public JSONObject save() {
        try {
            Alarm a = this;
            JSONObject o = super.save();
            o.put("id", this.id);
            o.put("ringtone", a.ringtone);
            o.put("ringtone_value", a.ringtoneValue);
            o.put("beep", a.beep);
            o.put("speech", a.speech);
            return o;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

}
