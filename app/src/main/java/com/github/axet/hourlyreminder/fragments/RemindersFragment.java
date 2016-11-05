package com.github.axet.hourlyreminder.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.basics.Alarm;
import com.github.axet.hourlyreminder.basics.ReminderSet;
import com.github.axet.hourlyreminder.basics.WeekSet;
import com.github.axet.hourlyreminder.dialogs.HoursDialogFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RemindersFragment extends WeekSetFragment implements DialogInterface.OnDismissListener {

    List<ReminderSet> reminders = new ArrayList<>();

    public RemindersFragment() {
    }

    int getPosition(long id) {
        for (int i = 0; i < reminders.size(); i++) {
            if (reminders.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getCount() {
        return reminders.size();
    }

    @Override
    public Object getItem(int position) {
        return reminders.get(position);
    }

    @Override
    public long getItemId(int position) {
        return reminders.get(position).id;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        reminders = HourlyApplication.loadReminders(getActivity());
        Collections.sort(reminders, new Alarm.CustomComparator());
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
            }
        });

        if (selected > 0)
            list.smoothScrollToPosition(getPosition(selected));

        return rootView;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        reminders = HourlyApplication.loadReminders(getActivity());
        Collections.sort(reminders, new Alarm.CustomComparator());
        changed();
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.reminder, parent, false);
            convertView.setTag(-1);
        }

        return super.getView(position, convertView, parent);
    }

    void save(WeekSet a) {
        super.save(a);
        HourlyApplication.saveReminders(getActivity(), reminders);
        if (reminders.indexOf(a) == 0)
            changed();
    }

    public void addAlarm(Alarm a) {
//        alarms.add(a);
//        Collections.sort(alarms, new Alarm.CustomComparator());
//        select(a.id);
//        int pos = alarms.indexOf(a);
//        list.smoothScrollToPosition(pos);
//
//        HourlyApplication.saveAlarms(getActivity(), alarms);

        boxAnimate = false;
    }

    @Override
    public void remove(WeekSet a) {
        super.remove(a);
        reminders.remove(a);
        HourlyApplication.saveReminders(getActivity(), reminders);
        boxAnimate = false;
    }

    @Override
    public void fillDetailed(final View view, final WeekSet a, boolean animate) {
        super.fillDetailed(view, a, animate);

        final ReminderSet rr = (ReminderSet) a;

        final TextView time = (TextView) view.findViewById(R.id.alarm_time);
        updateTime(view, (ReminderSet) a);
        time.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HoursDialogFragment dialog = new HoursDialogFragment();

                Bundle args = new Bundle();
                args.putStringArrayList("hours", new ArrayList<>(rr.hours));

                dialog.setArguments(args);
                dialog.show(getFragmentManager(), "");
            }
        });

        View ringtoneButton = view.findViewById(R.id.alarm_ringtone_value_box);
        ringtoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fragmentRequestRingtone = a;
                Uri uri = null;
                if (!a.ringtoneValue.isEmpty()) {
                    uri = Uri.parse(a.ringtoneValue);
                }
                RemindersOldFragment.selectRingtone(RemindersFragment.this, uri);
            }
        });

        View every = view.findViewById(R.id.alarm_every);
        every.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ;
            }
        });
    }

    @Override
    public void fillCompact(final View view, final WeekSet a, boolean animate) {
        super.fillCompact(view, a, animate);
        TextView time = (TextView) view.findViewById(R.id.alarm_time);
        updateTime(view, (ReminderSet) a);
        time.setClickable(false);

        View every = view.findViewById(R.id.alarm_every);
        every.setClickable(false);
    }

    void updateTime(View view, ReminderSet a) {
        TextView time = (TextView) view.findViewById(R.id.alarm_time);
        TextView am = (TextView) view.findViewById(R.id.alarm_am);
        TextView pm = (TextView) view.findViewById(R.id.alarm_pm);

//        int hour = a.getHour();

//        am.setText(HourlyApplication.getHour4String(getActivity(), hour));
//        pm.setText(HourlyApplication.getHour4String(getActivity(), hour));

        time.setText(a.format());
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        dialogInterface=null;
    }
}
