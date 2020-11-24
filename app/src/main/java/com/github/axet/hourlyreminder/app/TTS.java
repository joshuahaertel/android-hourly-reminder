package com.github.axet.hourlyreminder.app;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.text.format.DateFormat;
import android.util.Log;

import com.github.axet.androidlibrary.app.AlarmManager;
import com.github.axet.androidlibrary.app.Storage;
import com.github.axet.androidlibrary.crypto.MD5;
import com.github.axet.androidlibrary.widgets.CacheImagesAdapter;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.widgets.TTSPreference;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

public class TTS extends Player {
    public static final String TAG = TTS.class.getSimpleName();

    public static void clearCache(Context context) {
        CacheImagesAdapter.cacheClear(context);
    }

    public static File cacheUri(Context context, Locale lang, String speak) {
        File cache = CacheImagesAdapter.getCache(context);
        return new File(cache, CacheImagesAdapter.CACHE_NAME + MD5.digest(lang + "_" + speak));
    }

    public static void cacheTouch(File cache) {
        Storage.touch(cache);
    }

    @TargetApi(30)
    public static int synthesizeToFile(TextToSpeech tts, String speak, Bundle params, ParcelFileDescriptor fd, String s) {
        try {
            Method m = tts.getClass().getMethod("synthesizeToFile", CharSequence.class, Bundle.class, ParcelFileDescriptor.class, String.class);
            return (int) m.invoke(tts, speak, params, fd, s);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public TTS(Context context) {
        super(context);
    }

    public File cache(final long time) {
        Speak speak = seakText(time);
        if (speak == null)
            return null; // lang not supported
        final File cache = cacheUri(context, speak.locale, speak.text);
        if (cache.exists()) {
            long now = System.currentTimeMillis();
            if (cache.length() == 0 && cache.lastModified() + 5 * AlarmManager.MIN1 > now)
                return cache; // keep recent cache if file size == 0
        }
        if (tts == null)
            ttsCreate();
        if (onInit != null) {
            dones.remove(delayed);
            handler.removeCallbacks(delayed);
            delayed = new Runnable() {
                @Override
                public void run() {
                    cache(time);
                }
            };
            dones.add(delayed);
            return null;
        }
        try {
            if (!cache.createNewFile()) // synthesizeToFile async
                return null;
        } catch (IOException e) {
            Log.e(TAG, "unable to create cache", e);
            return null;
        }
        Log.d(TAG, "caching '" + speak.text + "' to " + cache);
        tts.setLanguage(speak.locale);
        if (Build.VERSION.SDK_INT >= 21) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "DONE");
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    ParcelFileDescriptor fd = ParcelFileDescriptor.open(cache, ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_READ_WRITE);
                    if (synthesizeToFile(tts, speak.text, params, fd, UUID.randomUUID().toString()) != TextToSpeech.SUCCESS) {
                        cache.delete();
                        return null;
                    }
                } catch (Exception e) {
                    Log.d(TAG, "unable tts to file", e);
                    cache.delete();
                    return null;
                }
            } else {
                if (tts.synthesizeToFile(speak.text, params, cache, UUID.randomUUID().toString()) != TextToSpeech.SUCCESS) {
                    cache.delete();
                    return null;
                }
            }
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "DONE");
            if (tts.synthesizeToFile(speak.text, params, cache.getAbsolutePath()) != TextToSpeech.SUCCESS) {
                cache.delete();
                return null;
            }
        }
        return cache;
    }

    public void playSpeech(final long time, final Runnable done) {
        Speak speak = seakText(time);
        if (speak != null) {
            File cache = cacheUri(context, speak.locale, speak.text);
            if (cache.exists() && cache.length() > 0) {
                Log.d(TAG, "playing cache '" + speak.text + "' from " + cache);
                if (playCache(cache, done)) {
                    cacheTouch(cache);
                    return;
                } else {
                    cache.delete();
                }
            }
        }
        Log.d(TAG, "speaking '" + speak.text + "' (" + speak.locale + ")");
        super.playSpeech(speak, done);
    }

    public boolean playCache(File cache, final Runnable done) {
        playerCl();
        MediaPlayer player;
        try {
            player = create(Uri.fromFile(cache));
        } catch (RuntimeException e) {
            Log.d(TAG, "unable to play cache", e);
            return false;
        }
        dones.add(done);
        playOncePrepare(player, done);
        player.setVolume(getVolume(), getVolume());
        startPlayer(player);
        return true;
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

    public Speak seakText(long time) {
        Locale locale;
        if (tts == null)
            locale = getUserLocale();
        else
            locale = getTTSLocale();
        if (locale == null)
            return null;
        return new Speak(locale, speakText(time, locale, DateFormat.is24HourFormat(context)));
    }
}
