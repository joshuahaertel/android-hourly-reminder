package com.github.axet.hourlyreminder.fragments;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.animations.MarginAnimation;
import com.github.axet.androidlibrary.animations.RemoveItemAnimation;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.animations.AlarmAnimation;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.app.SoundConfig;
import com.github.axet.hourlyreminder.app.Storage;
import com.github.axet.hourlyreminder.basics.Alarm;
import com.github.axet.hourlyreminder.basics.Week;
import com.github.axet.hourlyreminder.basics.WeekSet;

import java.io.File;
import java.util.ArrayList;

public class WeekSetFragment extends Fragment implements ListAdapter, AbsListView.OnScrollListener, SharedPreferences.OnSharedPreferenceChangeListener {
    static final int TYPE_COLLAPSED = 0;
    static final int TYPE_EXPANDED = 1;
    static final int TYPE_DELETED = 2;

    final int[] ALL = {TYPE_COLLAPSED, TYPE_EXPANDED};

    WeekSet fragmentRequestRingtone;

    ListView list;

    ArrayList<DataSetObserver> listeners = new ArrayList<>();
    long selected = -1;
    int scrollState;
    boolean boxAnimate;
    Handler handler;
    // preview ringtone
    boolean preview;
    View alarmRingtonePlay;
    Sound sound;
    Storage storage;

    int startweek = 0;

    public WeekSetFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler();

        sound = new Sound(getActivity());

        storage = new Storage(getActivity());

        updateStartWeek();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.registerOnSharedPreferenceChangeListener(this);

        if (savedInstanceState != null) {
            selected = savedInstanceState.getLong("selected");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putLong("selected", selected);
    }

    int getPosition(long id) {
        return -1;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        boxAnimate = true;
        if (key.equals(HourlyApplication.PREFERENCE_WEEKSTART))
            updateStartWeek();
    }

    void updateStartWeek() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        String s = prefs.getString(HourlyApplication.PREFERENCE_WEEKSTART, "");
        for (int i = 0; i < Week.DAYS.length; i++) {
            if (s.equals(getString(Week.DAYS[i]))) {
                startweek = i;
                break;
            }
        }
    }

    String fallbackUri(Uri uri) {
        if (uri != null) {
            return uri.toString();
        } else {
            return Alarm.DEFAULT_ALARM.toString();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (fragmentRequestRingtone == null)
            return;

        if (resultCode != Activity.RESULT_OK) {
            fragmentRequestRingtone = null;
            return;
        }

        if (requestCode == 0) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            fragmentRequestRingtone.ringtoneValue = fallbackUri(uri);
            save(fragmentRequestRingtone);
            fragmentRequestRingtone = null;
            return;
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.scrollState = scrollState;
        boxAnimate = true;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        listeners.add(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        listeners.remove(observer);
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return -1;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    static boolean checkboxAnimate(CheckBox checkbox, View view) {
        boolean animate;
        Animation a = view.getAnimation();
        if (a != null && !a.hasEnded())
            return true;
        if (checkbox.isChecked()) {
            animate = view.getVisibility() != View.VISIBLE;
        } else {
            animate = view.getVisibility() == View.VISIBLE;
        }
        return animate;
    }


    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.alarm, parent, false);
            convertView.setTag(-1);
        }

        if ((int) convertView.getTag() == TYPE_DELETED) {
            RemoveItemAnimation.restore(convertView.findViewById(R.id.alarm_base));
            convertView.setTag(-1);
        }

        final WeekSet a = (WeekSet) getItem(position);

        final View alarmRingtonePlay = convertView.findViewById(R.id.alarm_ringtone_play);
        alarmRingtonePlay.clearAnimation();

        if (selected == a.id) {
            fillDetailed(convertView, a, boxAnimate);

            final CheckBox weekdays = (CheckBox) convertView.findViewById(R.id.alarm_week_days);
            final LinearLayout weekdaysValues = (LinearLayout) convertView.findViewById(R.id.alarm_week);
            final CheckBox alarmRingtone = (CheckBox) convertView.findViewById(R.id.alarm_ringtone);
            final View alarmRingtoneLayout = convertView.findViewById(R.id.alarm_ringtone_layout);

            AlarmAnimation.apply(list, convertView, true, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_COLLAPSED);

            MarginAnimation.apply(weekdaysValues, weekdays.isChecked(), scrollState == SCROLL_STATE_IDLE &&
                    (int) convertView.getTag() == TYPE_EXPANDED &&
                    checkboxAnimate(weekdays, weekdaysValues));

            MarginAnimation.apply(alarmRingtoneLayout, alarmRingtone.isChecked(), scrollState == SCROLL_STATE_IDLE &&
                    (int) convertView.getTag() == TYPE_EXPANDED &&
                    checkboxAnimate(alarmRingtone, alarmRingtoneLayout));

            convertView.setTag(TYPE_EXPANDED);

            return convertView;
        } else {
            fillCompact(convertView, a, boxAnimate);

            AlarmAnimation.apply(list, convertView, false, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_EXPANDED);

            convertView.setTag(TYPE_COLLAPSED);

            return convertView;
        }
    }

    void select(long id) {
        // stop sound preview when detailed view closed.
        if (preview) {
            sound.playerClose();
            preview = false;
        }
        selected = id;
        changed();
    }

    void save(WeekSet a) {
    }

    @Override
    public int getItemViewType(int position) {
        return TYPE_COLLAPSED;
    }

    @Override
    public int getViewTypeCount() {
        return ALL.length;
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }

    public void remove(WeekSet a) {
        boxAnimate = false;
    }

    void changed() {
        for (DataSetObserver l : listeners) {
            l.onChanged();
        }
    }

    void setWeek(WeekSet a, int week, boolean c) {
        a.setWeek(week, c);
        save(a);
    }

    void previewCancel() {
        if (alarmRingtonePlay != null) {
            alarmRingtonePlay.clearAnimation();
            alarmRingtonePlay = null;
        }
        sound.vibrateStop();
        sound.playerClose();
        preview = false;
    }

    Sound.Silenced playPreview(WeekSet a) {
        return SoundConfig.Silenced.NONE;
    }

    public void fillDetailed(final View view, final WeekSet a, boolean animate) {
        final SwitchCompat enable = (SwitchCompat) view.findViewById(R.id.alarm_enable);
        enable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setEnable(a, enable.isChecked());
            }
        });
        enable.setChecked(a.getEnable());
        if (!animate)
            enable.jumpDrawablesToCurrentState();

        final CheckBox weekdays = (CheckBox) view.findViewById(R.id.alarm_week_days);
        LinearLayout weekdaysValues = (LinearLayout) view.findViewById(R.id.alarm_week);

        for (int i = 0; i < weekdaysValues.getChildCount(); i++) {
            final CheckBox child = (CheckBox) weekdaysValues.getChildAt(i);
            if (child instanceof CheckBox) {
                child.setText(getString(Week.DAYS[startweek]).substring(0, 1));
                final int week = Week.EVERYDAY[startweek];

                child.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setWeek(a, week, child.isChecked());
                    }
                });
                child.setChecked(a.isWeek(week));
                startweek++;
                if (startweek >= Week.DAYS.length)
                    startweek = 0;
            }
        }
        weekdays.setChecked(a.weekdaysCheck);
        weekdays.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.weekdaysCheck = weekdays.isChecked();
                if (a.weekdaysCheck && a.noDays()) {
                    a.setEveryday();
                }
                save(a);
            }
        });

        final CheckBox ringtone = (CheckBox) view.findViewById(R.id.alarm_ringtone);
        ringtone.setChecked(a.ringtone);
        if (ringtone.isChecked()) {
            TextView ringtoneValue = (TextView) view.findViewById(R.id.alarm_ringtone_value);
            String title = HourlyApplication.getTitle(getActivity(), a.ringtoneValue);
            if (title == null)
                title = HourlyApplication.getTitle(getActivity(), fallbackUri(null));
            ringtoneValue.setText(title);
        }

        final CheckBox beep = (CheckBox) view.findViewById(R.id.alarm_beep);
        beep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.beep = beep.isChecked();
                save(a);
            }
        });
        beep.setChecked(a.beep);
        final CheckBox speech = (CheckBox) view.findViewById(R.id.alarm_speech);
        speech.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.speech = speech.isChecked();
                save(a);
            }
        });
        speech.setChecked(a.speech);

        final CheckBox alarmRingtone = (CheckBox) view.findViewById(R.id.alarm_ringtone);
        final View alarmRingtonePlay = view.findViewById(R.id.alarm_ringtone_play);

        if (preview) {
            previewCancel();
        }

        alarmRingtone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.ringtone = alarmRingtone.isChecked();
                save(a);
            }
        });
        alarmRingtonePlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (preview) {
                    previewCancel();
                    return;
                }

                Sound.Silenced s = playPreview(a);

                if (s == Sound.Silenced.VIBRATE) { // we can stop vibrate by clicking on image
                    WeekSetFragment.this.preview = true;
                    WeekSetFragment.this.alarmRingtonePlay = alarmRingtonePlay;
                    return;
                }
                if (s != Sound.Silenced.NONE) { // if not vibrating exit
                    return;
                }

                WeekSetFragment.this.preview = true;
                WeekSetFragment.this.alarmRingtonePlay = alarmRingtonePlay;

                Animation a = AnimationUtils.loadAnimation(getActivity(), R.anim.shake);
                alarmRingtonePlay.startAnimation(a);
            }
        });

        final View trash = view.findViewById(R.id.alarm_bottom_first);
        trash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            view.setTag(TYPE_DELETED);
                            // mark scroll as animating. because we about to remove item.
                            RemoveItemAnimation.apply(list, view.findViewById(R.id.alarm_base), new Runnable() {
                                @Override
                                public void run() {
                                    remove(a);
                                    select(-1);
                                }
                            });
                        }
                    }
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(R.string.AreYouSure).setPositiveButton(R.string.Yes, dialogClickListener)
                        .setNegativeButton(R.string.No, dialogClickListener).show();
            }
        });

        View ringtoneButton = view.findViewById(R.id.alarm_ringtone_value_box);
        ringtoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fragmentRequestRingtone = a;
                Uri uri = null;
                if (!a.ringtoneValue.isEmpty()) {
                    uri = Uri.parse(a.ringtoneValue);
                }
                startActivityForResult(new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Alarm.DEFAULT_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, R.string.SelectAlarm)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri), 0);
            }
        });
        View ringtoneBrowse = view.findViewById(R.id.alarm_ringtone_browse);
        ringtoneBrowse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fragmentRequestRingtone = a;
                if (permitted())
                    selectFile();
            }
        });

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boxAnimate = true;
                select(-1);
            }
        });
    }

    void selectFile() {
        final OpenFileDialog f = new OpenFileDialog(getActivity());

        String path = fragmentRequestRingtone.ringtoneValue;

        if (path == null) {
            path = "";
        }

        File sound = new File(path);

        while (!sound.exists()) {
            sound = sound.getParentFile();
            if (sound == null) {
                String def = Environment.getExternalStorageDirectory().getPath();
                SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
                sound = new File(shared.getString(HourlyApplication.PREFERENCE_LAST_PATH, def));
            }
        }

        f.setReadonly(true);
        f.setCurrentPath(sound);
        f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File ff = f.getCurrentPath();

                SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
                shared.edit().putString(HourlyApplication.PREFERENCE_LAST_PATH, ff.getParent()).commit();

                if (!ff.isFile())
                    return;

                fragmentRequestRingtone.ringtoneValue = ff.getAbsolutePath();
                save(fragmentRequestRingtone);
                fragmentRequestRingtone = null;
            }
        });
        f.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1:
                if (permitted(permissions))
                    selectFile();
                else
                    Toast.makeText(getActivity(), R.string.NotPermitted, Toast.LENGTH_SHORT).show();
        }
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};

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

    boolean permitted() {
        if (Build.VERSION.SDK_INT < 11)
            return true;
        for (String s : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(getActivity(), s) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(PERMISSIONS, 1);
                return false;
            }
        }
        return true;
    }

    void setEnable(WeekSet a, boolean e) {
        a.setEnable(e);
        save(a);
    }

    public void fillCompact(final View view, final WeekSet a, boolean animate) {
        TextView time = (TextView) view.findViewById(R.id.alarm_time);
        time.setClickable(false);

        final SwitchCompat enable = (SwitchCompat) view.findViewById(R.id.alarm_enable);
        enable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setEnable(a, enable.isChecked());
            }
        });
        enable.setChecked(a.getEnable());
        if (!animate)
            enable.jumpDrawablesToCurrentState();

        TextView days = (TextView) view.findViewById(R.id.alarm_compact_first);
        days.setText(a.getDays());

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boxAnimate = true;
                select(a.id);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.unregisterOnSharedPreferenceChangeListener(this);

        if (sound != null) {
            sound.close();
            sound = null;
        }
    }
}
