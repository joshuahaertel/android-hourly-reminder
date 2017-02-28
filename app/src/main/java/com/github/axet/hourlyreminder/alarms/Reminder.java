package com.github.axet.hourlyreminder.alarms;

import android.content.Context;

import java.util.Set;

public class Reminder extends WeekTime {
    public Reminder(Context context) {
        super(context);
    }

    public Reminder(Context context, Set days) {
        super(context);

        weekdaysCheck = true;
        setWeekDaysProperty(days);
    }

    public static String format(int hour) {
        return String.format("%02d", hour);
    }
}
