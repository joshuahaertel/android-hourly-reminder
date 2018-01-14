package com.github.axet.hourlyreminder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.preference.PreferenceManager;

public class SoundConfig extends com.github.axet.androidlibrary.sound.Sound {
    public static final String TAG = SoundConfig.class.getSimpleName();

    public final static int SOUND_CHANNELS = AudioFormat.CHANNEL_OUT_STEREO;
    public final static int SOUND_SAMPLERATE = 44100;

    public enum Silenced {
        NONE,
        VIBRATE, // vibrate instead of sound
        SETTINGS,
        CALL,
        MUSIC
    }

    public static class SoundChannel {
        public int streamType; // AudioManager.STREAM_* == AudioSystem.STREAM_*
        public int usage; // AudioAttributes.USAGE_*
        public int ct; // AudioAttributes.CONTENT_TYPE_*
    }

    Handler handler;

    public SoundConfig(Context context) {
        super(context);
        this.handler = new Handler();
    }

    public SoundChannel getSoundChannel() {
        SoundChannel s = new SoundChannel();
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        boolean b = shared.getBoolean(HourlyApplication.PREFERENCE_PHONESILENCE, false);
        if (b) {
            s.streamType = AudioManager.STREAM_NOTIFICATION;
            s.usage = AudioAttributes.USAGE_NOTIFICATION;
            s.ct = AudioAttributes.CONTENT_TYPE_SONIFICATION;
        } else {
            s.streamType = AudioManager.STREAM_ALARM;
            s.usage = AudioAttributes.USAGE_ALARM;
            s.ct = AudioAttributes.CONTENT_TYPE_SONIFICATION;
        }
        return s;
    }

    float getRingtoneVolume() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_INCREASE_VOLUME, "0")) > 0) {
            return 0;
        }
        return getVolume();
    }

    float getVolume() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        float vol = shared.getFloat(HourlyApplication.PREFERENCE_VOLUME, 1f);
        return reduce(vol);
    }

    float reduce(float vol) {
        return (float) Math.pow(vol, 3);
    }

    float unreduce(float vol) {
        return (float) Math.exp(Math.log(vol) / 3);
    }
}
