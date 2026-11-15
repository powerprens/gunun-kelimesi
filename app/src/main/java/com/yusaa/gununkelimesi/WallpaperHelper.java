package com.yusaa.gununkelimesi;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WallpaperHelper {

    public static boolean applyLock(Context ctx, Word w) {
        try {
            int W = 1080;
            int H = 2400;
            Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.drawColor(Color.BLACK);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTextAlign(Paint.Align.CENTER);

            Locale tr = new Locale("tr");
            String date = new SimpleDateFormat("d MMMM yyyy", tr).format(new Date());

            float y = 660f;
            p.setColor(0xFF666666);
            p.setTextSize(40f);
            c.drawText("GÜNÜN KELİMESİ", W / 2f, y, p);
            y += 60f;
            p.setColor(0xFF444444);
            p.setTextSize(38f);
            c.drawText(date, W / 2f, y, p);
            y += 140f;

            p.setColor(0xFFFFB300);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setTextSize(120f);
            y = drawWrapped(c, w.en, W / 2f, y, p, W - 160) + 30f;

            if (w.okunus != null && !w.okunus.isEmpty()) {
                p.setTypeface(Typeface.DEFAULT);
                p.setColor(0xFFCC8A00);
                p.setTextSize(56f);
                y = drawWrapped(c, "(" + w.okunus + ")", W / 2f, y, p, W - 160) + 30f;
            } else {
                y += 20f;
            }

            p.setTypeface(Typeface.DEFAULT);
            p.setColor(Color.WHITE);
            p.setTextSize(64f);
            y = drawWrapped(c, w.tr, W / 2f, y, p, W - 160) + 50f;

            p.setColor(0xFF888888);
            p.setTextSize(36f);
            drawWrapped(c, "\u201C" + w.ex + "\u201D", W / 2f, y, p, W - 160);

            WallpaperManager wm = WallpaperManager.getInstance(ctx);
            if (Build.VERSION.SDK_INT >= 24) {
                wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK);
            } else {
                wm.setBitmap(bmp);
            }
            bmp.recycle();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static float drawWrapped(Canvas c, String text, float cx, float y, Paint p, int maxW) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String wd : words) {
            String t = line.length() == 0 ? wd : line + " " + wd;
            if (p.measureText(t) > maxW && line.length() > 0) {
                c.drawText(line.toString(), cx, y, p);
                y += p.getTextSize() * 1.25f;
                line = new StringBuilder(wd);
            } else {
                line = new StringBuilder(t);
            }
        }
        if (line.length() > 0) {
            c.drawText(line.toString(), cx, y, p);
            y += p.getTextSize() * 1.25f;
        }
        return y;
    }
}
