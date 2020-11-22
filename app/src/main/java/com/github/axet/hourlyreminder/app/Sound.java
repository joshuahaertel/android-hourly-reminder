package com.github.axet.hourlyreminder.app;

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
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.github.axet.androidlibrary.app.NotificationManagerCompat;
import com.github.axet.androidlibrary.sound.AudioTrack;
import com.github.axet.androidlibrary.sound.FadeVolume;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.alarms.Alarm;
import com.github.axet.hourlyreminder.alarms.ReminderSet;
import com.github.axet.hourlyreminder.alarms.WeekSet;
import com.github.axet.hourlyreminder.services.FireAlarmService;
import com.github.axet.hourlyreminder.widgets.BeepPreference;
import com.github.axet.hourlyreminder.widgets.FlashPreference;
import com.github.axet.hourlyreminder.widgets.VibratePreference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Sound extends SoundConfig {
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
    FlashPreference.Flash flash;

    // https://gist.github.com/slightfoot/6330866
    public static AudioTrack generateTone(Channel c, double hz, int dur) {
        int rate = getValidAudioRate(SOUND_CHANNELS, SOUND_SAMPLERATE);
        if (rate == -1)
            throw new RuntimeException("Unable to find proper audio attrs");
        int count = rate * dur / 1000; // samples count
        int last = count - 1; // last sample index
        int cn = getChannelCount(SOUND_CHANNELS);
        int stereo = count * cn; // total actual samples count
        AudioTrack.AudioBuffer buf = new AudioTrack.AudioBuffer(rate, SOUND_CHANNELS, DEFAULT_AUDIOFORMAT, stereo);
        for (int i = 0; i < count; i++) {
            double sx = 2 * Math.PI * i / (rate / hz);
            short sample = (short) (Math.sin(sx) * 0x7FFF);
            buf.write(i * cn, sample, cn);
        }
        AudioTrack track = AudioTrack.create(c.streamType, c.usage, c.streamType, buf);
        track.setNotificationMarkerPosition(last);
        return track;
    }

    public static <T> JSONArray toJSONArray(List<T> list) {
        ArrayList<String> ll = new ArrayList<>();
        for (T u : list)
            ll.add(u.toString());
        return new JSONArray(ll);
    }

    public static ArrayList<Uri> toUriArray(JSONArray aa) {
        ArrayList<Uri> l = new ArrayList<>();
        try {
            if (aa != null) {
                for (int i = 0; i < aa.length(); i++) {
                    String s = aa.getString(i);
                    Uri u = Uri.parse(s);
                    l.add(u);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return l;
    }

    /**
     * Convert "s:1000,v:100" to android java pattern
     *
     * @param pattern
     * @return
     */
    public static long[] patternLoad(String pattern) {
        ArrayList<Long> list = new ArrayList<>();
        String current = "s"; // start from silence
        Long value = 0l;
        String[] ss = pattern.split(",");
        for (String s : ss) {
            String[] vv = s.split(":");
            String k = vv[0];
            String v = vv[1];
            if (k.equals(current)) {
                value += Long.parseLong(v);
            } else {
                list.add(value);
                current = k;
                value = Long.parseLong(v);
            }
        }
        if (value != 0)
            list.add(value);
        long[] r = new long[list.size()];
        for (int i = 0; i < r.length; i++)
            r[i] = list.get(i);
        return r;
    }

    public static long patternLength(long[] patttern) {
        long ll = 0;
        for (long l : patttern)
            ll += l;
        return ll;
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
                beforeOnce = toUriArray(o.optJSONArray("beforeOnce")); // opt for <= 2.1.4
                before = toUriArray(o.optJSONArray("before")); // opt for unknown old version
                beep = o.getBoolean("beep");
                speech = o.getBoolean("speech");
                afterOnce = toUriArray(o.optJSONArray("afterOnce")); // opt for <= 2.1.4
                after = toUriArray(o.optJSONArray("after")); // opt for unknown old version
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        public JSONObject save() {
            JSONObject o = new JSONObject();
            try {
                o.put("beforeOnce", toJSONArray(beforeOnce));
                o.put("before", toJSONArray(before));
                o.put("beep", beep);
                o.put("speech", speech);
                o.put("afterOnce", toJSONArray(afterOnce));
                o.put("after", toJSONArray(after));
                return o;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Sound(Context context) {
        super(context);
        flash = new FlashPreference.Flash(context);
    }

    public void close() {
        super.close();
        vibrateStop();
        playerClose();
        flashStop();
    }

    public Silenced silencedPlaylist(VibratePreference.Config flash, VibratePreference.Config config, Playlist rr) {
        Silenced ss = silenced(flash, config);

        if (ss != Silenced.NONE)
            return ss;

        boolean v = config.reminders || flash.reminders;
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
        VibratePreference.Config flashConfig = VibratePreference.loadConfig(context, HourlyApplication.PREFERENCE_FLASH);

        VibratePreference.Config config = VibratePreference.loadConfig(context, HourlyApplication.PREFERENCE_VIBRATE);

        Silenced ss = silenced(flashConfig, config);

        if (ss != Silenced.NONE)
            return ss;

        boolean v = config.alarms || flashConfig.alarms;
        boolean c = a.ringtone;
        boolean s = a.speech;
        boolean b = a.beep;

        if (!v && !c && !s && !b)
            return Silenced.SETTINGS;

        if (v && !c && !s && !b)
            return Silenced.VIBRATE;

        return Silenced.NONE;
    }

    public Silenced silenced(VibratePreference.Config flash, VibratePreference.Config config) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        if (shared.getBoolean(HourlyApplication.PREFERENCE_CALLSILENCE, false)) {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK)
                return Silenced.CALL;
        }

        if (shared.getBoolean(HourlyApplication.PREFERENCE_MUSICSILENCE, false)) {
            AudioManager tm = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (tm.isMusicActive())
                return Silenced.MUSIC;
        }

        if (shared.getBoolean(HourlyApplication.PREFERENCE_PHONESILENCE, false)) {
            AudioManager tm = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int mode = tm.getRingerMode();
            if (mode == AudioManager.RINGER_MODE_VIBRATE) { // phone in vibrate mode
                if (config.isChecked() || flash.isChecked()) // if vibrate enabled
                    return Silenced.VIBRATE;
                return Silenced.SETTINGS;
            }
            if (Build.VERSION.SDK_INT < 16) {
                int t = tm.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
                if (t == AudioManager.VIBRATE_SETTING_ON) {
                    if (config.isChecked() || flash.isChecked()) // if vibrate enabled
                        return Silenced.VIBRATE;
                }
                if (t == AudioManager.VIBRATE_SETTING_ONLY_SILENT && mode == AudioManager.RINGER_MODE_SILENT) {
                    if (config.isChecked() || flash.isChecked()) // if vibrate enabled
                        return Silenced.VIBRATE;
                    return Silenced.SETTINGS;
                }
            }
            if (mode == AudioManager.RINGER_MODE_SILENT)
                return Silenced.SETTINGS;
            // https://stackoverflow.com/questions/31387137/android-detect-do-not-disturb-status
            if (Build.VERSION.SDK_INT >= 17) {
                switch (getDNDMode()) {
                    case ZEN_MODE_OFF:
                        break;
                    case ZEN_MODE_IMPORTANT_INTERRUPTIONS:
                    case ZEN_MODE_NO_INTERRUPTIONS:
                    case ZEN_MODE_ALARMS:
                        return Silenced.SETTINGS;
                }
            }
        }

        return Silenced.NONE;
    }

    public Silenced playList(final Playlist rr, final long time, final Runnable done) {
        playerClose();

        VibratePreference.Config flashConfig = VibratePreference.loadConfig(context, HourlyApplication.PREFERENCE_FLASH);

        VibratePreference.Config config = VibratePreference.loadConfig(context, HourlyApplication.PREFERENCE_VIBRATE);

        Silenced s = silencedPlaylist(flashConfig, config, rr);

        if (config.reminders)
            vibrate(config.remindersPattern);

        if (flashConfig.reminders) {
            try {
                flash.start(flashConfig.remindersPattern);
            } catch (RuntimeException e) {
                Toast.Error(context, "Unable to use Flash", e);
            }
        }

        // do we have slince alarm?
        if (s != Silenced.NONE) {
            dones.add(done);
            done(done);
            return s;
        }

        final Runnable after = new Runnable() {
            @Override
            public void run() {
                if (!rr.after.isEmpty()) {
                    playCustom(rr.after, done);
                } else {
                    dones.add(done);
                    done(done);
                }
            }
        };

        final Runnable speech = new Runnable() {
            @Override
            public void run() {
                if (rr.speech)
                    playSpeech(time, after);
                else
                    after.run();
            }
        };

        final Runnable beep = new Runnable() {
            @Override
            public void run() {
                if (rr.beep)
                    playBeep(speech);
                else
                    speech.run();
            }
        };

        final Runnable before = new Runnable() {
            @Override
            public void run() {
                if (!rr.before.isEmpty())
                    playCustom(rr.before, beep);
                else
                    beep.run();
            }
        };

        before.run();
        return s;
    }

    public Silenced playReminder(final ReminderSet rs, final long time, final Runnable done) {
        return playList(new Playlist(rs), time, done);
    }

    public void playCustom(List<Uri> uu, final Runnable done) {
        dones.add(done);
        playCustom(uu, 0, done);
    }

    public void playCustom(final List<Uri> uu, final int index, final Runnable done) {
        if (index >= uu.size()) {
            done(done);
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

        dones.add(done);

        player = playOnce(uri, new Runnable() {
            @Override
            public void run() {
                done(done);
            }
        });
    }

    public void playBeep(final Runnable done) {
        beepClose();

        dones.add(done);

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String b = shared.getString(HourlyApplication.PREFERENCE_BEEP_CUSTOM, BeepPreference.BeepConfig.DEFAULT);

        BeepPreference.BeepConfig beep = new BeepPreference.BeepConfig();
        beep.load(b);

        try {
            AudioTrack t = generateTone(getSoundChannel(), beep.value_f, beep.value_l);
            playBeep(t, done);
        } catch (RuntimeException e1) {
            Log.d(TAG, "Unable get track", e1);
            toastTone(e1);
            try {
                playerCl();
                MediaPlayer p = create(ReminderSet.DEFAULT_NOTIFICATION); // first fallback to system media player
                player = playOnce(p, done);
            } catch (RuntimeException e2) { // second fallback to tone (samsung phones crashes on tone native initialization (seems like some AudioTrack initialization failed)
                Log.d(TAG, "Unable get tone", e2);
                toastTone(e2);
                Runnable end = new Runnable() {
                    @Override
                    public void run() {
                        toneClose();
                        done(done);
                    }
                };
                try {
                    long dur = tonePlayBeep();
                    handler.postDelayed(end, dur); // length of tone
                } catch (RuntimeException e3) {
                    Log.d(TAG, "Unable get tone", e3);
                    toastTone(e3);
                    notificationAlarm();
                    handler.postDelayed(end, 1000);
                }
            }
        }
    }

    int getToneVolume() {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        Channel c = getSoundChannel();
        float systemVolume = am.getStreamVolume(c.streamType) / (float) am.getStreamMaxVolume(c.streamType);
        systemVolume = unreduce(systemVolume);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        float alarmVolume = shared.getFloat(HourlyApplication.PREFERENCE_VOLUME, 1f);
        return (int) (100 * systemVolume * alarmVolume);
    }

    public void playBeep(AudioTrack t, final Runnable done) { // done should be added by caller
        beepClose();

        track = t;

        if (Build.VERSION.SDK_INT < 21)
            track.setStereoVolume(getVolume(), getVolume());
        else
            track.setVolume(getVolume());

        final Runnable end = new Runnable() {
            @Override
            public void run() {
                // prevent strange android bug, with second beep when connecting android to external usb audio source.
                // seems like this beep pushed to external audio source from sound cache.
                beepClose();
                done(done);
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
        playerCl();

        try {
            player = create(uri);
        } catch (RuntimeException e1) {
            Log.d(TAG, "unable to get the ringtone", e1);
            toastTone(e1);
            try {
                player = create(Alarm.DEFAULT_ALARM);
            } catch (RuntimeException e2) { // last resort fallback
                Log.d(TAG, "unable to get the default ringtone", e2);
                toastTone(e2);
                toneLoop = new Runnable() {
                    @Override
                    public void run() {
                        long dur = tonePlay();
                        handler.removeCallbacks(toneLoop);
                        handler.postDelayed(toneLoop, dur); // length of tone
                    }
                };
                try {
                    toneLoop.run();
                    return;
                } catch (RuntimeException e3) {
                    toneLoop = new Runnable() {
                        @Override
                        public void run() {
                            notificationAlarm();
                            handler.removeCallbacks(toneLoop);
                            handler.postDelayed(toneLoop, 1000);
                        }
                    };
                    toneLoop.run();
                    return;
                }
            }
        }

        player.setLooping(true);
        startPlayer(player);
    }

    void toastTone(Throwable e) {
        Toast.makeText(context, "MediaPlayer init failed, fallback to Tone " + ErrorDialog.toMessage(e), Toast.LENGTH_SHORT).show();
    }

    long tonePlay() {
        if (tone != null)
            tone.release();
        tone = new ToneGenerator(getSoundChannel().streamType, getToneVolume());
        tone.startTone(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL);
        return 4000;
    }

    long tonePlayBeep() {
        if (tone != null)
            tone.release();
        tone = new ToneGenerator(getSoundChannel().streamType, getToneVolume());
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
        Channel c = getSoundChannel();
        float systemVolume = am.getStreamVolume(c.streamType) / (float) am.getStreamMaxVolume(c.streamType);
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

        Toast.makeText(context, text.trim(), Toast.LENGTH_SHORT).center().show();
    }

    MediaPlayer create(Uri uri) { // MediaPlayer.create expand
        if (Build.VERSION.SDK_INT >= 21) {
            Channel c = getSoundChannel();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(c.usage)
                    .setContentType(c.ct)
                    .build();

            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int audioSessionId = am.generateAudioSessionId();

            try {
                MediaPlayer mp = new MediaPlayer();
                final AudioAttributes aa = audioAttributes != null ? audioAttributes : new AudioAttributes.Builder().build();
                mp.setAudioAttributes(aa);
                mp.setAudioSessionId(audioSessionId);
                mp.setAudioStreamType(getSoundChannel().streamType);
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
                mp.setAudioStreamType(getSoundChannel().streamType);
                mp.prepare();
                return mp;
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    // called from reminder or test sound button
    public MediaPlayer playOnce(Uri uri, final Runnable done) {
        dones.add(done);

        MediaPlayer player;
        try {
            player = create(uri);
        } catch (RuntimeException e1) {
            Log.d(TAG, "failed get notification", e1);
            toastTone(e1);
            try {
                player = create(ReminderSet.DEFAULT_NOTIFICATION);
            } catch (RuntimeException e2) {
                Log.d(TAG, "failed get default notification", e2);
                toastTone(e2);
                Runnable end = new Runnable() {
                    @Override
                    public void run() {
                        toneClose();
                        done(done);
                    }
                };
                try {
                    long dur = tonePlay();
                    handler.postDelayed(end, dur);
                } catch (RuntimeException e3) {
                    Log.d(TAG, "failed get default notification", e3);
                    toastTone(e3);
                    notificationAlarm();
                    handler.postDelayed(end, 1000);
                }
                return null;
            }
        }

        return playOnce(player, done);
    }

    MediaPlayer playOnce(final MediaPlayer player, final Runnable done) { // done should be added by caller
        player.setLooping(false); // https://code.google.com/p/android/issues/detail?id=1314

        final MediaPlayer.OnCompletionListener c = new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playerCl();
                done(done);
            }
        };

        loop = new Runnable() { // loop detector. mediaplayer has bug looping non looped sounds
            int last = 0;
            long delay;

            {
                delay = player.getDuration(); // we can't pool as fast as we want
                if (delay <= 0)
                    delay = 200; // also, mediaplayer has bug, which return unaccurate current playback position at first 400ms
            }

            @Override
            public void run() {
                int pos = player.getCurrentPosition();
                if (pos < last) {
                    c.onCompletion(player);
                    return;
                }
                last = pos;
                handler.postDelayed(loop, delay);
                delay = 200; // first run takes getDuration(), next 200 ms
            }
        };
        loop.run();

        player.setOnCompletionListener(c);

        startPlayer(player);

        return player;
    }

    public void vibrate(String pattern) {
        long[] p = patternLoad(pattern);
        vibrateStart(p, -1);
    }

    public long[] vibrateStart(String pattern, int repeat) {
        long[] p = patternLoad(pattern);
        vibrateStart(p, repeat);
        return p;
    }

    public void vibrateStart(long[] pattern, int repeat) {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null)
            return;
        vibrateTrack = pattern;
        v.vibrate(vibrateTrack, repeat);
        if (repeat == -1) { // not repating? clear track, prevent vibrateorStop call twice
            long l = patternLength(vibrateTrack);
            handler.postDelayed(vibrateEnd, l);
        }
    }

    public void vibrateStop() {
        if (vibrateTrack == null)
            return;
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null)
            return;
        v.cancel();
        vibrateTrack = null;
        handler.removeCallbacks(vibrateEnd);
    }

    public void flashStop() {
        try {
            flash.stop();
        } catch (RuntimeException e) {
            Toast.Error(context, "Unable to use Flash", e);
        }
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

        beepClose();
    }

    void beepClose() {
        if (track != null) {
            track.release();
            track = null;
        }
    }

    public void playerClose() {
        playerCl();
        dones.clear();
        exits.clear();
    }

    public Silenced playAlarm(final FireAlarmService.FireAlarm alarm, final long delay, final Runnable late) {
        playerClose();

        final Playlist rr = alarm.list;

        VibratePreference.Config flashConfig = VibratePreference.loadConfig(context, HourlyApplication.PREFERENCE_FLASH);

        VibratePreference.Config config = VibratePreference.loadConfig(context, HourlyApplication.PREFERENCE_VIBRATE);

        Silenced s = silencedPlaylist(flashConfig, config, alarm.list);

        if (config.alarms)
            vibrateStart(config.alarmsPattern, 0);

        if (flashConfig.alarms) {
            try {
                flash.start(flashConfig.alarmsPattern, 0);
            } catch (RuntimeException e) {
                Toast.Error(context, "Unable to use Flash", e);
            }
        }

        // is the alarm silenced?
        if (s != Silenced.NONE)
            return s;

        final Runnable restart = new Runnable() {
            @Override
            public void run() {
                final Runnable restart = this;
                dones.add(restart);
                done(late);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (dones.contains(restart)) {
                            playAlarm(alarm, delay, late);
                        }
                    }
                }, 1000);
            }
        };

        final Runnable after = new Runnable() {
            @Override
            public void run() {
                if (!rr.after.isEmpty()) {
                    dones.add(late);
                    if (rr.before.isEmpty() && rr.after.size() == 1) { // do not loop sounds
                        playRingtone(rr.after.get(0));
                    } else {
                        playCustom(rr.after, restart);
                    }
                    if (late != null) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                done(late);
                            }
                        }, delay);
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

    void notificationAlarm() {
        String t = context.getString(R.string.app_name);
        String c = context.getString(R.string.fallback_text);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_launcher_notification)
                .setContentTitle(t)
                .setContentText(c);
        HourlyApplication.from(context).channelErrors.apply(b);
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.notify(HourlyApplication.NOTIFICATION_FALLBACK_ICON, b.build());
    }

}
