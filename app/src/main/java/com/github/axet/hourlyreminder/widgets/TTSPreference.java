package com.github.axet.hourlyreminder.widgets;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

public class TTSPreference extends EditTextPreference {

    public static long testTime(int h, int m) {
        Calendar time10 = Calendar.getInstance();
        time10.set(Calendar.HOUR_OF_DAY, h);
        time10.set(Calendar.MINUTE, m);
        return time10.getTimeInMillis();
    }

    public static class TTSConfig {
        public boolean def;
        public String time12h00;
        public String time24h00;
        public String time12h01;
        public String time24h01;

        public Locale locale;

        public TTSConfig() {

        }

        public TTSConfig(TTSConfig c) {
            locale = c.locale;
            def = c.def;
            time12h00 = c.time12h00;
            time24h00 = c.time24h00;
            time12h01 = c.time12h01;
            time24h01 = c.time24h01;
        }

        public TTSConfig(Locale locale) {
            this.locale = locale;
        }

        public void def(Context context) {
            def = true;
            time12h00 = HourlyApplication.getString(context, locale, R.string.speak_time_12h00);
            time12h01 = HourlyApplication.getString(context, locale, R.string.speak_time_12h01);
            time24h00 = HourlyApplication.getString(context, locale, R.string.speak_time_24h00);
            time24h01 = HourlyApplication.getString(context, locale, R.string.speak_time_24h01);
        }

        public void load(String s) {
            try {
                load(new JSONObject(s));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public void load(JSONObject o) {
            try {
                def = o.getBoolean("def");
                time12h00 = o.getString("time12h00");
                time24h00 = o.getString("time24h00");
                time12h01 = o.getString("time12h01");
                time24h01 = o.getString("time24h01");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public JSONObject save() {
            JSONObject o = new JSONObject();
            try {
                o.put("def", def);
                o.put("time12h00", time12h00);
                o.put("time24h00", time24h00);
                o.put("time12h01", time12h01);
                o.put("time24h01", time24h01);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return o;
        }
    }

    public static class TTSPrefDialogFragment extends PreferenceDialogFragmentCompat {
        private boolean mPreferenceChanged;
        TTSConfig config;
        CheckBox def;
        CheckBox show12h24h;
        TextView tts12h00;
        TextView tts12h00text;
        TextWatcher tts12h00tw;
        View tts12h00play;
        TextView tts24h00;
        TextView tts24h00text;
        TextWatcher tts24h00tw;
        View tts24h00play;
        TextView tts12h01;
        TextView tts12h01text;
        TextWatcher tts12h01tw;
        View tts12h01play;
        TextView tts24h01;
        TextView tts24h01text;
        TextWatcher tts24h01tw;
        View tts24h01play;
        Sound sound;

        public TTSPrefDialogFragment() {
        }

        public static void show(Fragment f, String key) {
            TTSPreference.TTSPrefDialogFragment d = TTSPreference.TTSPrefDialogFragment.newInstance(key);
            d.setTargetFragment(f, 0);
            d.show(f.getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
        }

        public static TTSPrefDialogFragment newInstance(String key) {
            TTSPrefDialogFragment fragment = new TTSPrefDialogFragment();
            Bundle b = new Bundle(1);
            b.putString("key", key);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            sound = new Sound(getContext()) {
                @Override
                public void onInit() {
                    super.onInit();
                    update();
                }
            };

            String values;
            if (savedInstanceState != null) {
                values = savedInstanceState.getString("values");
                mPreferenceChanged = savedInstanceState.getBoolean("changed");
            } else {
                TTSPreference preference = (TTSPreference) getPreference();
                values = preference.getValues();
            }
            try {
                Locale locale = sound.getUserLocale();
                config = new TTSConfig(locale);
                if (values == null || values.isEmpty()) {
                    config.def(getContext());
                } else {
                    config.load(new JSONObject(values));
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString("values", config.save().toString());
            outState.putBoolean("changed", mPreferenceChanged);
        }

        @Override
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            final Context context = getActivity();

            final TTSPreference preference = (TTSPreference) getPreference();

            LayoutInflater inflater = LayoutInflater.from(context);
            final View view = inflater.inflate(R.layout.tts, null, false);

            def = (CheckBox) view.findViewById(R.id.tts_def);
            show12h24h = (CheckBox) view.findViewById(R.id.tts_show12h24h);
            tts12h00 = (TextView) view.findViewById(R.id.tts_12h_00);
            tts12h00text = (TextView) view.findViewById(R.id.tts_12h_00_text);
            tts24h00 = (TextView) view.findViewById(R.id.tts_24h_00);
            tts24h00text = (TextView) view.findViewById(R.id.tts_24h_00_text);
            tts12h01 = (TextView) view.findViewById(R.id.tts_12h_01);
            tts12h01text = (TextView) view.findViewById(R.id.tts_12h_01_text);
            tts24h01 = (TextView) view.findViewById(R.id.tts_24h_01);
            tts24h01text = (TextView) view.findViewById(R.id.tts_24h_01_text);

            tts12h00.addTextChangedListener(tts12h00tw = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    mPreferenceChanged = true;
                    config.time12h00 = tts12h00.getText().toString();
                    tts12h00text.setText(sound.speakText(10, 0, config.locale, tts12h00.getText().toString(), false));
                }
            });
            tts12h00play = view.findViewById(R.id.tts_12h_00_play);
            tts12h00play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playSpeech(10, 0, tts12h00, false);
                }
            });

            tts12h01.addTextChangedListener(tts12h01tw = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    mPreferenceChanged = true;
                    config.time12h01 = tts12h01.getText().toString();
                    tts12h01text.setText(sound.speakText(10, 5, config.locale, tts12h01.getText().toString(), false));
                }
            });
            tts12h01play = view.findViewById(R.id.tts_12h_01_play);
            tts12h01play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playSpeech(10, 5, tts12h01, false);
                }
            });

            tts24h00.addTextChangedListener(tts24h00tw = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    mPreferenceChanged = true;
                    config.time24h00 = tts24h00.getText().toString();
                    tts24h00text.setText(sound.speakText(16, 0, config.locale, tts24h00.getText().toString(), true));
                }
            });
            tts24h00play = view.findViewById(R.id.tts_24h_00_play);
            tts24h00play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playSpeech(16, 5, tts24h00, true);
                }
            });

            tts24h01.addTextChangedListener(tts24h01tw = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    mPreferenceChanged = true;
                    config.time24h01 = tts24h01.getText().toString();
                    tts24h01text.setText(sound.speakText(16, 5, config.locale, tts24h01.getText().toString(), true));
                }
            });
            tts24h01play = view.findViewById(R.id.tts_24h_01_play);
            tts24h01play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playSpeech(16, 5, tts24h01, true);
                }
            });

            def.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPreferenceChanged = true;
                    config.def = !config.def;
                    update();
                }
            });
            show12h24h.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    update();
                }
            });

            update();

            builder.setView(view);

            builder.setNeutralButton(R.string.reset, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
        }

        void update() {
            int s12;
            int s24;

            if (DateFormat.is24HourFormat(getContext())) {
                show12h24h.setText(R.string.show_12h);
                s12 = show12h24h.isChecked() ? View.VISIBLE : View.GONE;
                s24 = View.VISIBLE;
            } else {
                show12h24h.setText(R.string.show_24h);
                s12 = View.VISIBLE;
                s24 = show12h24h.isChecked() ? View.VISIBLE : View.GONE;
            }

            tts12h00.setVisibility(s12);
            tts12h00text.setVisibility(s12);
            tts12h00play.setVisibility(s12);
            tts24h00.setVisibility(s24);
            tts24h00text.setVisibility(s24);
            tts24h00play.setVisibility(s24);
            tts12h01.setVisibility(s12);
            tts12h01text.setVisibility(s12);
            tts12h01play.setVisibility(s12);
            tts24h01.setVisibility(s24);
            tts24h01text.setVisibility(s24);
            tts24h01play.setVisibility(s24);

            def.setChecked(config.def);
            tts12h00.setEnabled(!config.def);
            tts24h00.setEnabled(!config.def);
            tts12h01.setEnabled(!config.def);
            tts24h01.setEnabled(!config.def);

            tts12h00.removeTextChangedListener(tts12h00tw);
            tts12h01.removeTextChangedListener(tts12h01tw);
            tts24h00.removeTextChangedListener(tts24h00tw);
            tts24h01.removeTextChangedListener(tts24h01tw);

            TTSConfig c = getShowConfig();

            tts12h00.setText(c.time12h00);
            tts12h01.setText(c.time12h01);
            tts24h00.setText(c.time24h00);
            tts24h01.setText(c.time24h01);

            tts12h00.addTextChangedListener(tts12h00tw);
            tts12h01.addTextChangedListener(tts12h01tw);
            tts24h00.addTextChangedListener(tts24h00tw);
            tts24h01.addTextChangedListener(tts24h01tw);

            tts12h00text.setText(sound.speakText(10, 0, c.locale, tts12h00.getText().toString(), false));
            tts12h01text.setText(sound.speakText(10, 5, c.locale, tts12h01.getText().toString(), false));
            tts24h00text.setText(sound.speakText(16, 0, c.locale, tts24h00.getText().toString(), true));
            tts24h01text.setText(sound.speakText(16, 5, c.locale, tts24h01.getText().toString(), true));
        }

        public TTSConfig getShowConfig() {
            TTSConfig c = new TTSConfig(config);
            if (c.def) { // if def = load speak locale
                c.locale = sound.getTTSLocale();
                if (c.locale == null)
                    c.locale = sound.getUserLocale();
                c.def(getContext());
            }
            return c;
        }

        public void playSpeech(int hour, int min, TextView t, boolean is24) {
            TTSConfig c = getShowConfig();
            sound.playSpeech(c.locale, sound.speakText(hour, min, c.locale, t.getText().toString(), is24));
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final AlertDialog d = (AlertDialog) super.onCreateDialog(savedInstanceState);
            d.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    Button b = d.getButton(AlertDialog.BUTTON_NEUTRAL);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            config.locale = sound.getUserLocale();
                            config.def(getContext()); // load user selected language
                            update();
                        }
                    });
                }
            });
            return d;
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            TTSPreference preference = (TTSPreference) getPreference();
            if (positiveResult && mPreferenceChanged) {
                if (preference.callChangeListener(null)) {
                    preference.setValues(config.save().toString());
                }
            }
            mPreferenceChanged = false;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (sound != null) {
                sound.close();
                sound = null;
            }
        }
    }

    public TTSPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TTSPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TTSPreference(Context context) {
        this(context, null);
    }

    public String getValues() {
        return getText();
    }

    public void setValues(String s) {
        setText(s);
    }
}
