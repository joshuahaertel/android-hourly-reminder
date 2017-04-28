package com.github.axet.hourlyreminder.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.alarms.Reminder;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.widgets.RoundCheckbox;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class HoursDialogFragment extends DialogFragment {
    private boolean mPreferenceChanged;

    View v;
    View title;
    CheckBox min30;
    View hours60_1;
    View hours30_1;
    View hours60_2;
    View hours30_2;
    TextView status;
    Button okb;

    public static int[] ids = new int[]{
            R.id.hours_00,
            R.id.hours_01,
            R.id.hours_02,
            R.id.hours_03,
            R.id.hours_04,
            R.id.hours_05,
            R.id.hours_06,
            R.id.hours_07,
            R.id.hours_08,
            R.id.hours_09,
            R.id.hours_10,
            R.id.hours_11,
            R.id.hours_12,
            R.id.hours_13,
            R.id.hours_14,
            R.id.hours_15,
            R.id.hours_16,
            R.id.hours_17,
            R.id.hours_18,
            R.id.hours_19,
            R.id.hours_20,
            R.id.hours_21,
            R.id.hours_22,
            R.id.hours_23,
    };

    public static int[] ids30 = new int[]{
            R.id.hours_0030,
            R.id.hours_0130,
            R.id.hours_0230,
            R.id.hours_0330,
            R.id.hours_0430,
            R.id.hours_0530,
            R.id.hours_0630,
            R.id.hours_0730,
            R.id.hours_0830,
            R.id.hours_0930,
            R.id.hours_1030,
            R.id.hours_1130,
            R.id.hours_1230,
            R.id.hours_1330,
            R.id.hours_1430,
            R.id.hours_1530,
            R.id.hours_1630,
            R.id.hours_1730,
            R.id.hours_1830,
            R.id.hours_1930,
            R.id.hours_2030,
            R.id.hours_2130,
            R.id.hours_2230,
            R.id.hours_2330,
    };

    public static String[] H12 = new String[]{
            "12",
            "1",
            "2",
            "3",
            "4",
            "5",
            "6",
            "7",
            "8",
            "9",
            "10",
            "11",
            "12",
            "1",
            "2",
            "3",
            "4",
            "5",
            "6",
            "7",
            "8",
            "9",
            "10",
            "11",
    };

    public static String[] H24 = new String[]{
            "00",
            "01",
            "02",
            "03",
            "04",
            "05",
            "06",
            "07",
            "08",
            "09",
            "10",
            "11",
            "12",
            "13",
            "14",
            "15",
            "16",
            "17",
            "18",
            "19",
            "20",
            "21",
            "22",
            "23",
    };

    Set<String> values;

    boolean ok;

    public class Result implements DialogInterface {
        public int index;
        public Set<String> hours;
        public boolean ok;

        public Result() {
            this.hours = values;
            this.index = getArguments().getInt("index");
            this.ok = HoursDialogFragment.this.ok;
        }

        @Override
        public void cancel() {
        }

        @Override
        public void dismiss() {
        }
    }

    public HoursDialogFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            values = new TreeSet<>(Arrays.asList(savedInstanceState.getStringArray("values")));
            mPreferenceChanged = savedInstanceState.getBoolean("changed");
        } else {
            values = new TreeSet<>(getArguments().getStringArrayList("hours"));
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        values = save();
        outState.putStringArray("values", values.toArray(new String[]{}));
        outState.putBoolean("changed", mPreferenceChanged);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        title = inflater.inflate(R.layout.hours_title, null, false);
        min30 = (CheckBox) title.findViewById(R.id.title30);
        v = createView(LayoutInflater.from(getActivity()), null, savedInstanceState);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setCustomTitle(title)
                .setNegativeButton(getString(android.R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.dismiss();
                            }
                        }
                )
                .setPositiveButton(getString(android.R.string.ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                ok = true;
                                dialog.dismiss();
                            }
                        }
                )
                .setView(v);
        final AlertDialog d = builder.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                okb = d.getButton(DialogInterface.BUTTON_POSITIVE);
                update();
            }
        });
        return d;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return null; // must be null or Illigal state exception
    }

    View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = inflater.getContext();
        final View view = inflater.inflate(R.layout.hours, container, false);

        status = (TextView) view.findViewById(R.id.status);

        hours60_1 = view.findViewById(R.id.hours_60_1);
        hours30_1 = view.findViewById(R.id.hours_30_1);
        hours60_2 = view.findViewById(R.id.hours_60_2);
        hours30_2 = view.findViewById(R.id.hours_30_2);

        for (int i = 0; i < ids.length; i++) {
            CheckBox c = (CheckBox) view.findViewById(ids[i]);
            String h = Reminder.format(i);
            boolean b = values.contains(h);
            c.setChecked(b);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    changed();
                }
            });
            if (!DateFormat.is24HourFormat(context)) {
                c.setText(H12[i]);
            } else {
                c.setText(H24[i]);
            }
        }
        for (int i = 0; i < ids30.length; i++) {
            final int id = ids30[i];
            CheckBox c = (CheckBox) hours30_1.findViewById(id);
            if (c == null)
                c = (CheckBox) hours30_2.findViewById(id);
            Reminder.Key h = new Reminder.Key(i, Reminder.HALF);
            boolean b = values.contains(h.key);
            c.setChecked(b);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    changed();
                }
            });
        }

        View am = view.findViewById(R.id.hours_am);
        View pm = view.findViewById(R.id.hours_pm);

        if (DateFormat.is24HourFormat(context)) {
            am.setVisibility(View.GONE);
            pm.setVisibility(View.GONE);
        } else {
            am.setVisibility(View.VISIBLE);
            pm.setVisibility(View.VISIBLE);
        }

        boolean b = false;
        for (String hour : values) {
            Reminder.Key k = new Reminder.Key(hour);
            if (k.min != 0)
                b = true;
        }
        min30.setChecked(b);
        min30.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                update();
            }
        });

        return view;
    }

    void changed() {
        mPreferenceChanged = true;
        values = save();
        update();
        values = save(); // half hours maybe removed
        update(); // and update status
    }

    Set<String> save() {
        Set<String> s = new TreeSet<>();
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            CheckBox c = (CheckBox) v.findViewById(id);
            Reminder.Key h = new Reminder.Key(i);
            if (c.isChecked()) {
                s.add(h.key);
            }
        }
        if (min30.isChecked()) {
            for (int i = 0; i < ids30.length; i++) {
                int id = ids30[i];
                CheckBox c = (CheckBox) hours30_1.findViewById(id);
                if (c == null)
                    c = (CheckBox) hours30_2.findViewById(id);
                Reminder.Key hh = new Reminder.Key(i, Reminder.HALF);
                if (c.isChecked()) {
                    s.add(hh.key);
                }
            }
        }
        return s;
    }

    void setDot30(int id, boolean dot, int dotV) {
        int accent = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
        View v = hours60_1.findViewById(id);
        if (v == null)
            v = hours60_2.findViewById(id);
        v.setVisibility(dotV);
        if (dot) {
            v.setBackgroundColor(accent);
        } else {
            v.setBackgroundColor(RoundCheckbox.SECOND_BACKGROUND);
        }
    }

    void setBut30(int id, boolean min, int minV) {
        CheckBox c = (CheckBox) hours30_1.findViewById(id);
        if (c == null)
            c = (CheckBox) hours30_2.findViewById(id);
        c.setVisibility(minV);
        c.setChecked(min);
    }

    void update() {
        okb.setEnabled(!values.isEmpty());
        if (!min30.isChecked()) {
            for (int id : ids30) {
                setDot30(id, false, View.INVISIBLE);
                setBut30(id, false, View.INVISIBLE);
            }
            status.setText(HourlyApplication.getHours2String(getContext(), save()));
            return;
        }
        status.setText(HourlyApplication.getHours2String(getContext(), values));
        for (int hour = 0; hour < 24; hour++) {
            Reminder.Key half = new Reminder.Key(hour, Reminder.HALF);
            String h = Reminder.format(hour);
            int hh = hour + 1;
            if (hh > 23)
                hh = 0;
            String next = Reminder.format(hh);
            if (values.contains(h)) {
                if (values.contains(next)) {
                    setDot30(ids30[hour], true, View.VISIBLE);
                    setBut30(ids30[hour], false, View.INVISIBLE);
                } else {
                    boolean b = values.contains(half.key);
                    setDot30(ids30[hour], b, View.VISIBLE);
                    setBut30(ids30[hour], b, View.VISIBLE);
                }
            } else {
                if (values.contains(next)) {
                    boolean b = values.contains(half.key);
                    setDot30(ids30[hour], b, View.VISIBLE);
                    setBut30(ids30[hour], b, View.VISIBLE);
                } else {
                    setDot30(ids30[hour], false, View.INVISIBLE);
                    setBut30(ids30[hour], false, View.INVISIBLE);
                }
            }
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Activity a = getActivity();
        if (a instanceof DialogInterface.OnDismissListener) {
            values = save();
            ((DialogInterface.OnDismissListener) a).onDismiss(new Result());
        }
    }
}
