package com.github.axet.hourlyreminder.basics;

import android.content.Context;

import com.github.axet.hourlyreminder.app.HourlyApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ReminderSet extends WeekSet {
    public Set<String> hours; // actual hours selected
    public List<Reminder> list; // generated reminders (depend on repeat)
    public int repeat; // minutes

    public ReminderSet(Context context, Set<String> hours, int repeat) {
        super(context);

        this.repeat = repeat;

        load(hours, repeat);
    }

    public ReminderSet(Context context, long time) {
        super(context);
        setTime(time);
        this.beep = true;
        this.speech = true;
        this.ringtone = false;
    }

    public ReminderSet(Context context, String json) {
        super(context, json);
    }

    public String format() {
        return HourlyApplication.getHours2String(context, new ArrayList<>(hours));
    }

    void load(Set<String> hours, int repeat) {
        ArrayList<Reminder> list = new ArrayList<>();

        for (int hour = 0; hour < 24; hour++) {
            String h = Reminder.format(hour);

            Reminder r = new Reminder(context);
            r.enabled = hours.contains(h);
            r.setTime(hour, 0);
            list.add(r);

            String next = Reminder.format(hour + 1);

            if (r.enabled && hours.contains(next)) {
                for (int m = repeat; m < 60; m += repeat) {
                    r = new Reminder(context);
                    r.enabled = true;
                    r.setTime(hour, m);
                    list.add(r);
                }
            }
        }
        this.list = list;
        this.hours = new TreeSet<>(hours);
    }

    @Override
    public void load(JSONObject o) {
        super.load(o);
        try {
            this.repeat = o.getInt("repeat");
            JSONArray list = o.getJSONArray("list");
            Set<String> hh = new TreeSet<>();
            for (int i = 0; i < list.length(); i++) {
                hh.add(list.getString(i));
            }
            load(hh, this.repeat);
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
