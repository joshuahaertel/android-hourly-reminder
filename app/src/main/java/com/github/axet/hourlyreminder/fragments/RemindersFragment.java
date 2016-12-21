package com.github.axet.hourlyreminder.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.basics.ReminderSet;
import com.github.axet.hourlyreminder.basics.WeekSet;
import com.github.axet.hourlyreminder.dialogs.HoursDialogFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RemindersFragment extends WeekSetFragment implements DialogInterface.OnDismissListener {

    List<ReminderSet> reminders = new ArrayList<>();

    public static void selectRingtone(Fragment context, Uri uri) {
        // W/MediaPlayer: Couldn't open file on client side; trying server side:
        // java.lang.SecurityException: Permission Denial: reading com.android.providers.media.MediaProvider uri content://media/external/audio/media/17722
        // from pid=697, uid=10204
        // requires android.permission.READ_EXTERNAL_STORAGE, or grantUriPermission()
        //
        // context.grantUriPermission("com.android.providers.media.MediaProvider", Uri.parse("content://media/external/images/media"), Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (uri == null) {
            uri = ReminderSet.DEFAULT_NOTIFICATION;
        }

        context.startActivityForResult(new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, ReminderSet.DEFAULT_NOTIFICATION)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.Reminder))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri), 0);
    }

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
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
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
            list.smoothScrollToPosition(getPosition(selected));

        return rootView;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (key.startsWith(HourlyApplication.PREFERENCE_REMINDERS_PREFIX)) {
            reminders = HourlyApplication.loadReminders(getActivity());
            changed();
        }
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
    }

    public void addAlarm(ReminderSet a) {
        reminders.add(a);
        select(a.id);
        int pos = reminders.indexOf(a);
        list.smoothScrollToPosition(pos);
        HourlyApplication.saveReminders(getActivity(), reminders);
        boxAnimate = false;
    }

    @Override
    public void remove(WeekSet a) {
        super.remove(a);
        reminders.remove(a);
        HourlyApplication.saveReminders(getActivity(), reminders);
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
                args.putInt("index", reminders.indexOf(a));
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
                selectRingtone(RemindersFragment.this, uri);
            }
        });

        TextView every = (TextView) view.findViewById(R.id.alarm_every);
        every.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final List<String> ss = new ArrayList<>(Arrays.asList(getActivity().getResources().getStringArray(R.array.repeat_values)));
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.every);
                builder.setSingleChoiceItems(R.array.repeat_text, ss.indexOf("" + rr.repeat), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String s = ss.get(which);
                        int min = Integer.parseInt(s);
                        rr.repeat = min;
                        save(rr);

                        // it is only for 23 api phones and up. since only alarms can trigs often then 15 mins.
                        if (Build.VERSION.SDK_INT >= 23) {
                            if (min < 15) {
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

                        dialog.dismiss();
                    }
                });
                Dialog d = builder.create();
                d.show();
            }
        });
        updateEvery(every, a);
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
        switch (rr.repeat) {
            case 60:
                str = "1" + getString(R.string.hour_symbol);
                break;
            default:
                str = "" + rr.repeat + getString(R.string.min_symbol);
                break;
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
                ReminderSet rs = reminders.get(r.index);
                rs.load(r.hours);
                save(rs);
            }
        }
    }

    @Override
    String fallbackUri(Uri uri) {
        if (uri != null) {
            return uri.toString();
        } else {
            return ReminderSet.DEFAULT_NOTIFICATION.toString();
        }
    }

    @Override
    Sound.Silenced playPreview(WeekSet a) {
        Sound.Silenced s = sound.playReminder((ReminderSet) a, System.currentTimeMillis(), new Runnable() {
            @Override
            public void run() {
                previewCancel();
            }
        });
        sound.silencedToast(s, System.currentTimeMillis());
        return s;
    }
}
