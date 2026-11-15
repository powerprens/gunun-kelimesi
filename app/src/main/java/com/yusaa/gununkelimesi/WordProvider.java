package com.yusaa.gununkelimesi;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

public class WordProvider {
    private static JSONArray cache = null;

    private static synchronized JSONArray load(Context ctx) throws Exception {
        if (cache != null) {
            return cache;
        }
        InputStream is = ctx.getAssets().open("words.json");
        byte[] buf = new byte[is.available()];
        int n = 0;
        int r;
        while ((r = is.read(buf, n, buf.length - n)) > 0) {
            n += r;
        }
        is.close();
        cache = new JSONArray(new String(buf, 0, n, StandardCharsets.UTF_8));
        return cache;
    }

    public static Word today(Context ctx) {
        try {
            JSONArray arr = load(ctx);
            int total = arr.length();
            int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            int idx = day % total;
            JSONObject o = arr.getJSONObject(idx);
            return new Word(o.getString("en"), o.getString("tr"), o.getString("ex"), idx, total);
        } catch (Exception e) {
            return new Word("hello", "merhaba", "Hello, world!", 0, 1);
        }
    }
}
