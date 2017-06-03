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
import android.util.Log;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.hourlyreminder.R;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class TTS extends SoundConfig {
    public static final String TAG = TTS.class.getSimpleName();

    public static final int DELAYED_DELAY = 5000;

    public static final String KEY_PARAM_VOLUME = "volume"; // TextToSpeech.Engine.KEY_PARAM_VOLUME

    public static final String TTS_INIT = SoundConfig.class.getCanonicalName() + "_TTS_INIT";

    TextToSpeech tts;
    Runnable delayed; // tts may not be initalized, on init done, run delayed.run()
    boolean restart; // restart tts once if failed. on apk upgrade tts failed connection.
    Set<Runnable> done = new HashSet<>(); // valid done list, in case sound was canceled during play done will not be present
    Runnable create;

    public TTS(Context context) {
        super(context);
        ttsCreate();
    }

    void ttsCreate() {
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(final int status) {
                if (status != TextToSpeech.SUCCESS)
                    return;
                if (create != null)
                    handler.removeCallbacks(create);
                create = new Runnable() {
                    TextToSpeech tts = TTS.this.tts;

                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= 21) {
                            tts.setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(SOUND_CHANNEL)
                                    .setContentType(SOUND_TYPE)
                                    .build());
                        }

                        if (delayed != null) {
                            delayed.run();
                            handler.removeCallbacks(delayed);
                            delayed = null;
                        }
                    }
                };
                handler.post(create);
            }
        });
    }

    public void close() {
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        if (create != null) {
            handler.removeCallbacks(create);
            create = null;
        }
        if (delayed != null) {
            handler.removeCallbacks(delayed);
            delayed = null;
        }
    }

    public void playSpeech(final long time, final Runnable done) {
        TTS.this.done.clear();
        TTS.this.done.add(done);

        // clear delayed(), sound just played
        final Runnable clear = new Runnable() {
            @Override
            public void run() {
                if (delayed != null) {
                    handler.removeCallbacks(delayed);
                    delayed = null;
                }
                if (done != null && TTS.this.done.contains(done))
                    done.run();
            }
        };

        if (tts == null) {
            ttsCreate();
        }

        if (Build.VERSION.SDK_INT < 15) {
            tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String s) {
                    handler.post(clear);
                }
            });
        } else {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                }

                @Override
                public void onDone(String utteranceId) {
                    handler.post(clear); // from different thread
                }

                @Override
                public void onError(String utteranceId) {
                    clear.run();
                }
            });
        }

        // TTS may say failed, but play sounds successfuly. we need regardless or failed do not
        // play speech twice if clear.run() was called.
        if (!playSpeech(time)) {
            Toast.makeText(context, context.getString(R.string.WaitTTS), Toast.LENGTH_SHORT).show();
            if (delayed != null) {
                handler.removeCallbacks(delayed);
            }
            delayed = new Runnable() {
                @Override
                public void run() {
                    if (!playSpeech(time)) {
                        tts.shutdown(); // on apk upgrade tts failed always. close it and restart.
                        tts = null;
                        if (restart) {
                            Toast.makeText(context, context.getString(R.string.FailedTTS), Toast.LENGTH_SHORT).show();
                            clear.run();
                        } else {
                            restart = true;
                            Toast.makeText(context, context.getString(R.string.FailedTTSRestar), Toast.LENGTH_SHORT).show();
                            if (delayed != null) {
                                handler.removeCallbacks(delayed);
                            }
                            delayed = new Runnable() {
                                @Override
                                public void run() {
                                    playSpeech(time, done);
                                }
                            };
                            handler.postDelayed(delayed, DELAYED_DELAY);
                        }
                    }
                }
            };
            handler.postDelayed(delayed, DELAYED_DELAY);
        }
    }

    boolean playSpeech(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);

        int h;
        if (DateFormat.is24HourFormat(context)) {
            h = hour;
        } else {
            h = c.get(Calendar.HOUR);
            if (h == 0) { // 12
                h = 12;
            }
        }

        String speak = "";

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        boolean is24 = DateFormat.is24HourFormat(context);
        boolean speakAMPMFlag = !is24 && shared.getBoolean(HourlyApplication.PREFERENCE_SPEAK_AMPM, false);

        String lang = shared.getString(HourlyApplication.PREFERENCE_LANGUAGE, ""); // take user lang preferences

        Locale locale;

        if (lang.isEmpty()) // use system locale (system language)
            locale = Locale.getDefault();
        else
            locale = new Locale(lang);

        if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) {
            lang = locale.getLanguage();
            locale = new Locale(lang);
        }

        if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) { // user selection not supported.
            locale = null;
            if (Build.VERSION.SDK_INT >= 21) {
                Voice v = tts.getDefaultVoice();
                if (v != null)
                    locale = v.getLocale();
            }
            if (locale == null) {
                if (Build.VERSION.SDK_INT >= 18) {
                    locale = tts.getDefaultLanguage();
                } else {
                    locale = tts.getLanguage();
                }
            }
            if (locale == null) {
                locale = Locale.getDefault();
            }
            if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) { // default tts voice not supported. use 'lang'
                lang = locale.getLanguage();
                locale = new Locale(lang);
            }
            if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) { // default 'lang' tts voice not supported. use 'system default lang'
                locale = Locale.getDefault();
                lang = locale.getLanguage();
                locale = new Locale(lang);
            }
            if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) { // 'system default lang' tts voice not supported. use 'en'
                locale = new Locale("en");
                if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_NOT_SUPPORTED) { // 'en' not supported? do not speak
                    return false;
                }
            }
        }

        if (tts.isLanguageAvailable(locale) == TextToSpeech.LANG_MISSING_DATA) {
            Intent intent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            }
            return false;
        }

        String speakAMPM = "";
        String speakHour = "";
        String speakMinute = "";

        // Russian requires dots "." and hours/minutes
        Locale ru = new Locale("ru");
        if (locale.toString().startsWith(ru.toString())) {
            if (speakAMPMFlag) {
                speakAMPM = HourlyApplication.getHour4String(context, ru, hour);
            }

            speakHour = HourlyApplication.getQuantityString(context, ru, R.plurals.hours, h, h);
            speakMinute = HourlyApplication.getQuantityString(context, ru, R.plurals.minutes, min, min);

            if (min != 0) {
                speak = HourlyApplication.getString(context, ru, R.string.speak_time, ". " + speakHour + ". " + speakMinute + " " + speakAMPM);
            } else {
                if (is24) {
                    speak = HourlyApplication.getString(context, ru, R.string.speak_time_24, speakHour);
                } else if (speakAMPMFlag) {
                    speak = HourlyApplication.getString(context, ru, R.string.speak_time, ". " + speakHour + ". " + speakAMPM);
                } else {
                    speak = HourlyApplication.getString(context, ru, R.string.speak_time_12, speakHour);
                }
            }
            tts.setLanguage(ru);
        }

        // english requres zero minutes
        Locale en = new Locale("en");
        if (locale.toString().startsWith(en.toString())) {
            if (speakAMPMFlag) {
                speakAMPM = HourlyApplication.getHour4String(context, en, hour);
            }

            speakHour = String.format("%d", h);

            if (min < 10)
                speakMinute = String.format("oh %d", min);
            else
                speakMinute = String.format("%d", min);

            if (min != 0) {
                speak = HourlyApplication.getString(context, en, R.string.speak_time, speakHour + " " + speakMinute + " " + speakAMPM);
            } else {
                if (is24) {
                    speak = HourlyApplication.getString(context, en, R.string.speak_time_24, speakHour);
                } else if (speakAMPMFlag) {
                    speak = HourlyApplication.getString(context, en, R.string.speak_time, speakHour + " " + speakAMPM);
                } else {
                    speak = HourlyApplication.getString(context, en, R.string.speak_time_12, speakHour);
                }
            }
            tts.setLanguage(en);
        }

        if (speak.isEmpty()) { // no adopted translation
            speakHour = String.format("%d", h);
            speakMinute = String.format("%d", min);
            speak = speakHour + " " + speakMinute;
            tts.setLanguage(locale);
        }

        Log.d(TAG, speak);

        if (Build.VERSION.SDK_INT >= 21) {
            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, SOUND_CHANNEL);
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, getVolume());
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "DONE");
            if (tts.speak(speak, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString()) != TextToSpeech.SUCCESS) {
                return false;
            }
        } else {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(SOUND_STREAM));
            params.put(KEY_PARAM_VOLUME, Float.toString(getVolume()));
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "DONE");
            if (tts.speak(speak, TextToSpeech.QUEUE_FLUSH, params) != TextToSpeech.SUCCESS) {
                return false;
            }
        }
        restart = false;
        return true;
    }
}
