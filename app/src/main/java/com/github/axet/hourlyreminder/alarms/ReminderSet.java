package com.github.axet.hourlyreminder.alarms;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.text.format.DateFormat;

import com.github.axet.hourlyreminder.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ReminderSet extends WeekSet {
    public static final int TYPE_NOTIFICATION = RingtoneManager.TYPE_NOTIFICATION;
    public static final Uri DEFAULT_NOTIFICATION = RingtoneManager.getDefaultUri(TYPE_NOTIFICATION);

    public Set<String> hours; // actual hours selected
    public List<Reminder> list; // generated reminders (depend on repeat)
    public int repeat; // minutes, negative means once per hour at specified time
    public long last; // last reminder announced, to prevent double announcements

    public static final Set<String> DEF_HOURS = new TreeSet<>(Arrays.asList(new String[]{"08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21"}));

    public ReminderSet(Context context, Set<String> hours, int repeat) {
        super(context);
        this.repeat = repeat;
        this.ringtoneValue = DEFAULT_NOTIFICATION;
        load(hours);
    }

    public ReminderSet(Context context, Set<String> hours) {
        super(context);
        this.repeat = 60;
        this.enabled = true;
        this.ringtone = false;
        this.ringtoneValue = DEFAULT_NOTIFICATION;
        load(hours);
    }

    public ReminderSet(Context context) {
        super(context);
        this.beep = true;
        this.speech = true;
        this.ringtone = false;
        this.ringtoneValue = DEFAULT_NOTIFICATION;
        this.repeat = 60;
        load(DEF_HOURS);
    }

    public ReminderSet(Context context, String json) {
        super(context, json);
    }

    public String format() {
        return format(context, hours);
    }

    @Override
    public String formatDays() {
        if (!weekdaysCheck)
            return context.getString(R.string.Everyday);
        else
            return super.formatDays();
    }

    public void reload() {
        last = 0;
        load(hours);
    }

    public void load(Set<String> hours) {
        this.hours = new TreeSet<>(hours);
        this.list = new ArrayList<>();

        ArrayList<String> list = new ArrayList<>(hours);

        if (list.isEmpty())
            return;

        // find start index, it maybe mid night or daylight interval.
        int start = 0;
        if (list.contains(Reminder.format(0))) {
            for (int prev = 23; prev >= 0; prev--) {
                Reminder.Key hh = new Reminder.Key(prev, Reminder.HALF);
                int i = list.indexOf(hh.key);
                if (i != -1)
                    start = i;
                Reminder.Key h = new Reminder.Key(prev);
                i = list.indexOf(h.key);
                if (i == -1)
                    break;
                start = i;
            }
        }

        Reminder.Key prev = null;
        for (int i = 0; i <= list.size(); i++) {
            int index = start + i;
            if (index >= list.size())
                index -= list.size();
            Reminder.Key s = new Reminder.Key(list.get(index));
            if (prev != null) {
                int max;
                if (prev.next(s)) { // have next, roll up full hour
                    if (s.min == 0)
                        max = 60; // 59 + 1
                    else
                        max = s.min + 1; // min + 1
                } else {
                    max = repeat;
                }
                add(prev, max);
            }
            prev = s;
        }
    }

    void add(Reminder.Key prev, int max) {
        if (repeat > 0) {
            for (int m = prev.min; m < max; m += repeat) {
                Reminder r = new Reminder(this, prev.hour, m);
                r.enabled = true;
                list.add(r);
            }
        } else {
            int min = -repeat;
            if (prev.min < min) {
                Reminder r = new Reminder(this, prev.hour, min);
                r.enabled = true;
                list.add(r);
            }
        }
    }

    @Override
    public void setNext() {
        super.setNext();
        reload();
    }

    @Override
    public void load(JSONObject o) throws JSONException {
        super.load(o);
        this.weekdaysCheck = true; // force weekdays to true for reminders
        try {
            this.repeat = o.getInt("repeat");
            this.last = o.optLong("last");
            JSONArray list = o.getJSONArray("list");
            Set<String> hh = new TreeSet<>();
            for (int i = 0; i < list.length(); i++)
                hh.add(list.getString(i));
            load(hh);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JSONObject save() {
        try {
            JSONArray list = new JSONArray();
            for (String h : hours)
                list.put(h);
            JSONObject o = super.save();
            o.put("repeat", this.repeat);
            o.put("list", list);
            o.put("last", last);
            return o;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static String format(Context context, Set<String> hours) {
        boolean h24 = DateFormat.is24HourFormat(context);

        String AM = context.getString(R.string.day_am);
        String PM = context.getString(R.string.day_pm);

        String H = context.getString(R.string.hour_symbol);

        String str = "";

        ArrayList<String> list = new ArrayList<>(hours);

        // find start index, it maybe mid night or daylight interval.
        int start = 0;
        if (list.contains(Reminder.format(0))) {
            for (int prev = 23; prev >= 0; prev--) {
                Reminder.Key hh = new Reminder.Key(prev, Reminder.HALF);
                int i = list.indexOf(hh.key);
                if (i != -1)
                    start = i;
                Reminder.Key h = new Reminder.Key(prev);
                i = list.indexOf(h.key);
                if (i == -1)
                    break;
                start = i;
            }
        }

        int count = 0;
        Reminder.Key prev = null;
        Reminder.Key last = null;
        for (int i = 0; i < list.size(); i++) {
            int index = start + i;
            if (index >= list.size())
                index -= list.size();
            Reminder.Key s = new Reminder.Key(list.get(index));
            if (prev != null && prev.next(s)) {
                count++;
            } else {
                if (count != 0) {
                    if (!h24) {
                        if (last.hour < 12 && prev.hour >= 12)
                            str += AM;
                        if (last.hour >= 12 && s.hour < 12)
                            str += PM;
                    }
                    if (count == 1 && (last.min == 0 && prev.min == 0))
                        str += ",";
                    else
                        str += "-";
                    str += prev.formatShort(context);
                    if (!h24) {
                        if (last.hour < 12 && s.hour >= 12)
                            str += AM;
                        if (last.hour >= 12 && s.hour < 12)
                            str += PM;
                    }
                    str += ",";
                    str += s.formatShort(context);
                    last = s;
                } else {
                    if (last != null) {
                        if (!h24) {
                            if (last.hour < 12 && s.hour >= 12)
                                str += AM;
                            if (last.hour >= 12 && s.hour < 12)
                                str += PM;
                        }
                        str += ",";
                    }
                    str += s.formatShort(context);
                    last = s;
                }
                count = 0;
            }
            prev = s;
        }

        if (count != 0) {
            if (!h24) {
                if (last.hour < 12 && prev.hour >= 12)
                    str += AM;
                if (last.hour >= 12 && prev.hour < 12)
                    str += PM;
            }
            if (count == 1 && (last.min == 0 && prev.min == 0))
                str += ",";
            else
                str += "-";
            str += prev.formatShort(context);
        }
        if (prev != null) {
            if (h24)
                str += H;
            else
                str += (prev.hour >= 12 ? PM : AM);
        }

        return str;
    }

    @Override
    protected Uri defaultRingtone() {
        return DEFAULT_NOTIFICATION;
    }

    @Override
    public String toString() {
        return format() + " " + formatDays();
    }
}
