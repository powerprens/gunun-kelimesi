package com.yusaa.gununkelimesi;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        root.addView(mk("\u25CF G\u00DCN\u00DCN KEL\u0130MES\u0130", 13, 0xFF888888));
        Word w = WordProvider.today(this);
        String date = new SimpleDateFormat("d MMMM yyyy, EEEE", new Locale("tr")).format(new Date());
        root.addView(mk(date, 13, 0xFF555555));

        TextView en = mk(w.en, 52, 0xFFFFB300);
        en.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(en);
        root.addView(mk(w.tr, 26, Color.WHITE));
        root.addView(mk("\u201C" + w.ex + "\u201D", 15, 0xFF999999));
        root.addView(mk((w.index + 1) + " / " + w.total, 12, 0xFF444444));

        Button btn = new Button(this);
        btn.setText("Kilidi \u015Fimdi g\u00FCncelle");
        btn.setOnClickListener(v -> {
            btn.setEnabled(false);
            new Thread(() -> {
                Word ww = WordProvider.today(this);
                boolean ok = WallpaperHelper.applyLock(this, ww);
                NotifyHelper.show(this, ww);
                runOnUiThread(() -> {
                    btn.setEnabled(true);
                    btn.setText(ok ? "Kilit g\u00FCncellendi \u2713" : "Olmad\u0131, tekrar dene");
                });
            }).start();
        });
        root.addView(btn);
        root.addView(mk("Her sabah 07:00'de kilit ekran\u0131n otomatik de\u011Fi\u015Fir.", 12, 0xFF555555));

        setContentView(root);
        Scheduler.schedule(this);
        NotifyHelper.show(this, w);
    }

    private TextView mk(String t, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, 12, 0, 12);
        return v;
    }
}
