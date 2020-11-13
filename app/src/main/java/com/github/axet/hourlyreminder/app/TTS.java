package com.github.axet.hourlyreminder.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.text.format.DateFormat;
import android.util.AndroidRuntimeException;
import android.util.Log;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.widgets.TTSPreference;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class TTS extends com.github.axet.androidlibrary.sound.TTS {
    public static final String TAG = TTS.class.getSimpleName();

    public TTS(Context context) {
        super(context);
        ttsCreate();
    }

    public void playSpeech(final long time, final Runnable done) {
        super.playSpeech(seakText(time), done);
    }

    public String speakText(long time, Locale locale, boolean is24) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);

        String speak;

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String custom = shared.getString(HourlyApplication.PREFERENCE_SPEAK_CUSTOM, "");
        TTSPreference.TTSConfig config = new TTSPreference.TTSConfig(locale);
        if (custom.isEmpty())
            config.def(context);
        else
            config.load(custom);

        if (config.def)
            config.def(context);

        if (min != 0) {
            if (is24)
                speak = config.time24h01;
            else
                speak = config.time12h01;
        } else {
            if (is24)
                speak = config.time24h00;
            else
                speak = config.time12h00;
        }

        return speakText(hour, min, locale, speak, is24);
    }

    @Override
    public Locale getUserLocale() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        String lang = shared.getString(HourlyApplication.PREFERENCE_LANGUAGE, ""); // take user lang preferences

        Locale locale;

        if (lang.isEmpty()) // use system locale (system language)
            locale = Locale.getDefault();
        else
            locale = new Locale(lang);

        return locale;
    }

    public String speakText(int hour, int min, Locale locale, String speak, boolean is24) {
        int h;
        if (is24) {
            h = hour;
        } else {
            h = hour;
            if (h >= 12)
                h = h - 12;
            if (h == 0) // 12
                h = 12;
        }

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        boolean speakAMPMFlag = !is24 && shared.getBoolean(HourlyApplication.PREFERENCE_SPEAK_AMPM, false);

        String speakHour = "";
        String speakMinute = "";
        String speakAMPM = "";

        Locale ru = new Locale("ru");
        if (locale.toString().startsWith(ru.toString())) {
            if (speakAMPMFlag)
                speakAMPM = HourlyApplication.getHour4String(context, ru, hour);

            speakHour = HourlyApplication.getQuantityString(context, ru, R.plurals.hours, h, h);
            speakMinute = HourlyApplication.getQuantityString(context, ru, R.plurals.minutes, min, min);
        }

        // English requires zero minutes
        Locale en = new Locale("en");
        if (locale.toString().startsWith(en.toString())) {
            if (speakAMPMFlag)
                speakAMPM = HourlyApplication.getHour4String(context, en, hour);

            speakHour = String.format("%d", h);

            if (min < 10)
                speakMinute = String.format("oh %d", min);
            else
                speakMinute = String.format("%d", min);
        }

        if (speakHour.isEmpty())
            speakHour = String.format("%d", h);

        if (speakMinute.isEmpty())
            speakMinute = String.format("%d", min);

        speak = speak.replaceAll("%H", speakHour);
        speak = speak.replaceAll("%M", speakMinute);
        speak = speak.replaceAll("%A", speakAMPM);

        speak = speak.replaceAll("%h", String.format("%d", h)); // speak non translated hours
        speak = speak.replaceAll("%m", String.format("%d", min)); // speak non translated minutes

        return speak;
    }

    public String seakText(long time) {
        Locale locale = getTTSLocale();
        if (locale == null)
            return null;
        return speakText(time, locale, DateFormat.is24HourFormat(context));
    }
}
