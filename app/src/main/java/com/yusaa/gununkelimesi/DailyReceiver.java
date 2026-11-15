package com.yusaa.gununkelimesi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DailyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                Word w = WordProvider.today(ctx);
                WallpaperHelper.applyLock(ctx, w);
                NotifyHelper.show(ctx, w);
            } finally {
                pr.finish();
            }
        }).start();
    }
}
