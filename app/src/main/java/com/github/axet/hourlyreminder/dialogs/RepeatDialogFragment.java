package com.github.axet.hourlyreminder.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.CircularSeekBar;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.hourlyreminder.R;

public class RepeatDialogFragment extends DialogFragment {
    View v;

    TextView textint;
    TextView text;
    CircularSeekBar seekBar;

    View v1hour;
    View v30min;
    View v20min;
    View v15min;
    View v10min;
    View v5min;
    View[] vv;
    String[] ss;
    int[] mm = new int[]{60, 30, 20, 15, 10, 5};

    int mins;

    RadioButton buttonMins; // repeat every specified minute interval
    RadioButton buttonHourly; // once, at specified time

    boolean ok;

    public class Result implements DialogInterface {
        public boolean ok;
        public int index;
        public int mins;

        public Result() {
            index = getArguments().getInt("index");
            ok = RepeatDialogFragment.this.ok;
            if (buttonHourly.isChecked())
                mins = -RepeatDialogFragment.this.mins;
            else
                mins = RepeatDialogFragment.this.mins;
            if (mins == 0 || mins == -60)
                mins = 60;
        }

        @Override
        public void cancel() {
        }

        @Override
        public void dismiss() {
        }
    }

    public RepeatDialogFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mins = savedInstanceState.getInt("mins");
        } else {
            mins = getArguments().getInt("mins");
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (buttonHourly.isChecked()) {
            outState.putInt("mins", -mins);
        } else {
            outState.putInt("mins", mins);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog d = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.every)
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
        return null; // must be null or Illegal state exception
    }

    View createView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.repeat, container, false);
        v = view;

        textint = (TextView) view.findViewById(R.id.repeat_intervals);

        text = (TextView) view.findViewById(R.id.repeat_text);

        v1hour = view.findViewById(R.id.repeat_1_hour);
        v30min = view.findViewById(R.id.repeat_30_min);
        v15min = view.findViewById(R.id.repeat_15_min);
        v20min = view.findViewById(R.id.repeat_20_min);
        v10min = view.findViewById(R.id.repeat_10_min);
        v5min = view.findViewById(R.id.repeat_5_min);

        buttonHourly = (RadioButton) view.findViewById(R.id.repeat_hourly);
        buttonMins = (RadioButton) view.findViewById(R.id.repeat_mins);

        buttonHourly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateText();
            }
        });
        buttonMins.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateText();
            }
        });

        if (mins < 0) {
            buttonHourly.setChecked(true);
            mins = -mins;
        } else {
            buttonMins.setChecked(true);
        }

        seekBar = (CircularSeekBar) view.findViewById(R.id.repeat_seekbar);
        seekBar.setOnSeekBarChangeListener(new CircularSeekBar.OnCircularSeekBarChangeListener() {
            @Override
            public void onProgressChanged(CircularSeekBar circularSeekBar, int progress, boolean fromUser) {
                mins = progress;
                updateText();
            }

            @Override
            public void onStopTrackingTouch(CircularSeekBar seekBar) {

            }

            @Override
            public void onStartTrackingTouch(CircularSeekBar seekBar) {

            }
        });

        vv = new View[]{v1hour, v30min, v20min, v15min, v10min, v5min};
        String h = getString(R.string.hour_symbol);
        String m = getString(R.string.min_symbol);
        ss = new String[]{"1" + h, "30" + m, "20" + m, "15" + m, "10" + m, "5" + m};

        updateText();
        updateProgress();

        return view;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Activity a = getActivity();
        if (a instanceof DialogInterface.OnDismissListener)
            ((DialogInterface.OnDismissListener) a).onDismiss(new Result());
    }

    void updateText() {
        int color = ThemeUtils.getThemeColor(getContext(), android.R.attr.colorForeground);
        final int accent = ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent);
        if (mins == 0 || mins == 60) {
            text.setText(R.string.hourly);
        } else {
            String pref = buttonHourly.isChecked() ? ":" : "";
            text.setText(pref + mins + " " + getString(R.string.min));
        }
        for (int i = 0; i < vv.length; i++) {
            View v = vv[i];
            if (buttonMins.isChecked()) {
                textint.setVisibility(View.VISIBLE);
                v.setVisibility(View.VISIBLE);
                setRepeatColor(v, color);
                TextView t = (TextView) v.findViewById(R.id.alarm_every_text);
                t.setText(ss[i]);
                final int m = i;
                v.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mins = mm[m];
                        updateProgress();
                        updateText();
                    }
                });
            } else {
                textint.setVisibility(View.INVISIBLE);
                v.setVisibility(View.INVISIBLE);
            }
        }
        switch (mins) {
            case 0:
            case 60:
                setRepeatColor(v1hour, accent);
                break;
            case 30:
                setRepeatColor(v30min, accent);
                break;
            case 20:
                setRepeatColor(v20min, accent);
                break;
            case 15:
                setRepeatColor(v15min, accent);
                break;
            case 10:
                setRepeatColor(v10min, accent);
                break;
            case 5:
                setRepeatColor(v5min, accent);
                break;
        }
    }

    void setRepeatColor(View v, int c) {
        ImageView i = (ImageView) v.findViewById(R.id.alarm_every_image);
        TextView t = (TextView) v.findViewById(R.id.alarm_every_text);
        if (t != null)
            t.setTextColor(c);
        if (i != null)
            i.setColorFilter(c);
    }

    void updateProgress() {
        if (mins == 60)
            seekBar.setProgress(0);
        else
            seekBar.setProgress(mins);
    }
}
