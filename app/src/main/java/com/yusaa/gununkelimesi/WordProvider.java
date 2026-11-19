package com.yusaa.gununkelimesi;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class WordProvider {
    private static final String CACHE = "words-cache.json";
    private static final String REMOTE = "https://ingilizce.yusaa.com.tr/words.json";
    private static JSONArray cache = null;

    private static byte[] readAll(InputStream is) throws Exception {
        byte[] buf = new byte[65536];
        int r;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while ((r = is.read(buf)) > 0) {
            out.write(buf, 0, r);
        }
        is.close();
        return out.toByteArray();
    }

    private static synchronized JSONArray load(Context ctx) throws Exception {
        if (cache != null) {
            return cache;
        }
        File f = new File(ctx.getFilesDir(), CACHE);
        if (f.exists()) {
            FileInputStream fis = new FileInputStream(f);
            cache = new JSONArray(new String(readAll(fis), StandardCharsets.UTF_8));
            return cache;
        }
        InputStream is = ctx.getAssets().open("words.json");
        cache = new JSONArray(new String(readAll(is), StandardCharsets.UTF_8));
        return cache;
    }

    private static Word fallbackWord() {
        return new Word("hello", "merhaba", "Hello, world!", "/h\u0259\u02c8lo\u028a/", 0, 1);
    }

    private static Word parseWord(JSONObject o, int idx, int total) throws Exception {
        return new Word(o.getString("en"), o.getString("tr"), o.getString("ex"),
                o.optString("okunus", ""), idx, total);
    }

    public static Word today(Context ctx) {
        try {
            JSONArray arr = load(ctx);
            int total = arr.length();
            int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            int idx = day % total;
            return parseWord(arr.getJSONObject(idx), idx, total);
        } catch (Exception e) {
            return fallbackWord();
        }
    }

    public static List<Word> all(Context ctx) {
        List<Word> list = new ArrayList<>();
        try {
            JSONArray arr = load(ctx);
            int total = arr.length();
            for (int i = 0; i < total; i++) {
                list.add(parseWord(arr.getJSONObject(i), i, total));
            }
        } catch (Exception ignored) {
        }
        if (list.isEmpty()) {
            list.add(fallbackWord());
        }
        return list;
    }

    public static int todayIndex(Context ctx) {
        try {
            int total = load(ctx).length();
            if (total <= 0) {
                return 0;
            }
            int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            return day % total;
        } catch (Exception e) {
            return 0;
        }
    }

    public static Word get(Context ctx, int idx) {
        try {
            JSONArray arr = load(ctx);
            int total = arr.length();
            if (total <= 0) {
                return fallbackWord();
            }
            int safe = ((idx % total) + total) % total;
            return parseWord(arr.getJSONObject(safe), safe, total);
        } catch (Exception e) {
            return fallbackWord();
        }
    }

    public static int count(Context ctx) {
        try {
            int len = load(ctx).length();
            return len > 0 ? len : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    /** Sunucudan güncel listeyi indirir. Dönüş: 'guncel', 'guncellendi:N' veya 'hata'. */
    public static String updateFromNetwork(Context ctx) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(REMOTE);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            if (c.getResponseCode() != 200) {
                return "hata";
            }
            String fresh = new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(fresh);
            if (arr.length() == 0) {
                return "hata";
            }
            synchronized (WordProvider.class) {
                if (cache != null && cache.toString().equals(arr.toString())) {
                    return "guncel";
                }
                File f = new File(ctx.getFilesDir(), CACHE);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(fresh.getBytes(StandardCharsets.UTF_8));
                fos.close();
                cache = arr;
            }
            return "guncellendi:" + arr.length();
        } catch (Exception e) {
            return "hata";
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}
