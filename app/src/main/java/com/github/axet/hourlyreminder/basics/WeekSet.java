package com.github.axet.hourlyreminder.basics;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;

public class WeekSet extends Week {
    // unique id
    public long id;
    // alarm with ringtone?
    public boolean ringtone;
    // uri or file
    public String ringtoneValue;
    // beep?
    public boolean beep;
    // speech time?
    public boolean speech;

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
        weekDaysValues = new ArrayList<>(Arrays.asList(Week.EVERYDAY));
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
        this.ringtoneValue = o.optString("ringtone_value", "");
        this.beep = o.getBoolean("beep");
        this.speech = o.getBoolean("speech");
    }

    public JSONObject save() {
        try {
            JSONObject o = super.save();
            o.put("id", this.id);
            o.put("ringtone", this.ringtone);
            o.put("ringtone_value", this.ringtoneValue);
            o.put("beep", this.beep);
            o.put("speech", this.speech);
            return o;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

}
