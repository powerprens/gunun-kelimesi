package com.yusaa.gununkelimesi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class NotifyHelper {
    private static final String CH = "gunluk_kelime";

    public static void show(Context ctx, Word w) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH, "Günün Kelimesi", NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(ch);
            }
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                b = new Notification.Builder(ctx, CH);
            } else {
                b = new Notification.Builder(ctx);
            }
            b.setContentTitle(w.en + " \u2014 " + w.tr)
                    .setContentText(w.ex)
                    .setStyle(new Notification.BigTextStyle().bigText(w.tr + "\n" + w.ex))
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setOngoing(true);
            nm.notify(1, b.build());
        } catch (Exception ignored) {
        }
    }
}
