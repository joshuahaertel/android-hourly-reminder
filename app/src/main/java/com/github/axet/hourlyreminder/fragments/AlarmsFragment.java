package com.github.axet.hourlyreminder.fragments;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.RingtoneChoicer;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.alarms.Alarm;
import com.github.axet.hourlyreminder.alarms.WeekSet;
import com.github.axet.hourlyreminder.alarms.WeekTime;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.services.FireAlarmService;

import java.io.File;
import java.util.Collections;

public class AlarmsFragment extends WeekSetFragment {

    HourlyApplication.ItemsStorage items;

    public AlarmsFragment() {
    }

    int getPosition(long id) {
        for (int i = 0; i < items.alarms.size(); i++) {
            if (items.alarms.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getCount() {
        return items.alarms.size();
    }

    @Override
    public Object getItem(int position) {
        return items.alarms.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.alarms.get(position).id;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        items = HourlyApplication.from(getContext()).items;

        Collections.sort(items.alarms, new Alarm.CustomComparator());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_alarms, container, false);
        list = (ListView) rootView.findViewById(R.id.section_label);
        list.setAdapter(this);
        list.setOnScrollListener(this);
        FloatingActionButton fab = (FloatingActionButton) rootView.findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Alarm a = new Alarm(getActivity(), System.currentTimeMillis());
                TimePickerDialog d = new TimePickerDialog(getActivity(), new TimePickerDialog.OnTimeSetListener() {
                    // onTimeSet called twice on old phones
                    //
                    // http://stackoverflow.com/questions/19452993
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            addAlarm(a);
                            HourlyApplication.toastAlarmSet(getActivity(), a);
                        }
                    };

                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        a.setTime(hourOfDay, minute);
                        if (r != null) {
                            r.run();
                            r = null;
                        }
                    }
                }, a.getHour(), a.getMin(), DateFormat.is24HourFormat(getActivity()));
                d.show();
            }
        });

        if (selected > 0)
            list.smoothScrollToPosition(getPosition(selected));

        return rootView;
    }

    @Override
    void setWeek(WeekSet a, int week, boolean c) {
        super.setWeek(a, week, c);
        WeekTime t = (WeekTime) a;
        long time = t.getTime();
        if (t.getTime() != time && a.enabled) {
            HourlyApplication.toastAlarmSet(getActivity(), t);
        }
    }

    @Override
    public void setEnable(WeekSet a, boolean e) {
        super.setEnable(a, e);
        if (e)
            HourlyApplication.toastAlarmSet(getActivity(), (WeekTime) a);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (key.startsWith(HourlyApplication.PREFERENCE_ALARMS_PREFIX)) {
            items.loadAlarms(sharedPreferences);
            Collections.sort(items.alarms, new Alarm.CustomComparator());
            changed();
        }
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.alarm, parent, false);
            convertView.setTag(-1);
        }

        return super.getView(position, convertView, parent);
    }

    void save(WeekSet a) {
        super.save(a);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.unregisterOnSharedPreferenceChangeListener(this);
        items.saveAlarms();
        shared.registerOnSharedPreferenceChangeListener(this);
    }

    public void addAlarm(Alarm a) {
        items.alarms.add(a);
        Collections.sort(items.alarms, new Alarm.CustomComparator());
        select(a.id);
        int pos = items.alarms.indexOf(a);
        list.smoothScrollToPosition(pos);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.unregisterOnSharedPreferenceChangeListener(this);
        items.saveAlarms();
        boxAnimate = false;
    }

    @Override
    public void remove(WeekSet a) {
        super.remove(a);
        items.alarms.remove(a);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.unregisterOnSharedPreferenceChangeListener(this);
        items.saveAlarms();
        shared.registerOnSharedPreferenceChangeListener(this);
        boxAnimate = false;
    }

    @Override
    public void fillDetailed(final View view, final WeekSet w, boolean animate) {
        super.fillDetailed(view, w, animate);

        final WeekTime t = (WeekTime) w;

        View ringtoneButton = view.findViewById(R.id.alarm_ringtone_value_box);
        ringtoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri uri = w.ringtoneValue;

                if (uri == null) {
                    uri = Alarm.DEFAULT_ALARM;
                }

                RingtoneChoicer r = new RingtoneChoicer() {
                    @Override
                    public void onResult(Uri uri, boolean tmp) {
                        if (tmp) {
                            File f = storage.storeRingtone(uri);
                            uri = Uri.fromFile(f);
                        }
                        w.ringtoneValue = fallbackUri(uri);
                        save(w);
                    }

                    @Override
                    public void onDismiss() {
                        choicer = null;
                    }
                };
                r.setPermissionsDialog(AlarmsFragment.this, Storage.PERMISSIONS_RO, RESULT_RINGTONE);
                r.setRingtone(AlarmsFragment.this, Alarm.TYPE_ALARM, Alarm.DEFAULT_ALARM, getString(R.string.SelectAlarm), RESULT_RINGTONE);
                choicer = r;
                choicer.show(uri);
            }
        });

        final TextView time = (TextView) view.findViewById(R.id.alarm_time);
        updateTime(view, (Alarm) w);
        time.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TimePickerDialog d = new TimePickerDialog(getActivity(), new TimePickerDialog.OnTimeSetListener() {
                    // onTimeSet called twice on old phones
                    //
                    // http://stackoverflow.com/questions/19452993
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            if (w.enabled)
                                HourlyApplication.toastAlarmSet(getActivity(), t);
                            updateTime(view, (Alarm) w);
                            save(w);
                        }
                    };

                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        t.setTime(hourOfDay, minute);
                        if (r != null) {
                            r.run();
                            r = null;
                        }
                    }
                }, t.getHour(), t.getMin(), DateFormat.is24HourFormat(getActivity()));
                d.show();
            }
        });
    }

    @Override
    public void fillCompact(final View view, final WeekSet a, boolean animate) {
        super.fillCompact(view, a, animate);
        TextView time = (TextView) view.findViewById(R.id.alarm_time);
        updateTime(view, (Alarm) a);
        time.setClickable(false);
    }

    void updateTime(View view, Alarm a) {
        TextView time = (TextView) view.findViewById(R.id.alarm_time);
        TextView am = (TextView) view.findViewById(R.id.alarm_am);
        TextView pm = (TextView) view.findViewById(R.id.alarm_pm);

        int hour = a.getHour();

        am.setText(HourlyApplication.getHour4String(getActivity(), hour));
        pm.setText(HourlyApplication.getHour4String(getActivity(), hour));

        time.setText(a.format2412());

        if (DateFormat.is24HourFormat(getActivity())) {
            am.setVisibility(View.GONE);
            pm.setVisibility(View.GONE);
        } else {
            am.setVisibility(a.getHour() >= 12 ? View.GONE : View.VISIBLE);
            pm.setVisibility(a.getHour() >= 12 ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    Sound.Silenced playPreview(WeekSet a) {
        Sound.Silenced s = sound.playAlarm(new FireAlarmService.FireAlarm((Alarm) a), 0, null);
        sound.silencedToast(s, System.currentTimeMillis());
        return s;
    }

    @Override
    Uri fallbackUri(Uri uri) {
        if (uri != null) {
            return uri;
        } else {
            return Alarm.DEFAULT_ALARM;
        }
    }
}
