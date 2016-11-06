package com.github.axet.hourlyreminder.dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v14.preference.MultiSelectListPreference;
import android.support.v14.preference.PreferenceDialogFragment;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.basics.Reminder;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

public class HoursPrefDialogFragment extends PreferenceDialogFragment {
    private boolean mPreferenceChanged;

    Set<String> values;

    public HoursPrefDialogFragment() {
    }

    public static HoursPrefDialogFragment newInstance(String key) {
        HoursPrefDialogFragment fragment = new HoursPrefDialogFragment();
        Bundle b = new Bundle(1);
        b.putString("key", key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            values = new TreeSet<>(Arrays.asList(savedInstanceState.getStringArray("values")));
            mPreferenceChanged = savedInstanceState.getBoolean("changed");
        } else {
            MultiSelectListPreference preference = (MultiSelectListPreference) getPreference();
            values = new TreeSet<>(preference.getValues());
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArray("values", values.toArray(new String[]{}));
        outState.putBoolean("changed", mPreferenceChanged);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        Context context = builder.getContext();

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater.inflate(R.layout.hours, null, false);

        for (int i = 0; i < HoursDialogFragment.ids.length; i++) {
            CheckBox c = (CheckBox) view.findViewById(HoursDialogFragment.ids[i]);
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
                c.setText(HoursDialogFragment.AMPM[i]);
            }
        }

        View am = view.findViewById(R.id.hours_am);
        View pm = view.findViewById(R.id.hours_pm);

        if (DateFormat.is24HourFormat(context)) {
            am.setVisibility(View.GONE);
            pm.setVisibility(View.GONE);
        }else {
            am.setVisibility(View.VISIBLE);
            pm.setVisibility(View.VISIBLE);
        }

        builder.setView(view);
    }

    void changed(View view) {
        mPreferenceChanged = true;
        Set<String> s = new TreeSet<>();
        for (int i = 0; i < HoursDialogFragment.ids.length; i++) {
            CheckBox c = (CheckBox) view.findViewById(HoursDialogFragment.ids[i]);
            String h = Reminder.format(i);
            if (c.isChecked()) {
                s.add(h);
            }
        }
        values = s;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        MultiSelectListPreference preference = (MultiSelectListPreference) getPreference();
        if (positiveResult && this.mPreferenceChanged) {
            if (preference.callChangeListener(values)) {
                preference.setValues(values);
            }
        }
        this.mPreferenceChanged = false;
    }
}
