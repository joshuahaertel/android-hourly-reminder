package com.github.axet.hourlyreminder.app;

import android.content.ContentResolver;
import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import com.github.axet.hourlyreminder.alarms.Alarm;
import com.github.axet.hourlyreminder.alarms.Reminder;
import com.github.axet.hourlyreminder.alarms.ReminderSet;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

public class Storage extends com.github.axet.androidlibrary.app.Storage {
    public static final String TAG = Storage.class.getSimpleName();
    public static final String RINGTONES = "ringtones";

    HashMap<Uri, String> titles = new HashMap<>();

    public Storage(Context context) {
        super(context);
    }

    public File storeRingtone(Uri uri) {
        File dir = new File(context.getApplicationInfo().dataDir, RINGTONES);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("unable to create: " + dir);
        }

        List<Alarm> alarms = HourlyApplication.loadAlarms(context);
        List<ReminderSet> reminders = HourlyApplication.loadReminders(context);

        for (File child : dir.listFiles()) {
            Uri u = Uri.fromFile(child);
            boolean delete = true;
            for (Alarm a : alarms) {
                if (a.ringtoneValue.equals(u))
                    delete = false;
            }
            for (ReminderSet r : reminders) {
                if (r.ringtoneValue.equals(u))
                    delete = false;
            }
            if (delete)
                child.delete();
        }

        try {
            File dst;
            String t = getTitle(uri);
            if (t == null) {
                dst = File.createTempFile("ringtone_", ".tmp", dir);
            } else {
                File title = new File(t);
                dst = new File(dir, title.getName());
            }
            ContentResolver cr = context.getContentResolver();
            InputStream in = cr.openInputStream(uri);
            OutputStream out = new FileOutputStream(dst);
            IOUtils.copy(in, out);
            in.close();
            out.close();
            return dst;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getTitle(Uri uri) {
        if (uri == null)
            return null;

        String s = uri.getScheme();
        if (s == null || s.equals(ContentResolver.SCHEME_FILE)) {
            File f = new File(uri.getPath());
            return f.getName();
        }

        String a = uri.getAuthority();
        if (Build.VERSION.SDK_INT >= 21 && a.startsWith(Storage.SAF)) {
            return getTargetName(uri);
        }

        String title = titles.get(uri);
        if (title != null)
            return title;
        Ringtone rt = RingtoneManager.getRingtone(context, uri);
        if (rt == null)
            return null;
        try {
            title = rt.getTitle(context);
        } catch (SecurityException e) {
            return null;
        } finally {
            rt.stop();
        }
        titles.put(uri, title);
        return title;
    }
}
