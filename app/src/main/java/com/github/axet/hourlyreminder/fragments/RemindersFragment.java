package com.github.axet.hourlyreminder.fragments;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.widgets.RingtoneChoicer;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.alarms.WeekSet;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.dialogs.HoursDialogFragment;
import com.github.axet.hourlyreminder.dialogs.RepeatDialogFragment;

import java.io.File;
import java.util.ArrayList;

public class RemindersFragment extends WeekSetFragment implements DialogInterface.OnDismissListener {
    HourlyApplication.ItemsStorage items;

    public RemindersFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        items = HourlyApplication.from(getContext()).items;
        adapter = new Adapter() {
            @Override
            public int getItemCount() {
                return items.reminders.size();
            }

            @Override
            public Object getItem(int position) {
                return items.reminders.get(position);
            }

            @Override
            public long getItemId(int position) {
                return items.reminders.get(position).id;
            }

            @Override
            public int getPosition(long id) {
                for (int i = 0; i < items.reminders.size(); i++) {
                    if (items.reminders.get(i).id == id)
                        return i;
                }
                return -1;
            }

            @Override
            public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                View convertView = inflater.inflate(R.layout.reminder, parent, false);
                return new ViewHolder(convertView);
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
                HoursDialogFragment dialog = new HoursDialogFragment();

                ReminderSet rr = new ReminderSet(getActivity());

                Bundle args = new Bundle();
                args.putInt("index", -1);
                args.putStringArrayList("hours", new ArrayList<>(rr.hours));

                dialog.setArguments(args);
                dialog.show(getFragmentManager(), "");
            }
        });

        if (selected > 0)
            list.smoothScrollToPosition(adapter.getPosition(selected));

        return rootView;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (key.startsWith(HourlyApplication.PREFERENCE_REMINDERS_PREFIX)) {
            items.loadReminders(sharedPreferences);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void setEnable(WeekSet a, boolean e) {
        super.setEnable(a, e);
        ((ReminderSet) a).reload(); // update hours after enable / disable
    }

    void save(WeekSet a) {
        super.save(a);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.unregisterOnSharedPreferenceChangeListener(this); // prevent reload reminders
        items.saveReminders();
        shared.registerOnSharedPreferenceChangeListener(this);
        adapter.notifyDataSetChanged();
    }

    public void addAlarm(ReminderSet a) {
        int pos = items.reminders.size();
        items.reminders.add(a);
        adapter.notifyItemInserted(pos);
        select(-1);
        selected = a.id;
        list.smoothScrollToPosition(pos);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.unregisterOnSharedPreferenceChangeListener(this);
        items.saveReminders();
        shared.registerOnSharedPreferenceChangeListener(this);
        boxAnimate = false;
    }

    @Override
    public void remove(WeekSet a) {
        super.remove(a);
        items.reminders.remove(a);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.unregisterOnSharedPreferenceChangeListener(this);
        items.saveReminders();
        shared.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void fillDetailed(final View view, final WeekSet w, final boolean animate) {
        super.fillDetailed(view, w, animate);

        final ReminderSet rr = (ReminderSet) w;

        View ringtoneButton = view.findViewById(R.id.alarm_ringtone_value_box);
        ringtoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri uri = w.ringtoneValue;

                if (uri == null)
                    uri = ReminderSet.DEFAULT_NOTIFICATION;

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
                r.setPermissionsDialog(RemindersFragment.this, Storage.PERMISSIONS_RO, RESULT_RINGTONE);
                r.setRingtone(RemindersFragment.this, ReminderSet.TYPE_NOTIFICATION, ReminderSet.DEFAULT_NOTIFICATION, getString(R.string.Reminder), RESULT_RINGTONE);
                choicer = r;
                choicer.show(uri);
            }
        });

        final TextView time = (TextView) view.findViewById(R.id.alarm_time);
        updateTime(view, (ReminderSet) w);
        time.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HoursDialogFragment dialog = new HoursDialogFragment();

                Bundle args = new Bundle();
                args.putInt("index", items.reminders.indexOf(w));
                args.putStringArrayList("hours", new ArrayList<>(rr.hours));

                dialog.setArguments(args);
                dialog.show(getFragmentManager(), "");
            }
        });

        TextView every = (TextView) view.findViewById(R.id.alarm_every);
        every.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RepeatDialogFragment d = new RepeatDialogFragment();

                Bundle args = new Bundle();
                args.putInt("index", items.reminders.indexOf(w));
                args.putInt("mins", rr.repeat);
                d.setArguments(args);

                d.show(getFragmentManager(), "");
            }
        });
        updateEvery(every, w);

        final CheckBox weekdays = (CheckBox) view.findViewById(R.id.alarm_week_days);
        weekdays.setButtonDrawable(null);
        weekdays.setClickable(false);
    }

    @Override
    public void fillCompact(final View view, final WeekSet a, boolean animate) {
        super.fillCompact(view, a, animate);
        TextView time = (TextView) view.findViewById(R.id.alarm_time);
        updateTime(view, (ReminderSet) a);
        time.setClickable(false);

        TextView every = (TextView) view.findViewById(R.id.alarm_every);
        every.setClickable(false);

        updateEvery(every, a);
    }

    void updateEvery(TextView every, WeekSet a) {
        String str = "";
        final ReminderSet rr = (ReminderSet) a;
        if (rr.repeat < 0) {
            str = ":" + (-rr.repeat) + getString(R.string.min_symbol);
        } else {
            switch (rr.repeat) {
                case 60:
                    str = "1" + getString(R.string.hour_symbol);
                    break;
                default:
                    str = "" + rr.repeat + getString(R.string.min_symbol);
                    break;
            }
        }
        every.setText(str);
    }

    void updateTime(View view, ReminderSet a) {
        TextView time = (TextView) view.findViewById(R.id.alarm_time);
        time.setText(a.format());
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        if (dialogInterface instanceof HoursDialogFragment.Result) {
            HoursDialogFragment.Result r = (HoursDialogFragment.Result) dialogInterface;
            if (!r.ok)
                return;
            if (r.index == -1) {
                ReminderSet rs = new ReminderSet(getActivity(), r.hours);
                addAlarm(rs);
            } else {
                ReminderSet rs = items.reminders.get(r.index);
                rs.load(r.hours);
                save(rs);
            }
        }
        if (dialogInterface instanceof RepeatDialogFragment.Result) {
            RepeatDialogFragment.Result r = (RepeatDialogFragment.Result) dialogInterface;
            if (!r.ok)
                return;
            ReminderSet rs = items.reminders.get(r.index);
            rs.repeat = r.mins;
            rs.reload(); // update hours
            save(rs);

            // it is only for 23 api phones and up. since only alarms can trigs often then 15 mins.
            if (Build.VERSION.SDK_INT >= 23) {
                if (rs.repeat > 0 && rs.repeat < 15) {
                    SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
                    boolean b = shared.getBoolean(HourlyApplication.PREFERENCE_ALARM, false);
                    if (!b) {
                        Toast.makeText(getActivity(), R.string.alarm_type_alarm, Toast.LENGTH_SHORT).show();
                        SharedPreferences.Editor edit = shared.edit();
                        edit.putBoolean(HourlyApplication.PREFERENCE_ALARM, true);
                        edit.commit();
                    }
                }
            }
        }
    }

    @Override
    public Uri fallbackUri(Uri uri) {
        if (uri != null)
            return uri;
        else
            return ReminderSet.DEFAULT_NOTIFICATION;
    }

    @Override
    public Sound.Silenced playPreview(WeekSet a) {
        Sound.Silenced s = sound.playReminder((ReminderSet) a, System.currentTimeMillis(), new Runnable() {
            @Override
            public void run() {
                previewCancel();
            }
        });
        sound.silencedToast(s, System.currentTimeMillis());
        return s;
    }

    @Override
    public void setWeek(WeekSet a, int week, boolean c) {
        super.setWeek(a, week, c);
        if (a.noDays()) {
            a.weekdaysCheck = true;
            a.setEveryday();
            a.setWeek(week, false);
        }
    }
}
