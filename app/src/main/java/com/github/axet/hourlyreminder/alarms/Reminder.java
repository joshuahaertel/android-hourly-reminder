package com.github.axet.hourlyreminder.alarms;

import android.content.Context;
import android.text.format.DateFormat;

import java.util.Calendar;
import java.util.Set;

public class Reminder extends WeekTime {
    public static final int HALF = 30;

    public static class Key {
        public String key;
        public int hour;
        public int min;

        public Key(int h) {
            this(h, 0);
        }

        public Key(int h, int m) {
            if (m == 0)
                key = format(h);
            else
                key = format(h) + format(m);
            hour = h;
            min = m;
        }

        public Key(String k) {
            key = k;
            if (k.length() == 4) {
                String h = k.substring(0, 2);
                String m = k.substring(2, 4);
                hour = Integer.parseInt(h);
                min = Integer.parseInt(m);
            } else {
                hour = Integer.parseInt(k);
            }
        }

        public boolean next(Key k) {
            int next = hour + 1;
            if (next > 23)
                next = 0;
            if (min == 0 && hour == k.hour && k.min != 0)
                return true;
            if (min == 0 && next == k.hour && k.min == 0)
                return true;
            if (min != 0 && next == k.hour && k.min == 0)
                return true;
            return false;
        }

        public String formatShort(Context context) {
            boolean h24 = DateFormat.is24HourFormat(context);

            String[] H12 = new String[]{
                    "12", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11",
                    "12", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11",
            };

            if (min == 0) {
                if (h24) {
                    return Reminder.format(hour);
                }
                return H12[hour];
            } else {
                if (h24) {
                    return Reminder.format(hour) + ":" + Reminder.format(min);
                }
                return H12[hour] + ":" + Reminder.format(min);
            }
        }

        @Override
        public String toString() {
            return key;
        }
    }

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

    // check if it is the same hour and min. if reminder loaded late, it will have time will be in the future
    public boolean isSoundAlarm(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int min = cal.get(Calendar.MINUTE);
        return this.hour == hour && this.min == min;
    }

}
