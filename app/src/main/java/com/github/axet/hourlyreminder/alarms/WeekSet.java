package com.github.axet.hourlyreminder.alarms;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WeekSet extends Week {
    public static final List<Integer> DEF_DAYS = new ArrayList<>(Arrays.asList(Week.EVERYDAY));

    public long id; // unique id
    public boolean ringtone; // alarm with ringtone?
    public Uri ringtoneValue; // uri or file
    public boolean beep; // beep?
    public boolean speech; // speech time?
    public String name; // name to show, or empty

    public WeekSet(WeekSet copy) {
        super(copy);
        id = copy.id;
        ringtone = copy.ringtone;
        ringtoneValue = copy.ringtoneValue;
        beep = copy.beep;
        speech = copy.speech;
    }

    public WeekSet(Context context) {
        super(context);
        this.id = System.currentTimeMillis();
        enabled = false;
        weekdaysCheck = true;
        weekDaysValues = DEF_DAYS;
        ringtone = true;
        beep = true;
        speech = true;
    }

    public WeekSet(Context context, String json) {
        super(context, json);
    }

    public void setEnable(boolean e) {
        this.enabled = e;
        if (e)
            setNext();
    }

    public boolean getEnable() {
        return enabled;
    }

    @Override
    public void load(JSONObject o) throws JSONException {
        super.load(o);
        this.id = o.getLong("id");
        this.ringtone = o.getBoolean("ringtone");
        String s = o.optString("ringtone_value", null);
        if (s == null || s.isEmpty()) {
            this.ringtoneValue = defaultRingtone();
        } else {
            Uri u;
            if (s.startsWith(ContentResolver.SCHEME_CONTENT))
                u = Uri.parse(s);
            else if (s.startsWith(ContentResolver.SCHEME_FILE))
                u = Uri.parse(s);
            else
                u = Uri.fromFile(new File(s));
            this.ringtoneValue = u;
        }
        this.beep = o.getBoolean("beep");
        this.speech = o.getBoolean("speech");
        this.name = o.optString("name");
    }

    public JSONObject save() {
        try {
            JSONObject o = super.save();
            o.put("id", this.id);
            o.put("ringtone", this.ringtone);
            String s = null;
            if (this.ringtoneValue != null)
                s = this.ringtoneValue.toString();
            o.put("ringtone_value", s);
            o.put("beep", this.beep);
            o.put("speech", this.speech);
            o.put("name", this.name);
            return o;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    protected Uri defaultRingtone() {
        return null;
    }
}
