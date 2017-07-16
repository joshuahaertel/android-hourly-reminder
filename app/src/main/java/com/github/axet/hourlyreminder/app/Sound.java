package com.github.axet.hourlyreminder.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.sound.AudioTrack;
import com.github.axet.androidlibrary.sound.FadeVolume;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.alarms.Alarm;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.alarms.WeekSet;
import com.github.axet.hourlyreminder.dialogs.BeepPrefDialogFragment;
import com.github.axet.hourlyreminder.services.FireAlarmService;
import com.github.axet.hourlyreminder.widgets.VibratePreference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Sound extends TTS {
    public static final String TAG = Sound.class.getSimpleName();

    ToneGenerator tone;
    Runnable toneLoop;
    MediaPlayer player;
    AudioTrack track;
    long[] vibrateTrack;
    Runnable vibrateEnd = new Runnable() {
        @Override
        public void run() {
            handler.removeCallbacks(vibrateEnd);
            vibrateTrack = null;
        }
    };
    FadeVolume increaseVolume;
    Runnable loop; // loop preventer

    // https://gist.github.com/slightfoot/6330866
    public static AudioTrack generateTone(double hz, int dur) {
        int rate = com.github.axet.androidlibrary.sound.Sound.getValidAudioRate(SOUND_CHANNELS, SOUND_SAMPLERATE);
        if (rate == -1)
            throw new RuntimeException("Unable to find proper audio attrs");
        int count = rate * dur / 1000; // samples count
        int last = count - 1; // last sample index
        int stereo = count * 2; // total actual samples count
        AudioTrack.AudioBuffer buf = new AudioTrack.AudioBuffer(rate, SOUND_CHANNELS, com.github.axet.androidlibrary.sound.Sound.DEFAULT_AUDIOFORMAT, stereo);
        for (int i = 0; i < count; i++) {
            double sx = 2 * Math.PI * i / (rate / hz);
            short sample = (short) (Math.sin(sx) * 0x7FFF);
            buf.write(i * 2, sample, sample);
        }
        AudioTrack track = AudioTrack.create(SOUND_STREAM, SOUND_CHANNEL, SOUND_TYPE, buf);
        track.setNotificationMarkerPosition(last);
        return track;
    }

    public static class Playlist {
        public List<Uri> beforeOnce = new ArrayList<>();
        public List<Uri> before = new ArrayList<>();
        public boolean beep;
        public boolean speech;
        public List<Uri> afterOnce = new ArrayList<>();
        public List<Uri> after = new ArrayList<>();

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

        void add(List<Uri> l, Uri s) {
            if (l.contains(s))
                return;
            l.add(s);
        }

        ArrayList<Uri> load(JSONArray aa) {
            ArrayList<Uri> l = new ArrayList<>();
            try {
                if (aa != null) {
                    for (int i = 0; i < aa.length(); i++) {
                        String s = aa.getString(i);
                        Uri u;
                        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                            u = Uri.parse(s);
                        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                            u = Uri.parse(s);
                        } else {
                            u = Uri.fromFile(new File(s));
                        }
                        l.add(u);
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

        if (vibrateTrack != null)
            vibrateStop();

        playerClose();

        if (track != null) {
            track.release();
            track = null;
        }
    }

    public Silenced silencedPlaylist(VibratePreference.Config config, Playlist rr) {
        Silenced ss = silenced(config);

        if (ss != Silenced.NONE)
            return ss;

        boolean v = config.reminders;
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
        VibratePreference.Config config = VibratePreference.loadConfig(context);

        Silenced ss = silenced(config);

        if (ss != Silenced.NONE)
            return ss;


        boolean v = config.alarms;
        boolean c = a.ringtone;
        boolean s = a.speech;
        boolean b = a.beep;

        if (!v && !c && !s && !b)
            return Silenced.SETTINGS;

        if (v && !c && !s && !b)
            return Silenced.VIBRATE;

        return Silenced.NONE;
    }

    public Silenced silenced(VibratePreference.Config config) {
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

        if (shared.getBoolean(HourlyApplication.PREFERENCE_PHONESILENCE, false)) {
            AudioManager tm = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int mode = tm.getRingerMode();
            if (mode != AudioManager.RINGER_MODE_NORMAL) {
                if (mode == AudioManager.RINGER_MODE_VIBRATE) { // phone in vibrate mode
                    boolean v = config.isChecked();
                    if (v) { // if vibrate enabled
                        return Silenced.VIBRATE;
                    }
                }
                return Silenced.SETTINGS;
            }
        }

        return Silenced.NONE;
    }

    public Silenced playList(final Playlist rr, final long time, final Runnable done) {
        playerClose();

        VibratePreference.Config config = VibratePreference.loadConfig(context);

        Silenced s = silencedPlaylist(config, rr);

        // do we have slince alarm?
        if (s != Silenced.NONE) {
            if (s == Silenced.VIBRATE)
                vibrate(config.remindersPattern);
            if (done != null) {
                done.run();
            }
            return s;
        }

        if (config.reminders) {
            vibrate(config.remindersPattern);
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

    public void playCustom(List<Uri> uu, final Runnable done) {
        playCustom(uu, 0, done);
    }

    public void playCustom(final List<Uri> uu, final int index, final Runnable done) {
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

    public void playCustom(Uri uri, final Runnable done) {
        playerCl();

        Sound.this.done.add(done);

        player = playOnce(uri, new Runnable() {
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

        try {
            AudioTrack t = generateTone(beep.value_f, beep.value_l);
            playBeep(t, done);
        } catch (RuntimeException e) {
            Log.d(TAG, "Unable get track", e);
            toastTone(e);
            try {
                MediaPlayer player = create(ReminderSet.DEFAULT_NOTIFICATION); // first fallback to system media player
                this.player = playOnce(player, done);
            } catch (RuntimeException ee) { // second fallback to tone (samsung phones crahes on tone native initialization (seems like some AudioTrack initialization failed)
                Log.d(TAG, "Unable get tone", e);
                toastTone(ee);
                long dur = tonePlayBeep();
                Runnable end = new Runnable() {
                    @Override
                    public void run() {
                        toneClose();
                        if (done != null)
                            done.run();
                    }
                };
                handler.postDelayed(end, dur); // length of tone
            }
        }
    }

    int getToneVolume() {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        float systemVolume = am.getStreamVolume(SOUND_STREAM) / (float) am.getStreamMaxVolume(SOUND_STREAM);
        systemVolume = unreduce(systemVolume);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        float alarmVolume = shared.getFloat(HourlyApplication.PREFERENCE_VOLUME, 1f);
        return (int) (100 * systemVolume * alarmVolume);
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

        track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(android.media.AudioTrack t) {
                end.run();
            }

            @Override
            public void onPeriodicNotification(android.media.AudioTrack track) {
            }
        });

        track.play();
    }

    // called from alarm
    public void playRingtone(Uri uri) {
        playerClose();

        try {
            player = create(uri);
        } catch (RuntimeException e) {
            Log.d(TAG, "unable to get ringtone", e);
            toastTone(e);
            try {
                player = create(Alarm.DEFAULT_ALARM);
            } catch (RuntimeException ee) { // last resort fallback
                Log.d(TAG, "unable to get default ringtone", e);
                toastTone(ee);
                toneLoop = new Runnable() {
                    @Override
                    public void run() {
                        long dur = tonePlay();
                        handler.removeCallbacks(toneLoop);
                        handler.postDelayed(toneLoop, dur); // length of tone
                    }
                };
                toneLoop.run();
                return;
            }
        }

        player.setLooping(true);
        startPlayer(player);
    }

    void toastTone(Throwable e) {
        String str = "";
        if (e != null) {
            while (e.getCause() != null)
                e = e.getCause();
            str = e.getMessage();
            if (str == null || str.isEmpty())
                str = e.getClass().getSimpleName();
        }
        Toast.makeText(context, "MediaPlayer init failed, fallback to Tone " + str, Toast.LENGTH_SHORT).show();
    }

    long tonePlay() {
        if (tone != null)
            tone.release();
        tone = new ToneGenerator(SOUND_STREAM, getToneVolume());
        tone.startTone(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL);
        return 4000;
    }

    long tonePlayBeep() {
        if (tone != null)
            tone.release();
        tone = new ToneGenerator(SOUND_STREAM, getToneVolume());
        tone.startTone(ToneGenerator.TONE_SUP_ERROR);
        return 330;
    }

    void toneClose() {
        if (tone != null) {
            tone.stopTone();
            tone.release();
            tone = null;
        }
        handler.removeCallbacks(toneLoop);
        toneLoop = null;
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
        float systemVolume = am.getStreamVolume(SOUND_STREAM) / (float) am.getStreamMaxVolume(SOUND_STREAM);
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

    MediaPlayer create(Uri uri) { // MediaPlayer.create expand
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
                throw new RuntimeException(ex);
            }
        } else {
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(context, uri);
                mp.setAudioStreamType(SOUND_STREAM);
                mp.prepare();
                return mp;
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    // called from reminder or test sound button
    public MediaPlayer playOnce(Uri uri, final Runnable done) {
        Sound.this.done.add(done);

        MediaPlayer player;
        try {
            player = create(uri);
        } catch (RuntimeException e) {
            Log.d(TAG, "failed get notification", e);
            toastTone(e);
            try {
                player = create(ReminderSet.DEFAULT_NOTIFICATION);
            } catch (RuntimeException ee) {
                Log.d(TAG, "failed get default notification", ee);
                toastTone(ee);
                long dur = tonePlay();
                Runnable end = new Runnable() {
                    @Override
                    public void run() {
                        toneClose();
                        if (done != null)
                            done.run();
                    }
                };
                handler.postDelayed(end, dur);
                return null;
            }
        }

        return playOnce(player, done);
    }

    MediaPlayer playOnce(MediaPlayer player, final Runnable done) {
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

    public void vibrate(String pattern) {
        long[] p = VibratePreference.patternLoad(pattern);
        vibrateStart(p, -1);
    }

    public void vibrateStart(String pattern, int repeat) {
        long[] p = VibratePreference.patternLoad(pattern);
        vibrateStart(p, repeat);
    }

    public void vibrateStart(long[] pattern, int repeat) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null)
            return;
        vibrateTrack = pattern;
        v.vibrate(vibrateTrack, repeat);
        if (repeat == -1) { // not repating? clear track, prevent vibrateorStop call twice
            long l = VibratePreference.patternLength(vibrateTrack);
            handler.postDelayed(vibrateEnd, l);
        }
    }

    public void vibrateStop() {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null)
            return;
        v.cancel();
        vibrateTrack = null;
        handler.removeCallbacks(vibrateEnd);
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

        toneClose();

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

        VibratePreference.Config config = VibratePreference.loadConfig(context);

        Silenced s = silencedPlaylist(config, alarm.list);

        // do we have slince alarm?
        if (s != Silenced.NONE) {
            if (s == Silenced.VIBRATE)
                vibrateStart(config.alarmsPattern, 0);
            return s;
        }

        if (config.alarms) {
            vibrateStart(config.alarmsPattern, 0);
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
                        playRingtone(rr.after.get(0));
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
