package com.github.axet.hourlyreminder.animations;

import android.view.View;
import android.view.animation.Transformation;
import android.widget.TextView;

import com.github.axet.androidlibrary.animations.StepAnimation;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.hourlyreminder.R;

public class GlowAccentAnimation extends GlowAnimation {
    public GlowAccentAnimation(TextView view) {
        super(view);
    }

    @Override
    public void startAnimation(View v) {
        super.startAnimation(v);
    }

    @Override
    public void calc(float i, Transformation t) {
        super.calc(i, t);
        float k;
        if (i > 0.5f) {
        } else {
            k = i * 2;
            text.setTextColor(((int) (k * 0xff) << 24 | accent));
        }
    }
}
