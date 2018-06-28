package com.github.axet.hourlyreminder.widgets;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
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
import com.github.axet.hourlyreminder.app.Toast;

public class FlashPreference extends SwitchPreferenceCompat {
    public static final String TAG = FlashPreference.class.getSimpleName();

    public static final String[] PERMISSIONS_V = new String[]{Manifest.permission.VIBRATE};

    ArrayAdapter<CharSequence> values = ArrayAdapter.createFromResource(getContext(), R.array.patterns_values, android.R.layout.simple_spinner_item);

    AlertDialog d;

    VibratePreference.Config config;

    SwitchCompat remSw;
    ImageView remPlay;
    Spinner rem;
    Spinner ala;
    SwitchCompat alaSw;
    ImageView alaPlay;

    String loaded;
    Flash flash;
    Handler handler = new Handler();

    Runnable remStop = new Runnable() {
        @Override
        public void run() {
            stop();
            update();
        }
    };

    public static boolean supported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    public static class Flash {
        Context context;
        Camera cam;
        CameraManager camManager;
        Handler handler = new Handler();
        long[] pattern;
        int index;
        int repeat; //  the index into pattern at which to repeat, or -1 if you don't want to repeat
        Runnable update = new Runnable() {
            @Override
            public void run() {
                update();
            }
        };

        public void on() {
            if (cam != null)
                return;
            if (camManager != null)
                return;

            if (Build.VERSION.SDK_INT >= 23) {
                camManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                try {
                    String cameraId = camManager.getCameraIdList()[0];
                    camManager.setTorchMode(cameraId, true);
                    return;
                } catch (Exception e) { // catching CameraAccessException, cause VerifyError on old devices
                    throw new RuntimeException(e);
                }
            }

            cam = Camera.open();
            if (cam != null) {
                Camera.Parameters p = cam.getParameters();
                p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                cam.setParameters(p);
                cam.startPreview();
            }
        }

        public void off() {
            if (cam != null) {
                cam.stopPreview();
                cam.release();
                cam = null;
            }

            if (Build.VERSION.SDK_INT >= 23) {
                try {
                    if (camManager != null) {
                        String cameraId = camManager.getCameraIdList()[0]; // Usually front camera is at 0 position.
                        camManager.setTorchMode(cameraId, false);
                        camManager = null;
                    }
                } catch (Exception e) { // catching CameraAccessException, cause VerifyError on old devices
                    throw new RuntimeException(e);
                }
            }
        }

        public Flash(Context context) {
            this.context = context;
        }

        public void start(String pattern) {
            start(pattern, -1);
        }

        public long[] start(String pattern, int r) {
            long[] p = Sound.patternLoad(pattern);
            start(p, r);
            return p;
        }

        public void start(long[] pattern, int repeat) {
            this.pattern = pattern;
            this.index = 0;
            this.repeat = repeat;
            update();
        }

        void update() {
            if (index % 2 == 0)
                off();
            else
                on();
            long d = pattern[index];
            index++;
            boolean loop = false;
            if (index >= pattern.length) {
                index = repeat;
                loop = true;
            }
            if (!loop || repeat >= 0)
                handler.postDelayed(update, d);
        }

        public void stop() {
            off();
            handler.removeCallbacks(update);
        }
    }


    public FlashPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    public FlashPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public FlashPreference(Context context) {
        this(context, null);
        create();
    }

    public void create() {
        if (!supported(getContext())) {
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
        config = VibratePreference.loadConfig(getContext(), HourlyApplication.PREFERENCE_FLASH);
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
        builder.setView(R.layout.flash);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                d = null;
                stop();
            }
        });
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                save();
                SharedPreferences shared = getSharedPreferences();
                SharedPreferences.Editor edit = shared.edit();
                edit.putString(HourlyApplication.PREFERENCE_FLASH, config.save().toString());
                edit.commit();
                onResume();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        d = builder.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                read();

                flash = new Flash(getContext());
                stop();

                Window v = d.getWindow();

                remPlay = (ImageView) v.findViewById(R.id.reminders_play);
                remSw = (SwitchCompat) v.findViewById(R.id.reminders_switch);
                remSw.setChecked(config.reminders);
                rem = (Spinner) v.findViewById(R.id.spinner_reminders);
                VibratePreference.loadDropdown(rem, findPos(config.remindersPattern));

                alaPlay = (ImageView) v.findViewById(R.id.alarms_play);
                alaSw = (SwitchCompat) v.findViewById(R.id.alarms_switch);
                alaSw.setChecked(config.alarms);
                ala = (Spinner) v.findViewById(R.id.spinner_alarms);
                VibratePreference.loadDropdown(ala, findPos(config.alarmsPattern));

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
        try {
            long[] p = flash.start(pattern, r);
            return Sound.patternLength(p);
        } catch (RuntimeException e) {
            Toast.Error(getContext(), "Unable to use flashlight", e);
            return -1;
        }
    }

    void stop() {
        loaded = null;
        try {
            flash.stop();
        } catch (RuntimeException e) {
            Toast.Error(getContext(), "Unable to use flashlight", e);
        }
        handler.removeCallbacks(remStop);
    }
}
