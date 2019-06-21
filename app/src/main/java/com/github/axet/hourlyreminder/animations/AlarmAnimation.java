package com.github.axet.hourlyreminder.animations;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.axet.androidlibrary.animations.ExpandAnimation;
import com.github.axet.androidlibrary.animations.MarginAnimation;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.hourlyreminder.R;

public class AlarmAnimation extends ExpandAnimation {
    View bottom;
    View bottom_f;
    View compact;
    View compact_f;

    boolean animate;

    public static Animation apply(final RecyclerView list, final View v, final boolean expand, final boolean animate) {
        return apply(new LateCreator() {
            @Override
            public MarginAnimation create() {
                AlarmAnimation a = new AlarmAnimation(list, v, expand, animate);
                if (expand)
                    atomicExpander = a;
                return a;
            }
        }, v, expand, animate);
    }

    public AlarmAnimation(RecyclerView list, View v, boolean expand, boolean animate) {
        super(list, v, v.findViewById(R.id.alarm_detailed), v.findViewById(R.id.alarm_expand), expand);
        this.animate = animate;
        this.bottom = v.findViewById(R.id.alarm_bottom);
        this.bottom_f = v.findViewById(R.id.alarm_bottom_first);
        this.compact = v.findViewById(R.id.alarm_compact);
        this.compact_f = v.findViewById(R.id.alarm_compact_first);
    }

    public void init() {
        super.init();

        bottom.setVisibility(View.VISIBLE);
        compact.setVisibility(View.VISIBLE);

        if (!expand)
            colorOff();
    }

    @Override
    public void calc(final float i, Transformation t) {
        super.calc(i, t);

        float ii = expand ? i : 1 - i;

        ViewCompat.setAlpha(compact_f, 1 - ii);
        ViewCompat.setAlpha(bottom_f, ii);
    }

    @Override
    public void restore() {
        super.restore();
        ViewCompat.setAlpha(bottom_f, 1);
        ViewCompat.setAlpha(compact_f, 1);
    }

    @Override
    public void end() {
        super.end();

        view.setVisibility(expand ? View.VISIBLE : View.GONE);
        compact.setVisibility(expand ? View.GONE : View.VISIBLE);
        bottom.setVisibility(expand ? View.VISIBLE : View.GONE);

        if (expand) {
            if (animate)
                colorOn();
            else
                colorEnd();
        } else {
            colorOff();
        }
    }

    void colorOff() {
        int color = 0xFFAAAAAA;
        Context context = convertView.getContext();
        TextView time = (TextView) convertView.findViewById(R.id.alarm_time);
        time.setClickable(false);
        time.setTextColor(ThemeUtils.getThemeColor(context, android.R.attr.textColorSecondary));
        GlowAnimation.restore(time);

        TextView everyT = (TextView) convertView.findViewById(R.id.alarm_every_text);
        if (everyT != null) {
            GlowAnimation.restore(everyT);
            everyT.setTextColor(ThemeUtils.getThemeColor(context, android.R.attr.textColorSecondary));
        }
        ImageView every = (ImageView) convertView.findViewById(R.id.alarm_every_image);
        if (every != null) {
            every.clearAnimation();
            every.setColorFilter(color);
        }

        TextView browse = (TextView) convertView.findViewById(R.id.alarm_ringtone_browse);
        if (browse != null)
            GlowAnimation.restore(browse);

        TextView ring = (TextView) convertView.findViewById(R.id.alarm_ringtone_value);
        if (ring != null)
            GlowAnimation.restore(ring);
    }

    void colorOn() {
        final TextView time = (TextView) convertView.findViewById(R.id.alarm_time);
//        GlowAnimation a = new GlowAnimation(time) {
//            @Override
//            public void end() {
//                super.end();
//                colorEnd();
//            }
//        };
//        a.startAnimation(time);
        colorEnd();

        final TextView every = (TextView) convertView.findViewById(R.id.alarm_every_text);
        if (every != null) {
//            GlowAnimation aa = new GlowAnimation(every) {
//                @Override
//                public void end() {
//                    super.end();
//                    colorEnd();
//                }
//            };
//            aa.startAnimation(every);
            colorEnd();
        }

        TextView browse = (TextView) convertView.findViewById(R.id.alarm_ringtone_browse);
        if (browse != null) {
//            GlowAnimation aa = new GlowAnimation(browse);
//            aa.startAnimation(browse);
        }

        TextView ring = (TextView) convertView.findViewById(R.id.alarm_ringtone_value);
        if (ring != null) {
//            GlowAnimation aa = new GlowAnimation(ring);
//            aa.startAnimation(ring);
        }
    }

    void colorEnd() {
        final int acc = ThemeUtils.getThemeColor(convertView.getContext(), R.attr.colorAccent);
        final ImageView everyT = (ImageView) convertView.findViewById(R.id.alarm_every_image);
        final TextView every = (TextView) convertView.findViewById(R.id.alarm_every_text);
        final TextView time = (TextView) convertView.findViewById(R.id.alarm_time);
        if (time != null)
            time.setTextColor(acc);
        if (every != null)
            every.setTextColor(acc);
        if (everyT != null)
            everyT.setColorFilter(acc);
    }
}
