package com.github.axet.hourlyreminder.basics;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class WeekSet extends Week {
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

    public static class CustomComparator implements Comparator<WeekSet> {
        @Override
        public int compare(WeekSet o1, WeekSet o2) {
            int c = new Integer(o1.getHour()).compareTo(o2.getHour());
            if (c != 0)
                return c;
            return new Integer(o1.getMin()).compareTo(o2.getMin());
        }
    }

    public WeekSet(WeekSet copy) {
        super(copy);

        id = copy.id;
        ringtone = copy.ringtone;
        ringtoneValue = copy.ringtoneValue;
        beep = copy.beep;
        speech = copy.speech;
    }

    public WeekSet(Context context) {
        super(context);

        this.id = System.currentTimeMillis();

        enabled = false;
        weekdaysCheck = true;
        weekDaysValues = new ArrayList<>(Arrays.asList(Week.EVERYDAY));
        ringtone = true;
        beep = true;
        speech = true;
        ringtoneValue = DEFAULT_RING.toString();
    }

    public WeekSet(Context context, String json) {
        super(context);
        try {
            JSONObject o = new JSONObject(json);
            load(o);
        } catch (JSONException e) {
            throw new RuntimeException(e);
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

    public void load(JSONObject o) {
        try {
            WeekSet a = this;
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

    public JSONObject save() {
        try {
            WeekSet a = this;
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
