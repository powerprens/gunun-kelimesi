package com.yusaa.gununkelimesi;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.Button;

/** Pure OLED dark theme colors and programmatic view styling helpers. */
public class UiStyle {
    public static final int BG_BLACK = 0xFF000000;
    public static final int SURFACE = 0xFF101010;
    public static final int SURFACE_ALT = 0xFF171717;
    public static final int BORDER = 0xFF2C2C2C;
    public static final int DIVIDER = 0xFF1C1C1C;

    public static final int AMBER = 0xFFFFB300;
    public static final int AMBER_DIM = 0xFFCC8A00;
    public static final int AMBER_BG = 0xFF241A00;

    public static final int GREEN = 0xFF4ADE80;
    public static final int GREEN_BORDER = 0xFF22C55E;
    public static final int GREEN_BG = 0xFF0A2918;

    public static final int RED = 0xFFFCA5A5;
    public static final int RED_BORDER = 0xFFEF4444;
    public static final int RED_BG = 0xFF350E11;

    public static final int ORANGE = 0xFFFF9800;

    public static final int TEXT_WHITE = 0xFFFFFFFF;
    public static final int TEXT_SEC = 0xFF999999;
    public static final int TEXT_MUTED = 0xFF555555;

    public static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    public static Drawable pill(Context ctx, int fill, int stroke, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(ctx, radiusDp));
        if (stroke != 0) {
            gd.setStroke(Math.max(1, dp(ctx, 1)), stroke);
        }
        return gd;
    }

    public static Drawable ripplePill(Context ctx, int fill, int stroke, int radiusDp, int rippleColor) {
        Drawable content = pill(ctx, fill, stroke, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    public static Button button(Context ctx, String text, int textSp, int textColor,
                                int fill, int stroke, int radiusDp) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setTextSize(textSp);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setGravity(Gravity.CENTER);
        int hp = dp(ctx, 12);
        int vp = dp(ctx, 10);
        b.setPadding(hp, vp, hp, vp);
        styleButton(ctx, b, textColor, fill, stroke, radiusDp);
        return b;
    }

    public static void styleButton(Context ctx, Button b, int textColor,
                                   int fill, int stroke, int radiusDp) {
        b.setTextColor(textColor);
        b.setBackground(ripplePill(ctx, fill, stroke, radiusDp, 0x33FFFFFF));
    }
}
