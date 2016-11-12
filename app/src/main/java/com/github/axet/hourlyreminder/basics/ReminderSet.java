package com.github.axet.hourlyreminder.basics;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;

import com.github.axet.hourlyreminder.app.HourlyApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ReminderSet extends WeekSet {
    public final static Uri DEFAULT_NOTIFICATION = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

    public Set<String> hours; // actual hours selected
    public List<Reminder> list; // generated reminders (depend on repeat)
    public int repeat; // minutes

    public ReminderSet(Context context, Set<String> hours, int repeat) {
        super(context);

        this.repeat = repeat;

        load(hours);
    }

    public ReminderSet(Context context, Set<String> hours) {
        super(context);

        this.repeat = 60;
        this.enabled = true;
        this.ringtone = false;
        this.ringtoneValue = DEFAULT_NOTIFICATION.toString();

        load(hours);
    }

    public ReminderSet(Context context) {
        super(context);
        this.beep = true;
        this.speech = true;
        this.ringtone = false;
        this.ringtoneValue = DEFAULT_NOTIFICATION.toString();
        this.repeat = 60;
        load(new TreeSet<>(Arrays.asList(new String[]{"08", "09", "10", "11", "12"})));
    }

    public ReminderSet(Context context, String json) {
        super(context, json);
    }

    public String format() {
        return HourlyApplication.getHours2String(context, new ArrayList<>(hours));
    }

    public void load(Set<String> hours) {
        this.hours = new TreeSet<>(hours);
        this.list = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            String h = Reminder.format(hour);
            if (hours.contains(h)) {
                String next = Reminder.format(hour + 1);

                int max = repeat;

                if (hours.contains(next)) {
                    max = 60;
                }

                for (int m = 0; m < max; m += repeat) {
                    Reminder r = new Reminder(context, getWeekDaysProperty());
                    r.enabled = true;
                    r.setTime(hour, m);
                    list.add(r);
                }
            }
        }
    }

    @Override
    public void load(JSONObject o) throws JSONException {
        super.load(o);
        try {
            this.repeat = o.getInt("repeat");
            JSONArray list = o.getJSONArray("list");
            Set<String> hh = new TreeSet<>();
            for (int i = 0; i < list.length(); i++) {
                hh.add(list.getString(i));
            }
            load(hh);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JSONObject save() {
        try {
            JSONArray list = new JSONArray();
            for (String h : hours) {
                list.put(h);
            }
            JSONObject o = super.save();
            o.put("repeat", this.repeat);
            o.put("list", list);
            return o;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}