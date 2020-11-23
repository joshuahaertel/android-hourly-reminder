package com.github.axet.hourlyreminder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.preference.PreferenceManager;

public class SoundConfig extends TTS {
    public static final String TAG = SoundConfig.class.getSimpleName();

    public final static int SOUND_CHANNELS = AudioFormat.CHANNEL_OUT_MONO;
    public final static int SOUND_SAMPLERATE = 16000;

    public enum Silenced {
        NONE,
        VIBRATE, // vibrate/flash instead of sound
        SETTINGS,
        CALL,
        MUSIC
    }

    public SoundConfig(Context context) {
        super(context);
    }

    @Override
    public Channel getSoundChannel() {
        Channel s;
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        boolean b = shared.getBoolean(HourlyApplication.PREFERENCE_PHONESILENCE, false);
        if (b)
            s = Channel.NORMAL;
        else
            s = Channel.ALARM;
        return s;
    }

    float getRingtoneVolume() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_INCREASE_VOLUME, "0")) > 0) {
            return 0;
        }
        return getVolume();
    }

    @Override
    public float getVolume() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        float vol = shared.getFloat(HourlyApplication.PREFERENCE_VOLUME, 1f);
        return reduce(vol);
    }
}
