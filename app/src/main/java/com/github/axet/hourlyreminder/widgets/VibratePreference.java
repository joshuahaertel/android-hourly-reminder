package com.github.axet.hourlyreminder.widgets;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.support.v7.widget.SwitchCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class VibratePreference extends SwitchPreferenceCompat {
    public static final String[] PERMISSIONS_V = new String[]{Manifest.permission.VIBRATE};

    public static int DEFAULT_VALUE_INDEX = 1;

    ArrayAdapter<CharSequence> values = ArrayAdapter.createFromResource(getContext(), R.array.patterns_values, android.R.layout.simple_spinner_item);

    AlertDialog d;

    Config config;

    SwitchCompat remSw;
    ImageView remPlay;
    Spinner rem;
    Spinner ala;
    SwitchCompat alaSw;
    ImageView alaPlay;

    Sound sound;
    Handler handler = new Handler();

    String loaded;

    Runnable remStop = new Runnable() {
        @Override
        public void run() {
            stop();
            update();
        }
    };

    public static Config loadConfig(Context context, String key) {
        SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(context);
        ArrayAdapter<CharSequence> values = ArrayAdapter.createFromResource(context, R.array.patterns_values, android.R.layout.simple_spinner_item);
        try {
            String json = shared.getString(key, "");
            if (json.isEmpty()) {
                return new Config(false, values.getItem(DEFAULT_VALUE_INDEX).toString());
            }
            return new Config(json);
        } catch (ClassCastException e) {
            boolean b = shared.getBoolean(key, false);
            return new Config(b, values.getItem(DEFAULT_VALUE_INDEX).toString());
        }
    }

    public static boolean hasVibrator(Context context) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null)
            return false;
        if (Build.VERSION.SDK_INT < 11)
            return true;
        return v.hasVibrator();
    }

    public static void loadDropdown(Spinner rem, int pos) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(rem.getContext(), R.array.patterns_text, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rem.setAdapter(adapter);
        rem.setSelection(pos);
    }

    public static class Config {
        public boolean alarms;
        public String alarmsPattern;
        public boolean reminders;
        public String remindersPattern;

        public Config(String json) {
            try {
                JSONObject j = new JSONObject(json);
                reminders = j.getBoolean("reminders");
                remindersPattern = j.getString("reminders_pattern");
                alarms = j.getBoolean("alarms");
                alarmsPattern = j.getString("alarms_pattern");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public Config(boolean b, String p) {
            reminders = b;
            remindersPattern = p;
            alarms = b;
            alarmsPattern = p;
        }

        public JSONObject save() {
            try {
                JSONObject json = new JSONObject();
                json.put("reminders", reminders);
                json.put("reminders_pattern", remindersPattern);
                json.put("alarms", alarms);
                json.put("alarms_pattern", alarmsPattern);
                return json;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean isChecked() {
            return alarms || reminders;
        }
    }

    public VibratePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public VibratePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public VibratePreference(Context context) {
        this(context, null);
        create();
    }

    public void create() {
        if (!hasVibrator(getContext())) {
            setVisible(false);
        }
    }

    public void onResume() {
        setChecked(config.isChecked());
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        read();
        onResume();
    }

    int findPos(String pattern) {
        for (int i = 0; i < values.getCount(); i++) {
            if (values.getItem(i).equals(pattern)) {
                return i;
            }
        }
        return -1;
    }

    void read() {
        config = loadConfig(getContext(), HourlyApplication.PREFERENCE_VIBRATE);
    }

    void save() {
        config.reminders = remSw.isChecked();
        config.remindersPattern = values.getItem(rem.getSelectedItemPosition()).toString();
        config.alarms = alaSw.isChecked();
        config.alarmsPattern = values.getItem(ala.getSelectedItemPosition()).toString();
    }

    @Override
    protected void onClick() {
        if (d != null)
            return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(R.layout.vibrate);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                d = null;
                stop();
                if (sound != null) {
                    sound.close();
                    sound = null;
                }
            }
        });
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                save();
                SharedPreferences shared = getSharedPreferences();
                SharedPreferences.Editor edit = shared.edit();
                edit.putString(HourlyApplication.PREFERENCE_VIBRATE, config.save().toString());
                edit.commit();
                onResume();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        sound = new Sound(getContext());
        d = builder.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                read();

                stop();

                Window v = d.getWindow();

                remPlay = (ImageView) v.findViewById(R.id.reminders_play);
                remSw = (SwitchCompat) v.findViewById(R.id.reminders_switch);
                remSw.setChecked(config.reminders);
                rem = (Spinner) v.findViewById(R.id.spinner_reminders);
                loadDropdown(rem, findPos(config.remindersPattern));

                alaPlay = (ImageView) v.findViewById(R.id.alarms_play);
                alaSw = (SwitchCompat) v.findViewById(R.id.alarms_switch);
                alaSw.setChecked(config.alarms);
                ala = (Spinner) v.findViewById(R.id.spinner_alarms);
                loadDropdown(ala, findPos(config.alarmsPattern));

                update();
            }
        });
        d.show();
    }

    void update() {
        if (loaded == config.remindersPattern) {
            remPlay.setImageResource(R.drawable.ic_stop_black_24dp);
            remPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stop();
                    update();
                }
            });
        } else {
            remPlay.setImageResource(R.drawable.ic_play_arrow_black_24dp);
            remPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    save();
                    stop();
                    long l = start(config.remindersPattern, -1);
                    update();
                    handler.postDelayed(remStop, l);
                }
            });
        }

        if (loaded == config.alarmsPattern) {
            alaPlay.setImageResource(R.drawable.ic_stop_black_24dp);
            alaPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stop();
                    update();
                }
            });
        } else {
            alaPlay.setImageResource(R.drawable.ic_play_arrow_black_24dp);
            alaPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    save();
                    stop();
                    start(config.alarmsPattern, 0);
                    update();
                }
            });
        }
    }

    long start(String pattern, int r) {
        loaded = pattern;
        long[] p = sound.vibrateStart(pattern, r);
        return Sound.patternLength(p);
    }

    void stop() {
        loaded = null;
        sound.vibrateStop();
        handler.removeCallbacks(remStop);
    }
}
