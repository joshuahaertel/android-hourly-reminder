package com.github.axet.hourlyreminder.widgets;

import android.content.Context;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.PreferenceManager;
import android.util.AttributeSet;

public class BeepPreference extends EditTextPreference {
    public BeepPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public BeepPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BeepPreference(Context context) {
        this(context, null);
    }

    public String getValues() {
        return getText();
    }

    public void setValues(String s) {
        setText(s);
    }

}
