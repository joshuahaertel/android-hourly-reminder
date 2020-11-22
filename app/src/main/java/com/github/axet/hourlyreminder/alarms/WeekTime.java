package com.github.axet.hourlyreminder.alarms;

import android.content.Context;

import com.github.axet.androidlibrary.app.AlarmManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;

public class WeekTime extends WeekSet {
    // may be incorrect if user moved from one time zone to another or snoozed
    long time;
    // we have to keep original hours/minutes to proper handle time zone shifts
    int hour;
    int min;

    public static class CustomComparator implements Comparator<WeekTime> {
        @Override
        public int compare(WeekTime o1, WeekTime o2) {
            int c = new Integer(o1.getHour()).compareTo(o2.getHour());
            if (c != 0)
                return c;
            return new Integer(o1.getMin()).compareTo(o2.getMin());
        }
    }

    public WeekTime(Context context) {
        super(context);
    }

    public WeekTime(WeekTime copy) {
        super(copy);
        time = copy.time;
        hour = copy.hour;
        min = copy.min;
    }

    public WeekTime(WeekSet copy, int h, int m) {
        super(copy);
        setTime(h, m);
    }

    public WeekTime(Context context, String json) {
        super(context, json);
    }

    public long getTime() {
        return time;
    }

    public long getSetTime() {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time); // dismiss checks for alarm scheduled time (original hour/min)
        cal.set(Calendar.HOUR_OF_DAY, getHour());
        cal.set(Calendar.MINUTE, getMin());
        return cal.getTimeInMillis();
    }

    public void setTime(long l) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(l);
        this.hour = cal.get(Calendar.HOUR_OF_DAY);
        this.min = cal.get(Calendar.MINUTE);
        this.time = l;
    }

    // set today alarm
    public void setTime(int hour, int min) {
        Calendar cur = Calendar.getInstance();

        this.hour = hour;
        this.min = min;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);

        this.time = getAlarmTime(cal, cur);
    }

    // move alarm to the next day (tomorrow)
    //
    // (including weekdays checks)
    public void setTomorrow() {
        Calendar cur = Calendar.getInstance();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);
        cal.add(Calendar.DATE, 1);

        time = getAlarmTime(cal, cur);
    }

    // set alarm to go off next possible time
    //
    // today or tomorrow (including weekday checks)
    @Override
    public void setNext() {
        Calendar cur = Calendar.getInstance();

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);

        time = getAlarmTime(cal, cur);
    }

    // If alarm time > current time == tomorrow. Or compare hours.
    public boolean isToday() {
        Calendar cur = Calendar.getInstance();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);

        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
        return fmt.format(cur.getTime()).equals(fmt.format(cal.getTime()));
    }

    public boolean isTomorrow() {
        Calendar cur = Calendar.getInstance();
        cur.add(Calendar.DATE, 1);

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);

        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd");
        return fmt.format(cur.getTime()).equals(fmt.format(cal.getTime()));
    }

    @Override
    public void load(JSONObject o) throws JSONException {
        super.load(o);
        this.time = o.getLong("time");
        try {
            this.hour = o.getInt("hour");
            this.min = o.getInt("min");
        } catch (JSONException e) { // <=1.4.5
            setTime(this.time);
        }
    }

    public JSONObject save() {
        try {
            JSONObject o = super.save();
            o.put("time", this.time);
            o.put("hour", this.hour);
            o.put("min", this.min);
            return o;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public int getHour() {
        return hour;
    }

    public int getMin() {
        return min;
    }

    public String toString() {
        return "WeekTime: " + AlarmManager.formatTime(time);
    }
}
