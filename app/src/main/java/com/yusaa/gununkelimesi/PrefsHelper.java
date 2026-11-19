package com.yusaa.gununkelimesi;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** SharedPreferences helper for favorites, learned stamps, daily streaks, and quiz stats. */
public class PrefsHelper {
    private static final String PREFS = "gk_prefs";
    private static final String KEY_FAVS = "favorites";
    private static final String KEY_LEARNED = "learned";
    private static final String KEY_STREAK = "streak_count";
    private static final String KEY_LAST_DAY = "streak_last_day";
    private static final String KEY_QUIZ_BEST = "quiz_best_combo";
    private static final String KEY_QUIZ_CORRECT = "quiz_total_correct";
    private static final String KEY_QUIZ_ANSWERED = "quiz_total_answered";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String en) {
        return en == null ? "" : en.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> getWordSet(Context ctx, String prefKey) {
        Set<String> raw = prefs(ctx).getStringSet(prefKey, null);
        return raw == null ? new HashSet<>() : new HashSet<>(raw);
    }

    private static boolean toggleWordInSet(Context ctx, String prefKey, String en) {
        String k = key(en);
        if (k.isEmpty()) {
            return false;
        }
        Set<String> copy = getWordSet(ctx, prefKey);
        boolean added;
        if (copy.contains(k)) {
            copy.remove(k);
            added = false;
        } else {
            copy.add(k);
            added = true;
        }
        prefs(ctx).edit().putStringSet(prefKey, copy).apply();
        return added;
    }

    public static Set<String> getFavorites(Context ctx) {
        return getWordSet(ctx, KEY_FAVS);
    }

    public static boolean isFavorite(Context ctx, String en) {
        String k = key(en);
        return !k.isEmpty() && getWordSet(ctx, KEY_FAVS).contains(k);
    }

    public static boolean toggleFavorite(Context ctx, String en) {
        return toggleWordInSet(ctx, KEY_FAVS, en);
    }

    public static int favoriteCount(Context ctx) {
        return getWordSet(ctx, KEY_FAVS).size();
    }

    public static Set<String> getLearned(Context ctx) {
        return getWordSet(ctx, KEY_LEARNED);
    }

    public static boolean isLearned(Context ctx, String en) {
        String k = key(en);
        return !k.isEmpty() && getWordSet(ctx, KEY_LEARNED).contains(k);
    }

    public static boolean toggleLearned(Context ctx, String en) {
        boolean added = toggleWordInSet(ctx, KEY_LEARNED, en);
        if (added) {
            recordStreakActivity(ctx);
        }
        return added;
    }

    public static int learnedCount(Context ctx) {
        return getWordSet(ctx, KEY_LEARNED).size();
    }

    public static void recordStreakActivity(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long today = LocalDate.now().toEpochDay();
        long last = p.getLong(KEY_LAST_DAY, -1L);
        int streak = p.getInt(KEY_STREAK, 0);

        if (last == today) {
            if (streak < 1) {
                streak = 1;
            }
        } else if (last == today - 1) {
            streak = streak + 1;
        } else {
            streak = 1;
        }

        p.edit()
                .putInt(KEY_STREAK, streak)
                .putLong(KEY_LAST_DAY, today)
                .apply();
    }

    public static int getStreak(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long today = LocalDate.now().toEpochDay();
        long last = p.getLong(KEY_LAST_DAY, -1L);
        if (last == today || last == today - 1) {
            return Math.max(0, p.getInt(KEY_STREAK, 0));
        }
        return 0;
    }

    public static boolean isStreakActiveToday(Context ctx) {
        long today = LocalDate.now().toEpochDay();
        return prefs(ctx).getLong(KEY_LAST_DAY, -1L) == today;
    }

    public static void recordQuizResult(Context ctx, boolean correct, int currentCombo) {
        SharedPreferences p = prefs(ctx);
        int answered = p.getInt(KEY_QUIZ_ANSWERED, 0) + 1;
        int totalCorrect = p.getInt(KEY_QUIZ_CORRECT, 0) + (correct ? 1 : 0);
        int best = Math.max(p.getInt(KEY_QUIZ_BEST, 0), currentCombo);
        p.edit()
                .putInt(KEY_QUIZ_ANSWERED, answered)
                .putInt(KEY_QUIZ_CORRECT, totalCorrect)
                .putInt(KEY_QUIZ_BEST, best)
                .apply();
    }

    public static int getQuizBestCombo(Context ctx) {
        return prefs(ctx).getInt(KEY_QUIZ_BEST, 0);
    }

    public static int getQuizTotalCorrect(Context ctx) {
        return prefs(ctx).getInt(KEY_QUIZ_CORRECT, 0);
    }

    public static int getQuizTotalAnswered(Context ctx) {
        return prefs(ctx).getInt(KEY_QUIZ_ANSWERED, 0);
    }
}
