package com.github.axet.hourlyreminder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.github.axet.hourlyreminder.R;

public class SoundConfig {
    public static final String TAG = SoundConfig.class.getSimpleName();

    public final static int SOUND_STREAM = AudioManager.STREAM_ALARM; // AudioSystem.STREAM_ALARM == AudioManager.STREAM_ALARM;
    public final static int SOUND_CHANNEL = AudioAttributes.USAGE_ALARM;
    public final static int SOUND_TYPE = AudioAttributes.CONTENT_TYPE_SONIFICATION;
    public final static int SOUND_CHANNELS = AudioFormat.CHANNEL_OUT_STEREO;
    public final static int SOUND_SAMPLERATE = 44100;

    public enum Silenced {
        NONE,
        VIBRATE, // vibrate instead of sound
        SETTINGS,
        CALL,
        MUSIC
    }

    Context context;
    Handler handler;

    Float volume;

    public SoundConfig(Context context) {
        this.context = context;
        this.handler = new Handler();
    }

    float getRingtoneVolume() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_INCREASE_VOLUME, "0")) > 0) {
            return 0;
        }

        return getVolume();
    }

    float getVolume() {
        if (volume != null)
            return volume;

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        return (float) (Math.pow(shared.getFloat(HourlyApplication.PREFERENCE_VOLUME, 1f), 3));
    }

    public void setVolume(float f) {
        volume = f;
    }

}
