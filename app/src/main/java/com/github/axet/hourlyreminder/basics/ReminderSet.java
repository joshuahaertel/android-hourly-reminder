package com.github.axet.hourlyreminder.basics;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ReminderSet extends WeekSet {
    public List<Reminder> list;
    public int repeat; // minutes

    public ReminderSet(Context context, Set<String> hours, int repeat) {
        super(context);

        this.repeat = repeat;

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
    }
}
