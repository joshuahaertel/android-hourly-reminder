package com.github.axet.hourlyreminder.fragments;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    public static class ViewHolder extends WeekSetFragment.ViewHolder {
        TextView am;
        TextView pm;

        public ViewHolder(View v) {
            super(v);
            am = (TextView) v.findViewById(R.id.alarm_am);
            pm = (TextView) v.findViewById(R.id.alarm_pm);
        }
    }

    public AlarmsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        items = HourlyApplication.from(getContext()).items;
        Collections.sort(items.alarms, new Alarm.CustomComparator());

        adapter = new Adapter<ViewHolder>() {
            @Override
            public int getItemCount() {
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
            public int getPosition(long id) {
                for (int i = 0; i < items.alarms.size(); i++) {
                    if (items.alarms.get(i).id == id)
                        return i;
                }
                return -1;
            }

            @Override
            public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                View convertView = inflater.inflate(R.layout.alarm, parent, false);
                return new ViewHolder(convertView);
            }

            @Override
            public void fillDetailed(final ViewHolder h, final WeekSet w, boolean animate) {
                super.fillDetailed(h, w, animate);

                final WeekTime t = (WeekTime) w;

                h.ringtoneButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Uri uri = w.ringtoneValue;

                        if (uri == null)
                            uri = Alarm.DEFAULT_ALARM;

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

                updateTime(h, (Alarm) w);
                h.time.setOnClickListener(new View.OnClickListener() {
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
                                    updateTime(h, (Alarm) w);
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
            public void fillCompact(final ViewHolder h, final WeekSet a, boolean animate) {
                super.fillCompact(h, a, animate);
                updateTime(h, (Alarm) a);
                h.time.setClickable(false);
            }
        };
        adapter.setHasStableIds(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_alarms, container, false);
        list = (RecyclerView) rootView.findViewById(R.id.section_label);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);
        list.setItemAnimator(animator);
        list.addOnScrollListener(animator.onScrollListener);
        list.addItemDecoration(new DividerItemDecoration(list.getContext(), DividerItemDecoration.VERTICAL));
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
            list.smoothScrollToPosition(adapter.getPosition(selected));

        return rootView;
    }

    @Override
    public void setWeek(WeekSet a, int week, boolean c) {
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
            adapter.notifyDataSetChanged();
        }
    }

    void save(WeekSet a) {
        super.save(a);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.unregisterOnSharedPreferenceChangeListener(this); // prevent reload alarms
        items.saveAlarms();
        shared.registerOnSharedPreferenceChangeListener(this);
        adapter.notifyDataSetChanged();
    }

    public void addAlarm(Alarm a) {
        items.alarms.add(a);
        Collections.sort(items.alarms, new Alarm.CustomComparator());
        int pos = items.alarms.indexOf(a);
        adapter.notifyItemInserted(pos);
        selectAdd(a.id);
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

    void updateTime(ViewHolder h, Alarm a) {
        int hour = a.getHour();

        h.am.setText(HourlyApplication.getHour4String(getActivity(), hour));
        h.pm.setText(HourlyApplication.getHour4String(getActivity(), hour));

        h.time.setText(a.format2412());

        if (DateFormat.is24HourFormat(getActivity())) {
            h.am.setVisibility(View.GONE);
            h.pm.setVisibility(View.GONE);
        } else {
            h.am.setVisibility(a.getHour() >= 12 ? View.GONE : View.VISIBLE);
            h.pm.setVisibility(a.getHour() >= 12 ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public Sound.Silenced playPreview(WeekSet a) {
        Sound.Silenced s = sound.playAlarm(new FireAlarmService.FireAlarm((Alarm) a), 0, null);
        sound.silencedToast(s, System.currentTimeMillis());
        return s;
    }

    @Override
    public Uri fallbackUri(Uri uri) {
        if (uri != null)
            return uri;
        else
            return Alarm.DEFAULT_ALARM;
    }
}
