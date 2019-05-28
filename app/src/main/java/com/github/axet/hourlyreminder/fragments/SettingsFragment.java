package com.github.axet.hourlyreminder.fragments;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.support.v7.widget.ContentFrameLayout;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.services.PersistentService;
import com.github.axet.androidlibrary.widgets.AppCompatSettingsThemeActivity;
import com.github.axet.androidlibrary.widgets.FilePathPreference;
import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.SeekBarPreference;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.alarms.Reminder;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.app.SoundConfig;
import com.github.axet.hourlyreminder.services.AlarmService;
import com.github.axet.hourlyreminder.widgets.BeepPreference;
import com.github.axet.hourlyreminder.widgets.CustomSoundListPreference;
import com.github.axet.hourlyreminder.widgets.TTSPreference;

import java.util.Set;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_PHONE_STATE};

    public static final int RESULT_PHONE = 1;

    Sound sound;
    Handler handler = new Handler();
    PersistentService.SettingsReceiver receiver;

    public static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference.getKey().equals(HourlyApplication.PREFERENCE_CUSTOM_SOUND)) {
                CustomSoundListPreference pp = (CustomSoundListPreference) preference;
                pp.update(stringValue);
                // return true; // keep update List type pref
            }

            if (preference.getKey().equals(HourlyApplication.PREFERENCE_HOURS)) {
                preference.setSummary(ReminderSet.format(preference.getContext(), (Set) value));
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
            return AppCompatSettingsThemeActivity.sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, value);
        }
    };

    public static void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getAll().get(preference.getKey()));
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
            SoundConfig.SoundChannel c = sound.getSoundChannel();
            AudioManager am = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
            am.setStreamVolume(c.streamType, am.getStreamVolume(c.streamType), AudioManager.FLAG_SHOW_UI);
            SeekBarPreference.show(this, preference.getKey());
            return;
        }
        if (preference instanceof BeepPreference) {
            BeepPreference.BeepPrefDialogFragment.show(this, preference.getKey());
            return;
        }
        if (preference instanceof TTSPreference) {
            TTSPreference.TTSPrefDialogFragment.show(this, preference.getKey());
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

        PreferenceGroup app = (PreferenceGroup) findPreference("application");
        PreferenceGroup advanced = (PreferenceGroup) findPreference("advanced");
        Preference alarm = findPreference(HourlyApplication.PREFERENCE_ALARM);
        // 21+ SDK requires to be Alarm to be precise on time
        if (Build.VERSION.SDK_INT < 21)
            alarm.setVisible(false);
        if (Build.VERSION.SDK_INT >= 26 && getContext().getApplicationInfo().targetSdkVersion >= 26)
            findPreference(HourlyApplication.PREFERENCE_NOTIFICATIONS).setVisible(false);
        // 23+ SDK requires to be Alarm to be precise on time
        if (Build.VERSION.SDK_INT >= 23) {
            // it is only for 23 api phones and up. since only alarms can trigs often then 15 mins.
            alarm.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                @TargetApi(23)
                public boolean onPreferenceChange(Preference preference, Object o) {
                    boolean b = (Boolean) o;
                    HourlyApplication.ItemsStorage items = HourlyApplication.from(getActivity()).items;
                    boolean set = false;
                    for (ReminderSet rs : items.reminders) {
                        if (rs.repeat > 0 && rs.repeat < 15) {
                            if (!b) {
                                set = true;
                                rs.repeat = 15;
                            }
                        }
                    }
                    if (set) {
                        Toast.makeText(getActivity(), R.string.Reminders15, Toast.LENGTH_SHORT).show();
                        items.saveReminders();
                    }
                    return true;
                }
            });
        }

        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_VOLUME));

        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_INCREASE_VOLUME));

        PreferenceGroup speak = (PreferenceGroup) findPreference("speak");
        if (DateFormat.is24HourFormat(getActivity()))
            findPreference(HourlyApplication.PREFERENCE_SPEAK_AMPM).setVisible(false);

        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_THEME));
        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_WEEKSTART));

        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_SNOOZE_DELAY));
        bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_SNOOZE_AFTER));

        findPreference(HourlyApplication.PREFERENCE_CALLSILENCE).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                if (!Storage.permitted(getContext(), PERMISSIONS)) {
                    if (!Storage.permitted(SettingsFragment.this, PERMISSIONS, RESULT_PHONE))
                        return false;
                }
                return true;
            }
        });

        OptimizationPreferenceCompat optimization = (OptimizationPreferenceCompat) findPreference(HourlyApplication.PREFERENCE_OPTIMIZATION);
        optimization.enable(AlarmService.class);
        receiver = new PersistentService.SettingsReceiver(new Intent(getContext(), AlarmService.class), HourlyApplication.PREFERENCE_OPTIMIZATION);
        receiver.register(getContext());

        SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.registerOnSharedPreferenceChangeListener(this);

        PreferenceGroup haptic = (PreferenceGroup) findPreference("haptic");
        boolean empty = true;
        for (int i = 0; i < haptic.getPreferenceCount(); i++) {
            if (haptic.getPreference(i).isVisible())
                empty = false;
        }
        if (empty)
            haptic.setVisible(false);
    }

    void setPhone() {
        SwitchPreferenceCompat s = (SwitchPreferenceCompat) findPreference(HourlyApplication.PREFERENCE_CALLSILENCE);
        s.setChecked(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RESULT_PHONE:
                if (Storage.permitted(getContext(), PERMISSIONS))
                    setPhone();
                else
                    Toast.makeText(getActivity(), R.string.not_permitted, Toast.LENGTH_SHORT).show();
                break;
        }
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
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ContentFrameLayout.LayoutParams.WRAP_CONTENT, ContentFrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
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

        receiver.unregister(getContext());

        SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals(HourlyApplication.PREFERENCE_ALARM)) {
            ((SwitchPreferenceCompat) findPreference(HourlyApplication.PREFERENCE_ALARM)).setChecked(sharedPreferences.getBoolean(HourlyApplication.PREFERENCE_ALARM, false));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        OptimizationPreferenceCompat optimization = (OptimizationPreferenceCompat) findPreference(HourlyApplication.PREFERENCE_OPTIMIZATION);
        optimization.onResume();
    }
}
