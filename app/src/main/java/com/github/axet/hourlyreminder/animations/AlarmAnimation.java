package com.github.axet.hourlyreminder.animations;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.github.axet.androidlibrary.animations.MarginAnimation;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.hourlyreminder.R;

import org.w3c.dom.Text;

public class AlarmAnimation extends MarginAnimation {
    ListView list;

    View convertView;
    View bottom;
    View bottom_f;
    View bottom_s;
    View compact;
    View compact_f;
    View compact_s;

    boolean partial;
    Handler handler;

    // true if this animation was started simultaneously with expand animation.
    boolean collapse_multi = false;

    // if we have two concurrent animations on the same listview
    // the only one 'expand' should have control of showChild function.
    static AlarmAnimation atomicExpander;

    boolean animate;

    public static void apply(final ListView list, final View v, final boolean expand, final boolean animate) {
        apply(new LateCreator() {
            @Override
            public MarginAnimation create() {
                AlarmAnimation a = new AlarmAnimation(list, v, expand);
                a.animate = animate;
                if (expand)
                    atomicExpander = a;
                return a;
            }
        }, v, expand, animate);
    }

    public AlarmAnimation(ListView list, View v, boolean expand) {
        super(v.findViewById(R.id.alarm_detailed), expand);

        handler = new Handler();

        this.convertView = v;
        this.list = list;

        bottom = v.findViewById(R.id.alarm_bottom);
        bottom_f = v.findViewById(R.id.alarm_bottom_first);
        bottom_s = v.findViewById(R.id.alarm_bottom_second);
        compact = v.findViewById(R.id.alarm_compact);
        compact_f = v.findViewById(R.id.alarm_compact_first);
        compact_s = v.findViewById(R.id.alarm_compact_second);
    }

    public void init() {
        super.init();

        bottom.setVisibility(View.VISIBLE);
        bottom_s.setVisibility(expand ? View.INVISIBLE : View.VISIBLE);

        compact.setVisibility(View.VISIBLE);
        compact_s.setVisibility(expand ? View.VISIBLE : View.INVISIBLE);

        {
            final int paddedTop = list.getListPaddingTop();
            final int paddedBottom = list.getHeight() - list.getListPaddingTop() - list.getListPaddingBottom();

            partial = false;

            partial |= convertView.getTop() < paddedTop;
            partial |= convertView.getBottom() > paddedBottom;
        }

        if (!expand) {
            colorOff();
        }
    }

    @Override
    public void calc(final float i, Transformation t) {
        super.calc(i, t);

        float ii = expand ? i : 1 - i;

        if (Build.VERSION.SDK_INT >= 11) {
            compact_f.setAlpha(1 - ii);
            compact_s.setRotation(180 * ii);
            bottom_f.setAlpha(ii);
            bottom_s.setRotation(-180 + 180 * ii);
        }

        // ViewGroup will crash on null pointer without this post pone.
        // seems like some views are removed by RecyvingView when they
        // gone off screen.
        if (Build.VERSION.SDK_INT >= 19) {
            collapse_multi |= !expand && atomicExpander != null && !atomicExpander.hasEnded();
            if (collapse_multi) {
                // do not showChild;
            } else {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showChild(i);
                    }
                });
            }
        }
    }

    @TargetApi(19)
    void showChild(float i) {
        final int paddedTop = list.getListPaddingTop();
        final int paddedBottom = list.getHeight() - list.getListPaddingTop() - list.getListPaddingBottom();

        if (convertView.getTop() < paddedTop) {
            int off = convertView.getTop() - paddedTop;
            if (partial)
                off = (int) (off * i);
            list.scrollListBy(off);
        }

        if (convertView.getBottom() > paddedBottom) {
            int off = convertView.getBottom() - paddedBottom;
            if (partial)
                off = (int) (off * i);
            list.scrollListBy(off);
        }
    }

    @Override
    public void restore() {
        super.restore();
        if (Build.VERSION.SDK_INT >= 11) {
            bottom_f.setAlpha(1);
            bottom_s.setRotation(0);
            compact_f.setAlpha(1);
            compact_s.setRotation(0);
        }
    }

    @Override
    public void end() {
        super.end();

        bottom_s.setVisibility(View.VISIBLE);
        compact_s.setVisibility(View.VISIBLE);
        view.setVisibility(expand ? View.VISIBLE : View.GONE);
        compact.setVisibility(expand ? View.GONE : View.VISIBLE);
        bottom.setVisibility(expand ? View.VISIBLE : View.GONE);

        if (expand) {
            if (animate)
                colorOn();
            else
                colorEnd();
        }
    }

    void colorOff() {
        int color = 0xFFAAAAAA;
        Context context = convertView.getContext();
        TextView time = (TextView) convertView.findViewById(R.id.alarm_time);
        time.setClickable(false);
        time.setTextColor(ThemeUtils.getThemeColor(context, android.R.attr.textColorSecondary));
        GlowAnimation.restore(time);

        TextView everyT = (TextView) convertView.findViewById(R.id.alarm_every);
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
        GlowAnimation a = new GlowAnimation(time) {
            @Override
            public void end() {
                super.end();
                colorEnd();
            }
        };
        a.startAnimation(time);

        final TextView every = (TextView) convertView.findViewById(R.id.alarm_every);
        if (every != null) {
            GlowAnimation aa = new GlowAnimation(every) {
                @Override
                public void end() {
                    super.end();
                    colorEnd();
                }
            };
            aa.startAnimation(every);
        }

        TextView browse = (TextView) convertView.findViewById(R.id.alarm_ringtone_browse);
        if (browse != null) {
            GlowAnimation aa = new GlowAnimation(browse);
            aa.startAnimation(browse);
        }

        TextView ring = (TextView) convertView.findViewById(R.id.alarm_ringtone_value);
        if (ring != null) {
            GlowAnimation aa = new GlowAnimation(ring);
            aa.startAnimation(ring);
        }
    }

    void colorEnd() {
        final int acc = ThemeUtils.getThemeColor(convertView.getContext(), R.attr.colorAccent);
        final ImageView everyT = (ImageView) convertView.findViewById(R.id.alarm_every_image);
        final TextView every = (TextView) convertView.findViewById(R.id.alarm_every);
        final TextView time = (TextView) convertView.findViewById(R.id.alarm_time);
        if (time != null)
            time.setTextColor(acc);
        if (every != null)
            every.setTextColor(acc);
        if (everyT != null)
            everyT.setColorFilter(acc);
    }
}
