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
import com.github.axet.androidlibrary.sound.TTS;
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

public class Player extends TTS {
    public static final String TAG = Player.class.getSimpleName();

    MediaPlayer player;
    Runnable loop; // loop preventer

    public Player(Context context) {
        super(context);
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

    Runnable playOncePrepare(final MediaPlayer player, final Runnable done) { // done should be added by caller
        player.setLooping(false); // https://code.google.com/p/android/issues/detail?id=1314

        final MediaPlayer.OnCompletionListener c = new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playerCl();
                done(done);
            }
        };

        Runnable loop = new Runnable() { // loop detector. mediaplayer has bug looping non looped sounds
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
                handler.postDelayed(this, delay);
                delay = 200; // first run takes getDuration(), next 200 ms
            }
        };

        player.setOnCompletionListener(c);

        return loop;
    }

    public void startVolumePlayer(MediaPlayer player, Runnable loop) {
        player.setVolume(getVolume(), getVolume());
        startPlayer(player, loop);
    }

    public void startPlayer(MediaPlayer player, Runnable loop) {
        if (loop != null) {
            this.loop = loop;
            loop.run();
        }
        this.player = player;
        player.start();
    }

    public void close() {
        super.close();
        playerClose();
    }

    void playerCl() {
        if (loop != null) {
            handler.removeCallbacks(loop);
            loop = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }

    public void playerClose() {
        playerCl();
        dones.clear();
        exits.clear();
    }
}
