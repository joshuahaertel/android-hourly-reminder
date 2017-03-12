package com.github.axet.hourlyreminder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.sound.FadeVolume;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.alarms.Alarm;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.alarms.WeekSet;
import com.github.axet.hourlyreminder.dialogs.BeepPrefDialogFragment;
import com.github.axet.hourlyreminder.services.FireAlarmService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Sound extends TTS {
    public static final String TAG = Sound.class.getSimpleName();

    ToneGenerator tone;
    Runnable toneLoop;
    MediaPlayer player;
    AudioTrack track;
    FadeVolume increaseVolume;
    Runnable loop; // loop preventer

    public static class Playlist {
        public List<String> beforeOnce = new ArrayList<>();
        public List<String> before = new ArrayList<>();
        public boolean beep;
        public boolean speech;
        public List<String> afterOnce = new ArrayList<>();
        public List<String> after = new ArrayList<>();

        public Playlist() {
        }

        public Playlist(JSONObject o) {
            load(o);
        }

        public Playlist(WeekSet rs) {
            merge(rs);
        }

        public void merge(WeekSet rs) {
            this.beep |= rs.beep;
            this.speech |= rs.speech;
            if (rs.ringtone) {
                if (rs.beep || rs.speech) {
                    if (!before.contains(rs.ringtoneValue)) // do not add after, if same sound already played "before"
                        add(after, rs.ringtoneValue);
                } else {
                    after.remove(rs.ringtoneValue); // do not add after, if same sound already played "before"
                    add(before, rs.ringtoneValue);
                }
            }
        }

        // merge alarm with reminder
        public void withAlarm(ReminderSet rs) {
            this.beep |= rs.beep;
            this.speech |= rs.speech;
            if (rs.ringtone) {
                if (rs.beep || rs.speech) {
                    if (!beforeOnce.contains(rs.ringtoneValue)) // do not add after, if same sound already played "before"
                        add(afterOnce, rs.ringtoneValue);
                } else {
                    afterOnce.remove(rs.ringtoneValue); // do not add after, if same sound already played "before"
                    add(beforeOnce, rs.ringtoneValue);
                }
            }
        }

        void add(List<String> l, String s) {
            if (l.contains(s))
                return;
            l.add(s);
        }

        ArrayList<String> load(JSONArray aa) {
            ArrayList<String> l = new ArrayList<>();
            try {
                if (aa != null) {
                    for (int i = 0; i < aa.length(); i++) {
                        l.add(aa.getString(i));
                    }
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return l;
        }

        public void load(String json) {
            try {
                JSONObject o = new JSONObject(json);
                load(o);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public void load(JSONObject o) {
            try {
                beforeOnce = load(o.optJSONArray("beforeOnce")); // opt for <= 2.1.4
                before = load(o.optJSONArray("before")); // opt for unknown old version
                beep = o.getBoolean("beep");
                speech = o.getBoolean("speech");
                afterOnce = load(o.optJSONArray("afterOnce")); // opt for <= 2.1.4
                after = load(o.optJSONArray("after")); // opt for unknown old version
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public JSONObject save() {
            JSONObject o = new JSONObject();
            try {
                o.put("beforeOnce", new JSONArray(beforeOnce));
                o.put("before", new JSONArray(before));
                o.put("beep", beep);
                o.put("speech", speech);
                o.put("afterOnce", new JSONArray(afterOnce));
                o.put("after", new JSONArray(after));
                return o;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Sound(Context context) {
        super(context);
    }

    public void close() {
        super.close();

        playerClose();

        if (track != null) {
            track.release();
            track = null;
        }
    }

    // https://gist.github.com/slightfoot/6330866
    public static AudioTrack generateTone(double freqHz, int durationMs) {
        int min = AudioTrack.getMinBufferSize(SOUND_SAMPLERATE, SOUND_CHANNELS, SOUND_FORMAT);
        int count = SOUND_SAMPLERATE * durationMs / 1000;
        int last = count - 1;
        int stereo = count * 2;
        int size = stereo * (Short.SIZE / 8);
        if (size < min) // AudioTrack unable to play shorter then 'min' size of data, fill it with zeros
            size = min;
        short[] samples = new short[size];
        for (int i = 0; i < stereo; i += 2) {
            int si = i / 2;
            double sx = 2 * Math.PI * si / (SOUND_SAMPLERATE / freqHz);
            short sample = (short) (Math.sin(sx) * 0x7FFF);
            samples[i + 0] = sample;
            samples[i + 1] = sample;
        }
        // old phones bug.
        //
        // http://stackoverflow.com/questions/27602492
        //
        // with MODE_STATIC setNotificationMarkerPosition not called
        AudioTrack track = new AudioTrack(SOUND_STREAM, SOUND_SAMPLERATE, SOUND_CHANNELS, SOUND_FORMAT, size, AudioTrack.MODE_STREAM);
        track.write(samples, 0, size);
        track.setNotificationMarkerPosition(last); // do not throw exception on != AudioTrack.SUCCESS
        return track;
    }

    public Silenced silencedPlaylist(Playlist rr) {
        Silenced ss = silenced();

        if (ss != Silenced.NONE)
            return ss;

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        boolean v = shared.getBoolean(HourlyApplication.PREFERENCE_VIBRATE, false);
        boolean c = !rr.after.isEmpty() || !rr.before.isEmpty();
        boolean s = rr.speech;
        boolean b = rr.beep;

        if (!v && !c && !s && !b)
            return Silenced.SETTINGS;

        if (v && !c && !s && !b)
            return Silenced.VIBRATE;

        return Silenced.NONE;
    }

    public Silenced silencedAlarm(WeekSet a) {
        Silenced ss = silenced();

        if (ss != Silenced.NONE)
            return ss;

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        boolean v = shared.getBoolean(HourlyApplication.PREFERENCE_VIBRATE, false);
        boolean c = a.ringtone;
        boolean s = a.speech;
        boolean b = a.beep;

        if (!v && !c && !s && !b)
            return Silenced.SETTINGS;

        if (v && !c && !s && !b)
            return Silenced.VIBRATE;

        return Silenced.NONE;
    }

    public Silenced silenced() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        if (shared.getBoolean(HourlyApplication.PREFERENCE_CALLSILENCE, false)) {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK) {
                return Silenced.CALL;
            }
        }

        if (shared.getBoolean(HourlyApplication.PREFERENCE_MUSICSILENCE, false)) {
            AudioManager tm = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (tm.isMusicActive()) {
                return Silenced.MUSIC;
            }
        }

        return Silenced.NONE;
    }

    public Silenced playList(final Playlist rr, final long time, final Runnable done) {
        playerClose();

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        Silenced s = silencedPlaylist(rr);

        // do we have slince alarm?
        if (s != Silenced.NONE) {
            if (s == Silenced.VIBRATE)
                vibrate();
            if (done != null) {
                done.run();
            }
            return s;
        }

        if (shared.getBoolean(HourlyApplication.PREFERENCE_VIBRATE, false)) {
            vibrate();
        }

        final Runnable after = new Runnable() {
            @Override
            public void run() {
                if (!rr.after.isEmpty()) {
                    playCustom(rr.after, done);
                } else {
                    if (done != null) {
                        done.run();
                    }
                }
            }
        };

        final Runnable speech = new Runnable() {
            @Override
            public void run() {
                if (rr.speech) {
                    playSpeech(time, after);
                } else {
                    after.run();
                }
            }
        };

        final Runnable beep = new Runnable() {
            @Override
            public void run() {
                if (rr.beep) {
                    playBeep(speech);
                } else {
                    speech.run();
                }
            }
        };

        final Runnable before = new Runnable() {
            @Override
            public void run() {
                if (!rr.before.isEmpty()) {
                    playCustom(rr.before, beep);
                } else {
                    beep.run();
                }
            }
        };

        before.run();
        return s;
    }

    public Silenced playReminder(final ReminderSet rs, final long time, final Runnable done) {
        return playList(new Playlist(rs), time, done);
    }

    public void playCustom(List<String> uu, final Runnable done) {
        playCustom(uu, 0, done);
    }

    public void playCustom(final List<String> uu, final int index, final Runnable done) {
        if (index >= uu.size()) {
            if (done != null)
                done.run();
            return;
        }

        playCustom(uu.get(index), new Runnable() {
            @Override
            public void run() {
                playCustom(uu, index + 1, done);
            }
        });
    }

    public void playCustom(String uri, final Runnable done) {
        playerCl();

        Sound.this.done.add(done);

        player = playOnce(Uri.parse(uri), new Runnable() {
            @Override
            public void run() {
                if (done != null && Sound.this.done.contains(done))
                    done.run();
            }
        });
    }

    public void playBeep(final Runnable done) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String b = shared.getString(HourlyApplication.PREFERENCE_BEEP_CUSTOM, "1800:100");

        BeepPrefDialogFragment.BeepConfig beep = new BeepPrefDialogFragment.BeepConfig();
        beep.load(b);

        playBeep(generateTone(beep.value_f, beep.value_l), done);
    }

    public void playBeep(AudioTrack t, final Runnable done) {
        if (track != null)
            track.release();

        track = t;

        if (Build.VERSION.SDK_INT < 21) {
            track.setStereoVolume(getVolume(), getVolume());
        } else {
            track.setVolume(getVolume());
        }

        Sound.this.done.add(done);

        final Runnable end = new Runnable() {
            @Override
            public void run() {
                // prevent strange android bug, with second beep when connecting android to external usb audio source.
                // seems like this beep pushed to external audio source from sound cache.
                if (track != null) {
                    track.release();
                    track = null;
                }
                if (done != null && Sound.this.done.contains(done))
                    done.run();
            }
        };

        int mark = 0;
        try {
            mark = track.getNotificationMarkerPosition();
        } catch (IllegalStateException ignore) { // Unable to retrieve AudioTrack pointer for getMarkerPosition()
        }
        if (mark <= 0) { // some old bugged phones unable to set markers
            try {
                Method m = track.getClass().getDeclaredMethod("getNativeFrameCount");
                m.setAccessible(true);
                int len = (int) m.invoke(track) * 1000 / track.getSampleRate();
                handler.postDelayed(end, len * 2);
            } catch (Exception e) { // use 1 sec delay
                handler.postDelayed(end, 1000);
            }
        } else {
            track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override
                public void onMarkerReached(AudioTrack t) {
                    end.run();
                }

                @Override
                public void onPeriodicNotification(AudioTrack track) {
                }
            });
        }

        track.play();
    }

    // called from alarm
    public void playRingtone(Uri uri) {
        playerClose();

        player = create(uri);
        if (player == null) {
            player = create(Alarm.DEFAULT_ALARM);
        }
        if (player == null) { // last resort fallback
            toneLoop = new Runnable() {
                @Override
                public void run() {
                    long dur = tonePlay();
                    handler.postDelayed(toneLoop, dur); // length of tone
                }
            };
            toneLoop.run();
            return;
        }
        player.setLooping(true);

        startPlayer(player);
    }

    long tonePlay() {
        if (tone != null)
            tone.release();
        tone = new ToneGenerator(SOUND_STREAM, 100);
        tone.startTone(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL);
        return 4000;
    }

    public void startPlayer(final MediaPlayer player) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        final int inc = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_INCREASE_VOLUME, "0")) * 1000;

        if (inc == 0) {
            player.setVolume(getVolume(), getVolume());
            player.start();
            return;
        }

        final float startVolume;

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        float systemVolume = am.getStreamMaxVolume(SOUND_STREAM) / (float) am.getStreamVolume(SOUND_STREAM);
        float alarmVolume = getVolume();

        // if user trying to reduce alarms volume, then use it as start volume. else start from silence
        if (systemVolume > alarmVolume)
            startVolume = alarmVolume;
        else
            startVolume = 0;

        if (increaseVolume != null)
            increaseVolume.stop();
        increaseVolume = new FadeVolume(handler, inc) {
            float rest = 1f - startVolume;

            @Override
            public boolean step(float vol) {
                try {
                    vol = startVolume + rest * vol;
                    player.setVolume(vol, vol);
                    return true;
                } catch (IllegalStateException ignore) {
                    return false; // ignore. player probably already closed
                }
            }
        };
        increaseVolume.run();

        player.start();
    }

    public void timeToast(long time) {
        String text = context.getResources().getString(R.string.ToastTime, Alarm.format2412ap(context, time));
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    public void silencedToast(Silenced s, long time) {
        String text = "";
        switch (s) {
            case VIBRATE:
                text += context.getString(R.string.SoundSilencedVibrate);
                break;
            case CALL:
                text += context.getString(R.string.SoundSilencedCall);
                break;
            case MUSIC:
                text += context.getString(R.string.SoundSilencedMusic);
                break;
            case SETTINGS:
                text += context.getString(R.string.SoundSilencedSettings);
                break;
        }
        text += "\n";
        text += context.getResources().getString(R.string.ToastTime, Alarm.format2412ap(context, time));

        Toast t = Toast.makeText(context, text.trim(), Toast.LENGTH_SHORT);
        TextView v = (TextView) t.getView().findViewById(android.R.id.message);
        if (v != null)
            v.setGravity(Gravity.CENTER);
        t.show();
    }

    MediaPlayer create(Uri uri) {
        if (Build.VERSION.SDK_INT >= 21) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(SOUND_CHANNEL)
                    .setContentType(SOUND_TYPE)
                    .build();

            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int audioSessionId = am.generateAudioSessionId();

            try {
                MediaPlayer mp = new MediaPlayer();
                final AudioAttributes aa = audioAttributes != null ? audioAttributes : new AudioAttributes.Builder().build();
                mp.setAudioAttributes(aa);
                mp.setAudioSessionId(audioSessionId);
                mp.setAudioStreamType(SOUND_STREAM);
                mp.setDataSource(context, uri);
                mp.prepare();
                return mp;
            } catch (IOException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            } catch (SecurityException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            }
            return null;
        } else {
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(context, uri);
                mp.setAudioStreamType(SOUND_STREAM);
                mp.prepare();
                return mp;
            } catch (IOException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            } catch (SecurityException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            }
            return null;
        }
    }

    // called from reminder or test sound button
    public MediaPlayer playOnce(Uri uri, final Runnable done) {
        Sound.this.done.add(done);

        MediaPlayer player = create(uri);
        if (player == null) {
            player = create(ReminderSet.DEFAULT_NOTIFICATION);
        }
        if (player == null) {
            long dur = tonePlay();
            if (done != null)
                handler.postDelayed(done, dur);
            return null;
        }

        // https://code.google.com/p/android/issues/detail?id=1314
        player.setLooping(false);

        final MediaPlayer p = player;
        loop = new Runnable() {
            int last = 0;

            @Override
            public void run() {
                int pos = p.getCurrentPosition();
                if (pos < last) {
                    playerCl();
                    if (done != null && Sound.this.done.contains(done))
                        done.run();
                    return;
                }
                last = pos;
                handler.postDelayed(loop, 200);
            }
        };
        loop.run();

        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                           @Override
                                           public void onCompletion(MediaPlayer mp) {
                                               playerCl();
                                               if (done != null)
                                                   done.run();
                                           }
                                       }
        );

        startPlayer(player);

        return player;
    }

    public void vibrate() {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(400);
    }

    public void vibrateStart() {
        long[] pattern = {0, 1000, 300};
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(pattern, 0);
    }

    public void vibrateStop() {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.cancel();
    }

    void playerCl() {
        if (increaseVolume != null) {
            increaseVolume.stop();
            increaseVolume = null;
        }

        if (loop != null) {
            handler.removeCallbacks(loop);
            loop = null;
        }

        if (toneLoop != null) {
            handler.removeCallbacks(toneLoop);
            toneLoop = null;
        }

        if (tone != null) {
            tone.stopTone();
            tone.release();
            tone = null;
        }

        if (player != null) {
            player.release();
            player = null;
        }
    }

    public void playerClose() {
        playerCl();
        done.clear();
    }

    public Silenced playAlarm(final Alarm a) {
        return playAlarm(new FireAlarmService.FireAlarm(a));
    }

    public Silenced playAlarm(final FireAlarmService.FireAlarm alarm) {
        playerClose();

        final Playlist rr = alarm.list;

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        Silenced s = silencedPlaylist(alarm.list);

        // do we have slince alarm?
        if (s != Silenced.NONE) {
            if (s == Silenced.VIBRATE)
                vibrate();
            return s;
        }

        if (shared.getBoolean(HourlyApplication.PREFERENCE_VIBRATE, false)) {
            vibrate();
        }

        final Runnable restart = new Runnable() {
            @Override
            public void run() {
                final Runnable restart = this;
                done.add(restart);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (done.contains(restart)) {
                            playAlarm(alarm);
                        }
                    }
                }, 1000);
            }
        };

        final Runnable after = new Runnable() {
            @Override
            public void run() {
                if (!rr.after.isEmpty()) {
                    if (rr.before.isEmpty() && rr.after.size() == 1) { // do not loop sounds
                        playRingtone(Uri.parse(rr.after.get(0)));
                    } else {
                        playCustom(rr.after, restart);
                    }
                } else {
                    restart.run();
                }
            }
        };

        final Runnable afterOnce = new Runnable() {
            @Override
            public void run() {
                if (!rr.afterOnce.isEmpty()) {
                    playCustom(rr.afterOnce, after);
                } else {
                    after.run();
                }
            }
        };

        final Runnable speech = new Runnable() {
            @Override
            public void run() {
                if (rr.speech) {
                    playSpeech(System.currentTimeMillis(), afterOnce);
                } else {
                    afterOnce.run();
                }
            }
        };

        final Runnable beep = new Runnable() {
            @Override
            public void run() {
                if (rr.beep) {
                    playBeep(speech);
                } else {
                    speech.run();
                }
            }
        };

        final Runnable before = new Runnable() {
            @Override
            public void run() {
                if (!rr.before.isEmpty()) {
                    playCustom(rr.before, beep);
                } else {
                    beep.run();
                }
            }
        };

        final Runnable beforeOnce = new Runnable() {
            @Override
            public void run() {
                if (!rr.beforeOnce.isEmpty()) {
                    playCustom(rr.beforeOnce, before);
                } else {
                    before.run();
                }
            }
        };

        beforeOnce.run();
        return s;
    }

}
