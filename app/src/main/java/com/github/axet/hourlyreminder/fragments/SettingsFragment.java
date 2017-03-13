package com.github.axet.hourlyreminder.fragments;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceGroupAdapter;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceViewHolder;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.support.v7.widget.ContentFrameLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.FilePathPreference;
import com.github.axet.androidlibrary.widgets.SeekBarPreference;
import com.github.axet.androidlibrary.widgets.SeekBarPreferenceDialogFragment;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.app.SoundConfig;
import com.github.axet.hourlyreminder.alarms.Reminder;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.dialogs.BeepPrefDialogFragment;
import com.github.axet.hourlyreminder.widgets.CustomSoundListPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    Sound sound;
    Handler handler = new Handler();

    static String getTitle(Context context, String t) {
        String s = HourlyApplication.getTitle(context, t);
        if (s == null)
            s = "None";
        return s;
    }

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference.getKey().equals(HourlyApplication.PREFERENCE_RINGTONE)) {
                preference.setSummary(getTitle(preference.getContext(), stringValue));
                return true;
            }

            if (preference.getKey().equals(HourlyApplication.PREFERENCE_CUSTOM_SOUND)) {
                CustomSoundListPreference pp = (CustomSoundListPreference) preference;
                pp.update(stringValue);
                // keep update List type pref
                // return true;
            }

            if (preference.getKey().equals(HourlyApplication.PREFERENCE_HOURS)) {
                List sortedList = new ArrayList((Set) value);
                preference.setSummary(HourlyApplication.getHours2String(preference.getContext(), sortedList));
                return true;
            }

            if (preference.getKey().equals(HourlyApplication.PREFERENCE_DAYS)) {
                Reminder r = new Reminder(preference.getContext(), (Set) value);
                preference.setSummary(r.formatDays());
                return true;
            }

            if (preference instanceof FilePathPreference) {
                if (stringValue.isEmpty())
                    stringValue = preference.getContext().getString(R.string.not_selected);
                preference.setSummary(stringValue);
                return true;
            }

            if (preference instanceof SeekBarPreference) {
                float f = (Float) value;
                preference.setSummary((int) (f * 100) + "%");
            } else if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };


    //
    // support library 23.0.1 and api 23 failed with:
    //
    // https://code.google.com/p/android/issues/detail?id=85392#makechanges
    //
    // http://stackoverflow.com/questions/30336635
    //
    // To fix this, we need create our own PreferenceGroupAdapter
    //
    class PreferenceGroupAdapterFix extends PreferenceGroupAdapter {
        public PreferenceGroupAdapterFix(PreferenceGroup preferenceGroup) {
            super(preferenceGroup);
        }

        public void onBindViewHolder(PreferenceViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);

            // LinerLayoutManager.onLayoutChildren() call detach(), then fill() which cause:
            //
            // onBindViewHolder cause SwitchCompat.setCheck() call on currently detached view !!!
            // so no animation starts.
            // then called RecyclerView.attachViewToParent()
        }

        public void onViewAttachedToWindow(PreferenceViewHolder holder) {
            super.onViewAttachedToWindow(holder);
        }

        public void onViewDetachedFromWindow(PreferenceViewHolder holder) {
            super.onViewDetachedFromWindow(holder);
        }
    }

    // LinearLayoutManager llm;

    class LinearLayoutManagerFix extends LinearLayoutManager {
        public LinearLayoutManagerFix(Context context) {
            super(context);
        }

        public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
            super.onLayoutChildren(recycler, state);
        }

        @Override
        public void addView(View child) {
            if (child.getParent() != null)
                return;
            super.addView(child);
        }

        @Override
        public void addView(View child, int index) {
            if (child.getParent() != null)
                return;
            super.addView(child, index);
        }

        @Override
        public void addDisappearingView(View child) {
            if (child.getParent() != null)
                return;
            super.addDisappearingView(child);
        }

        @Override
        public void addDisappearingView(View child, int index) {
            if (child.getParent() != null)
                return;
            super.addDisappearingView(child, index);
        }
    }

    class RecyclerViewFix extends RecyclerView {
        public RecyclerViewFix(Context context) {
            super(context);
        }

        @Override
        protected void attachViewToParent(View child, int index, ViewGroup.LayoutParams params) {
            super.attachViewToParent(child, index, params);
        }

        @Override
        protected void detachViewFromParent(View child) {
            super.detachViewFromParent(child);
        }

        @Override
        protected void detachViewFromParent(int index) {
            super.detachViewFromParent(index);
        }
    }

//    @Override
//    public RecyclerView.LayoutManager onCreateLayoutManager() {
//        return llm = new LinearLayoutManagerFix(this.getActivity());
//    }

//    @Override
//    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
//        return new PreferenceGroupAdapterFix(preferenceScreen);
//    }

//    @Override
//    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
//        //RecyclerView recyclerView = (RecyclerView)inflater.inflate(android.support.v14.preference.R.layout.preference_recyclerview, parent, false);
//        RecyclerView recyclerView = new RecyclerViewFix(getActivity());
//        recyclerView.setLayoutManager(this.onCreateLayoutManager());
//        return recyclerView;
//    }

    public static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getAll().get(preference.getKey()));
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
    }

    @Override
    public Fragment getCallbackFragment() {
        return this;
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof SeekBarPreference) {
            AudioManager am = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamVolume(AudioManager.STREAM_ALARM), AudioManager.FLAG_SHOW_UI);
            SeekBarPreferenceDialogFragment f = SeekBarPreferenceDialogFragment.newInstance(preference.getKey());
            ((DialogFragment) f).setTargetFragment(this, 0);
            ((DialogFragment) f).show(this.getFragmentManager(), "android.support.v14.preference.PreferenceFragment.DIALOG");
            return;
        }
        if (preference.getKey().equals(HourlyApplication.PREFERENCE_BEEP_CUSTOM)) {
            BeepPrefDialogFragment f = BeepPrefDialogFragment.newInstance(preference.getKey());
            ((DialogFragment) f).setTargetFragment(this, 0);
            ((DialogFragment) f).show(this.getFragmentManager(), "android.support.v14.preference.PreferenceFragment.DIALOG");
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_settings);
        setHasOptionsMenu(true);

        sound = new Sound(getActivity());

        PreferenceGroup advanced = (PreferenceGroup) findPreference("advanced");
        Preference alarm = findPreference(HourlyApplication.PREFERENCE_ALARM);
        // 23 SDK requires to be Alarm to be percice on time
        if (Build.VERSION.SDK_INT < 23) {
            advanced.removePreference(alarm);
        } else {
            // it is only for 23 api phones and up. since only alarms can trigs often then 15 mins.
            alarm.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    boolean b = (Boolean) o;
                    List<ReminderSet> reminders = HourlyApplication.loadReminders(getActivity());
                    boolean set = false;
                    for (ReminderSet rs : reminders) {
                        if (rs.repeat > 0 && rs.repeat < 15) {
                            if (!b) {
                                set = true;
                                rs.repeat = 15;
                            }
                        }
                    }
                    if (set) {
                        Toast.makeText(getActivity(), R.string.Reminders15, Toast.LENGTH_SHORT).show();
                        HourlyApplication.saveReminders(getActivity(), reminders);
                    }
                    return true;
                }
            });
        }

        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_VOLUME));

        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_INCREASE_VOLUME));

        PreferenceGroup speak = (PreferenceGroup) findPreference("speak");
        if (DateFormat.is24HourFormat(getActivity())) {
            speak.removePreference(findPreference(HourlyApplication.PREFERENCE_SPEAK_AMPM));
        }

        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_THEME));
        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_WEEKSTART));

        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_SNOOZE_DELAY));
        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_SNOOZE_AFTER));

        findPreference(HourlyApplication.PREFERENCE_CALLSILENCE).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                if (!permitted(PERMISSIONS)) {
                    permitted(PERMISSIONS, 1);
                    return false;
                }
                return true;
            }
        });

        PreferenceGroup sounds = (PreferenceGroup) findPreference("sounds");
        Preference vp = findPreference(HourlyApplication.PREFERENCE_VIBRATE);

        if (!hasVibrator()) {
            sounds.removePreference(vp);
        } else {
            vp.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (!permitted(PERMISSIONS_V)) {
                        permitted(PERMISSIONS_V, 2);
                        return false;
                    }
                    return true;
                }
            });
        }

        SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.registerOnSharedPreferenceChangeListener(this);
    }

    boolean hasVibrator() {
        Vibrator v = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT < 11)
            return true;
        return v.hasVibrator();
    }

    void setPhone() {
        SwitchPreferenceCompat s = (SwitchPreferenceCompat) findPreference(HourlyApplication.PREFERENCE_CALLSILENCE);
        s.setChecked(true);
    }

    void setVibr() {
        SwitchPreferenceCompat s = (SwitchPreferenceCompat) findPreference(HourlyApplication.PREFERENCE_VIBRATE);
        s.setChecked(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1:
                if (permitted(PERMISSIONS))
                    setPhone();
                else
                    Toast.makeText(getActivity(), R.string.NotPermitted, Toast.LENGTH_SHORT).show();
                break;
            case 2:
                if (permitted(PERMISSIONS_V))
                    setVibr();
                else
                    Toast.makeText(getActivity(), R.string.NotPermitted, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_PHONE_STATE};

    public static final String[] PERMISSIONS_V = new String[]{Manifest.permission.VIBRATE};

    boolean permitted(String[] ss) {
        if (Build.VERSION.SDK_INT < 11)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(getActivity(), s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    boolean permitted(String[] p, int c) {
        if (Build.VERSION.SDK_INT < 11)
            return true;
        for (String s : p) {
            if (ContextCompat.checkSelfPermission(getActivity(), s) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(p, c);
                return false;
            }
        }
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);

        {
            final Context context = inflater.getContext();
            ViewGroup layout = (ViewGroup) view.findViewById(android.R.id.list_container);
            RecyclerView v = getListView();

            int fab_margin = (int) getResources().getDimension(R.dimen.fab_margin);
            int fab_size = ThemeUtils.dp2px(getActivity(), 61);
            int pad = 0;
            int top = 0;
            if (Build.VERSION.SDK_INT <= 16) { // so, it bugged only on 16
                pad = ThemeUtils.dp2px(context, 10);
                top = (int) getResources().getDimension(R.dimen.appbar_padding_top);
            }

            v.setClipToPadding(false);
            v.setPadding(pad, top, pad, pad + fab_size + fab_margin);

            FloatingActionButton fab = new FloatingActionButton(context);
            fab.setImageResource(R.drawable.play);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ContentFrameLayout.LayoutParams.WRAP_CONTENT, ContentFrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            lp.setMargins(fab_margin, fab_margin, fab_margin, fab_margin);
            fab.setLayoutParams(lp);
            layout.addView(fab);

            // fix nexus 9 tabled bug, when fab showed offscreen
            handler.post(new Runnable() {
                @Override
                public void run() {
                    view.requestLayout();
                }
            });

            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    SoundConfig.Silenced s = sound.playReminder(new ReminderSet(context), System.currentTimeMillis(), null);
                    sound.silencedToast(s, System.currentTimeMillis());
                }
            });
        }

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (sound != null) {
            sound.close();
            sound = null;
        }

        SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals(HourlyApplication.PREFERENCE_ALARM)) {
            ((SwitchPreferenceCompat) findPreference(HourlyApplication.PREFERENCE_ALARM)).setChecked(sharedPreferences.getBoolean(HourlyApplication.PREFERENCE_ALARM, false));
        }
    }
}
