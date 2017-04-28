package com.github.axet.hourlyreminder.animations;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.TextView;

import com.github.axet.androidlibrary.animations.StepAnimation;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.hourlyreminder.R;

public class GlowAnimation extends StepAnimation {
    TextView text;
    int accent;

    public GlowAnimation(TextView view) {
        super(view, true);
        this.text = view;
        this.accent = 0x00ffffff & ThemeUtils.getThemeColor(view.getContext(), R.attr.colorAccent);
        setDuration(500);
    }

    public static void restore(TextView view) {
        Animation a = view.getAnimation();
        if (a == null)
            return;
        if (a instanceof GlowAnimation) {
            view.setShadowLayer(0, 0, 0, 0xffff0000);
        }
        view.clearAnimation();
    }

    @Override
    public void startAnimation(View v) {
        super.startAnimation(v);
        text.setShadowLayer(0, 0, 0, 0xffff0000);
    }

    @Override
    public void calc(float i, Transformation t) {
        super.calc(i, t);
        float k;
        if (i > 0.5f) {
            float m = (i - 0.5f) * 2;
            k = 1 - m;
        } else {
            k = i * 2;
        }
        text.setShadowLayer(25 * k, 0, 0, ((int) (k * 0xff) << 24 | accent)); // Blur radius out of 0-25 pixel bound
    }

    @Override
    public void end() {
        super.end();
        text.setShadowLayer(0, 0, 0, 0);
    }

    @Override
    public void restore() {
        super.restore();
    }
}
