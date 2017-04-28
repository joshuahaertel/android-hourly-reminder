package com.github.axet.hourlyreminder.alarms;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.app.HourlyApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ReminderSet extends WeekSet {
    public static final int TYPE_NOTIFICATION = RingtoneManager.TYPE_NOTIFICATION;
    public static final Uri DEFAULT_NOTIFICATION = RingtoneManager.getDefaultUri(TYPE_NOTIFICATION);

    public Set<String> hours; // actual hours selected
    public List<Reminder> list; // generated reminders (depend on repeat)
    public int repeat; // minutes

    public static final Set<String> DEF_HOURS = new TreeSet<>(Arrays.asList(new String[]{"08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21"}));

    public ReminderSet(Context context, Set<String> hours, int repeat) {
        super(context);
        this.repeat = repeat;
        this.ringtoneValue = DEFAULT_NOTIFICATION.toString();
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
        load(DEF_HOURS);
    }

    public ReminderSet(Context context, String json) {
        super(context, json);
    }

    public String format() {
        return HourlyApplication.getHours2String(context, hours);
    }

    @Override
    public String formatDays() {
        if (!weekdaysCheck) {
            return context.getString(R.string.Everyday);
        }
        return super.formatDays();
    }

    public void load(Set<String> hours) {
        this.hours = new TreeSet<>(hours);
        this.list = new ArrayList<>();

        ArrayList<String> list = new ArrayList<>(hours);

        // find start index, it maybe mid night or daylight interval.
        int start = 0;
        if (list.contains(Reminder.format(0))) {
            for (int prev = 23; prev >= 0; prev--) {
                String h = Reminder.format(prev) + Reminder.HALF;
                int i = list.indexOf(h);
                if (i != -1) {
                    start = i;
                }
                h = Reminder.format(prev);
                i = list.indexOf(h);
                if (i == -1) {
                    break;
                }
                start = i;
            }
        }

        Reminder.Key prev = null;
        for (int i = 0; i < list.size(); i++) {
            int index = start + i;
            if (index >= list.size()) {
                index -= list.size();
            }
            Reminder.Key s = new Reminder.Key(list.get(index));
            int max;
            if (prev != null) {
                if (prev.next(s)) { // have next, roll up full hour
                    if (s.min == 0)
                        max = 60;
                    else
                        max = s.min;
                } else {
                    max = repeat;
                }
                add(prev, max);
            }
            prev = s;
        }
        if (prev != null) { // add last hour
            int min = prev.min + repeat;
            if (min < repeat)
                min = repeat;
            add(prev, min);
        }
    }

    void add(Reminder.Key prev, int max) {
        if (repeat > 0) {
            for (int m = prev.min; m < max; m += repeat) {
                Reminder r = new Reminder(context, getWeekDaysProperty());
                r.enabled = true;
                r.setTime(prev.hour, m);
                this.list.add(r);
            }
        } else { // negative means once per hour at specified time
            int min = -repeat;
            if (prev.min < min) {
                Reminder r = new Reminder(context, getWeekDaysProperty());
                r.enabled = true;
                r.setTime(prev.hour, min);
                this.list.add(r);
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
