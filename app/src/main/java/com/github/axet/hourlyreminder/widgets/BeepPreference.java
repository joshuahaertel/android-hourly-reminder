package com.github.axet.hourlyreminder.widgets;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.app.Sound;

public class BeepPreference extends EditTextPreference {

    public static class BeepConfig {
        public static String DEFAULT = "1800:100";

        public int value_f;
        public int value_l;

        public void reset() {
            value_f = 1800;
            value_l = 100;
        }

        public void load(String values) {
            reset();

            String[] v = values.split(":");
            if (v.length > 0) {
                try {
                    value_f = Integer.parseInt(v[0]);
                } catch (NumberFormatException e) {
                }
            }
            if (v.length > 1) {
                try {
                    value_l = Integer.parseInt(v[1]);
                } catch (NumberFormatException e) {
                }
            }
        }

        public String save() {
            return value_f + ":" + value_l;
        }
    }

    public static class BeepPrefDialogFragment extends PreferenceDialogFragmentCompat {
        private boolean mPreferenceChanged;

        Sound sound;

        TextView len;
        TextView freq;
        SeekBar seek;
        boolean ignore = false;

        int fmin = 20;
        int fmax = 20000;

        BeepConfig config = new BeepConfig();

        public BeepPrefDialogFragment() {
        }

        public static BeepPrefDialogFragment newInstance(String key) {
            BeepPrefDialogFragment fragment = new BeepPrefDialogFragment();
            Bundle b = new Bundle(1);
            b.putString("key", key);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            sound = new Sound(getActivity());

            if (savedInstanceState != null) {
                String values = savedInstanceState.getString("values");
                config.load(values);
                mPreferenceChanged = savedInstanceState.getBoolean("changed");
            } else {
                BeepPreference preference = (BeepPreference) getPreference();
                String values = preference.getValues();
                config.load(values);
            }
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            super.onSaveInstanceState(outState);

            outState.putString("values", config.save());
            outState.putBoolean("changed", mPreferenceChanged);
        }

        @Override
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);

            Context context = getActivity();

            final BeepPreference preference = (BeepPreference) getPreference();

            LayoutInflater inflater = LayoutInflater.from(context);
            final View view = inflater.inflate(R.layout.beep, null, false);
            freq = (TextView) view.findViewById(R.id.beep_freq);
            len = (TextView) view.findViewById(R.id.beep_length);
            seek = (SeekBar) view.findViewById(R.id.beep_seekbar);
            final BeepView beepView = (BeepView) view.findViewById(R.id.beep_view);
            View reset = view.findViewById(R.id.beep_reset);

            reset.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    config.reset();
                    update();
                }
            });

            len.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    int l = getLen();

                    if (l > 5000) {
                        s.clear();
                        s.append("" + 5000);
                        l = 5000;
                    }

                    config.value_l = l;
                    mPreferenceChanged = true;
                }
            });

            freq.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (ignore)
                        return;

                    int f = getFreq();
                    if (f > fmax) {
                        s.clear();
                        s.append("" + fmax);
                        f = fmax;
                    }

                    config.value_f = f;
                    mPreferenceChanged = true;

                    int p = f2p(config.value_f);
                    ignore = true;
                    seek.setProgress(p);
                    ignore = false;
                }
            });

            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int f = p2f(progress);
                    beepView.setCoeff(f / 400f);
                    if (ignore)
                        return;

                    config.value_f = p2f(progress);
                    mPreferenceChanged = true;

                    ignore = true;
                    BeepPrefDialogFragment.this.freq.setText("" + config.value_f);
                    ignore = false;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            update();

            builder.setView(view);

            builder.setNeutralButton(R.string.play, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
        }

        void update() {
            freq.setText("" + config.value_f);
            len.setText("" + config.value_l);
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
                            playSound();
                        }
                    });
                }
            });
            return d;
        }

        // progress2freq
        int p2f(int progress) {
            int max = 100 + 1;

            float log1 = (float) (Math.log(max - progress) / Math.log(max));
            float v = 1 - log1;
            return (int) (fmin + v * (fmax - fmin));
        }

        // freq2progress
        int f2p(int freq) {
            int max = 100 + 1;

            float p = (freq - fmin) / (float) fmax;
            float log1 = 1 - p;
            int mp = (int) Math.exp(log1 * Math.log(max)); // max - progress
            int progress = max - mp;
            return progress;
        }


        void playSound() {
            int f = getFreq();
            int l = getLen();
            sound.playBeep(Sound.generateTone(sound.getSoundChannel(), f, l), null);
        }

        int getFreq() {
            String t = freq.getText().toString();
            if (t.isEmpty())
                t = "20";
            return Integer.valueOf(t);
        }

        int getLen() {
            String t = len.getText().toString();
            if (t.isEmpty())
                t = "50";
            return Integer.valueOf(t);
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            BeepPreference preference = (BeepPreference) getPreference();
            if (positiveResult && this.mPreferenceChanged) {
                if (preference.callChangeListener(null)) {
                    preference.setValues(config.save());
                }
            }
            this.mPreferenceChanged = false;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            sound.close();
        }
    }

    public BeepPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public BeepPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BeepPreference(Context context) {
        this(context, null);
    }

    public String getValues() {
        return getText();
    }

    public void setValues(String s) {
        setText(s);
    }

}
