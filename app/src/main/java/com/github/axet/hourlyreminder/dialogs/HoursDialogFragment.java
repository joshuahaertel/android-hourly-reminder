package com.github.axet.hourlyreminder.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.alarms.Reminder;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

public class HoursDialogFragment extends DialogFragment {
    private boolean mPreferenceChanged;

    View v;

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

    public static String[] AMPM = new String[]{
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
        outState.putStringArray("values", values.toArray(new String[]{}));
        outState.putBoolean("changed", mPreferenceChanged);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog d = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.hours)
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
                .setView(createView(LayoutInflater.from(getActivity()), null, savedInstanceState))
                .create();
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
        v = view;

        for (int i = 0; i < ids.length; i++) {
            CheckBox c = (CheckBox) view.findViewById(ids[i]);
            String h = Reminder.format(i);
            boolean b = values.contains(h);
            c.setChecked(b);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    changed(view);
                }
            });
            if (!DateFormat.is24HourFormat(context)) {
                c.setText(AMPM[i]);
            }
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

        return view;
    }

    void changed(View view) {
        mPreferenceChanged = true;
        Set<String> s = new TreeSet<>();
        for (int i = 0; i < ids.length; i++) {
            CheckBox c = (CheckBox) view.findViewById(ids[i]);
            String h = Reminder.format(i);
            if (c.isChecked()) {
                s.add(h);
            }
        }
        values = s;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Activity a = getActivity();
        if (a instanceof DialogInterface.OnDismissListener) {
            ((DialogInterface.OnDismissListener) a).onDismiss(new Result());
        }
    }
}
