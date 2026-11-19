package com.yusaa.gununkelimesi;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int MODE_ALL = 0;
    private static final int MODE_FAVS = 1;
    private static final int MODE_QUIZ = 2;

    private static final String STATE_MODE = "state_mode";
    private static final String STATE_ALL_IDX = "state_all_idx";
    private static final String STATE_FAV_IDX = "state_fav_idx";
    private static final String STATE_Q_CORRECT = "state_q_correct";
    private static final String STATE_Q_WRONG = "state_q_wrong";
    private static final String STATE_Q_COMBO = "state_q_combo";
    private static final String STATE_Q_FAVS_ONLY = "state_q_favs_only";

    private List<Word> allWords;
    private int todayIndex = 0;
    private int allIndex = 0;
    private int favIndex = 0;
    private int mode = MODE_ALL;

    private final QuizEngine quizEngine = new QuizEngine();
    private QuizEngine.Question currentQuestion = null;
    private boolean quizAnswered = false;
    private int quizCorrect = 0;
    private int quizWrong = 0;
    private int quizCombo = 0;
    private boolean quizFavsOnly = false;

    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private Word displayedWord = null;
    private AppUpdater appUpdater;

    // Header & Tabs
    private TextView streakBadge;
    private TextView learnedBadge;
    private Button tabAll;
    private Button tabFavs;
    private Button tabQuiz;

    // Word Panel
    private LinearLayout wordPanel;
    private TextView wordSubtitle;
    private LinearLayout emptyFavBox;
    private LinearLayout wordCardBox;
    private TextView learnedStamp;
    private TextView wordEn;
    private TextView wordOkunus;
    private TextView wordTr;
    private TextView wordEx;
    private TextView wordCounter;
    private Button btnSpeak;
    private Button btnFav;
    private Button btnLearned;
    private Button btnPrev;
    private Button btnToday;
    private Button btnNext;
    private Button btnLock;
    private Button btnUpdate;

    // Quiz Panel
    private LinearLayout quizPanel;
    private TextView quizScoreLine;
    private TextView quizStatsLine;
    private Button btnQuizAll;
    private Button btnQuizFavs;
    private Button btnQuizReset;
    private TextView quizWordEn;
    private TextView quizWordOkunus;
    private Button btnQuizSpeak;
    private final Button[] quizOptionBtns = new Button[4];
    private TextView quizFeedback;
    private Button btnQuizNext;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        Window wnd = getWindow();
        if (wnd != null) {
            wnd.setStatusBarColor(Color.BLACK);
            wnd.setNavigationBarColor(Color.BLACK);
        }

        allWords = WordProvider.all(this);
        todayIndex = WordProvider.todayIndex(this);
        allIndex = todayIndex;

        if (b != null) {
            mode = b.getInt(STATE_MODE, MODE_ALL);
            allIndex = clampIndex(b.getInt(STATE_ALL_IDX, todayIndex), allWords.size());
            favIndex = Math.max(0, b.getInt(STATE_FAV_IDX, 0));
            quizCorrect = Math.max(0, b.getInt(STATE_Q_CORRECT, 0));
            quizWrong = Math.max(0, b.getInt(STATE_Q_WRONG, 0));
            quizCombo = Math.max(0, b.getInt(STATE_Q_COMBO, 0));
            quizFavsOnly = b.getBoolean(STATE_Q_FAVS_ONLY, false);
        }

        initTts();
        buildUi();
        switchMode(mode);

        Scheduler.schedule(this);
        NotifyHelper.show(this, WordProvider.today(this));

        appUpdater = new AppUpdater(this);
        if (!appUpdater.resumePendingInstallIfReady(b == null) && b == null) {
            appUpdater.checkOnStartup();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_MODE, mode);
        outState.putInt(STATE_ALL_IDX, allIndex);
        outState.putInt(STATE_FAV_IDX, favIndex);
        outState.putInt(STATE_Q_CORRECT, quizCorrect);
        outState.putInt(STATE_Q_WRONG, quizWrong);
        outState.putInt(STATE_Q_COMBO, quizCombo);
        outState.putBoolean(STATE_Q_FAVS_ONLY, quizFavsOnly);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (appUpdater != null) {
            appUpdater.handleActivityResult(requestCode);
        }
    }

    @Override
    protected void onDestroy() {
        if (appUpdater != null) {
            appUpdater.detach();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (mode != MODE_ALL) {
            switchMode(MODE_ALL);
            return;
        }
        super.onBackPressed();
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                int res = tts.setLanguage(Locale.US);
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    res = tts.setLanguage(Locale.UK);
                }
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    res = tts.setLanguage(Locale.ENGLISH);
                }
                if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setSpeechRate(0.92f);
                    ttsReady = true;
                }
            }
        });
    }

    private void speak(String text) {
        if (!ttsReady || tts == null || text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "Sesli okuma haz\u0131r de\u011Fil", Toast.LENGTH_SHORT).show();
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gk_" + System.currentTimeMillis());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiStyle.BG_BLACK);

        root.addView(buildHeader());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(UiStyle.BG_BLACK);

        LinearLayout contentRoot = new LinearLayout(this);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        contentRoot.setBackgroundColor(UiStyle.BG_BLACK);
        int hp = UiStyle.dp(this, 20);
        int vp = UiStyle.dp(this, 16);
        contentRoot.setPadding(hp, vp, hp, UiStyle.dp(this, 24));

        wordPanel = buildWordPanel();
        quizPanel = buildQuizPanel();

        contentRoot.addView(wordPanel);
        contentRoot.addView(quizPanel);
        scroll.addView(contentRoot);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollLp);

        setContentView(root);
    }

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(UiStyle.BG_BLACK);
        int hp = UiStyle.dp(this, 16);
        int tp = UiStyle.dp(this, 14);
        int bp = UiStyle.dp(this, 10);
        header.setPadding(hp, tp, hp, bp);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = new TextView(this);
        brand.setText("\u25CF G\u00DCN\u00DCN KEL\u0130MES\u0130");
        brand.setTextSize(12);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setTextColor(UiStyle.TEXT_SEC);
        brand.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        topRow.addView(brand, brandLp);

        streakBadge = makeStatBadge();
        LinearLayout.LayoutParams streakLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        streakLp.setMargins(0, 0, UiStyle.dp(this, 6), 0);
        topRow.addView(streakBadge, streakLp);

        learnedBadge = makeStatBadge();
        topRow.addView(learnedBadge);

        header.addView(topRow);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tabRowLp.setMargins(0, UiStyle.dp(this, 12), 0, 0);

        tabAll = UiStyle.button(this, "Kelime", 13, UiStyle.TEXT_SEC,
                UiStyle.SURFACE, UiStyle.BORDER, 10);
        tabFavs = UiStyle.button(this, "\u2605 Favoriler (0)", 13, UiStyle.TEXT_SEC,
                UiStyle.SURFACE, UiStyle.BORDER, 10);
        tabQuiz = UiStyle.button(this, "Quiz", 13, UiStyle.TEXT_SEC,
                UiStyle.SURFACE, UiStyle.BORDER, 10);

        tabAll.setOnClickListener(v -> switchMode(MODE_ALL));
        tabFavs.setOnClickListener(v -> switchMode(MODE_FAVS));
        tabQuiz.setOnClickListener(v -> switchMode(MODE_QUIZ));

        tabRow.addView(tabAll, weightedTabParams(0, 4));
        tabRow.addView(tabFavs, weightedTabParams(4, 4));
        tabRow.addView(tabQuiz, weightedTabParams(4, 0));
        header.addView(tabRow, tabRowLp);

        View divider = new View(this);
        divider.setBackgroundColor(UiStyle.DIVIDER);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, UiStyle.dp(this, 1)));
        divLp.setMargins(0, UiStyle.dp(this, 12), 0, 0);
        header.addView(divider, divLp);

        return header;
    }

    private TextView makeStatBadge() {
        TextView tv = new TextView(this);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(UiStyle.dp(this, 10), UiStyle.dp(this, 4),
                UiStyle.dp(this, 10), UiStyle.dp(this, 4));
        return tv;
    }

    private LinearLayout.LayoutParams weightedTabParams(int leftDp, int rightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(UiStyle.dp(this, leftDp), 0, UiStyle.dp(this, rightDp), 0);
        return lp;
    }

    private LinearLayout buildWordPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);

        wordSubtitle = new TextView(this);
        wordSubtitle.setTextSize(13);
        wordSubtitle.setTextColor(UiStyle.TEXT_MUTED);
        wordSubtitle.setGravity(Gravity.CENTER);
        wordSubtitle.setPadding(0, UiStyle.dp(this, 4), 0, UiStyle.dp(this, 14));
        panel.addView(wordSubtitle);

        emptyFavBox = new LinearLayout(this);
        emptyFavBox.setOrientation(LinearLayout.VERTICAL);
        emptyFavBox.setGravity(Gravity.CENTER);
        emptyFavBox.setBackground(UiStyle.pill(this, UiStyle.SURFACE, UiStyle.BORDER, 16));
        int ePad = UiStyle.dp(this, 24);
        emptyFavBox.setPadding(ePad, ePad, ePad, ePad);

        TextView emptyMsg = new TextView(this);
        emptyMsg.setText("Hen\u00FCz favori kelimen yok.\nBe\u011Fendi\u011Fin kelimeleri \u2606 ile y\u0131ld\u0131zlayabilirsin.");
        emptyMsg.setTextSize(15);
        emptyMsg.setTextColor(UiStyle.TEXT_SEC);
        emptyMsg.setGravity(Gravity.CENTER);
        emptyMsg.setPadding(0, 0, 0, UiStyle.dp(this, 16));
        emptyFavBox.addView(emptyMsg);

        Button backAll = UiStyle.button(this, "T\u00FCm kelimelere d\u00F6n", 14, UiStyle.AMBER,
                UiStyle.AMBER_BG, UiStyle.AMBER, 10);
        backAll.setOnClickListener(v -> switchMode(MODE_ALL));
        emptyFavBox.addView(backAll);

        LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        emptyLp.setMargins(0, UiStyle.dp(this, 12), 0, UiStyle.dp(this, 20));
        panel.addView(emptyFavBox, emptyLp);

        wordCardBox = new LinearLayout(this);
        wordCardBox.setOrientation(LinearLayout.VERTICAL);
        wordCardBox.setGravity(Gravity.CENTER_HORIZONTAL);
        wordCardBox.setBackground(UiStyle.pill(this, UiStyle.SURFACE, UiStyle.BORDER, 18));
        int cPad = UiStyle.dp(this, 20);
        wordCardBox.setPadding(cPad, UiStyle.dp(this, 22), cPad, UiStyle.dp(this, 20));

        learnedStamp = new TextView(this);
        learnedStamp.setText("\u2713 \u00D6\u011EREN\u0130LD\u0130");
        learnedStamp.setTextSize(11);
        learnedStamp.setTypeface(Typeface.DEFAULT_BOLD);
        learnedStamp.setTextColor(UiStyle.GREEN);
        learnedStamp.setLetterSpacing(0.08f);
        learnedStamp.setBackground(UiStyle.pill(this, UiStyle.GREEN_BG, UiStyle.GREEN_BORDER, 20));
        learnedStamp.setPadding(UiStyle.dp(this, 12), UiStyle.dp(this, 4),
                UiStyle.dp(this, 12), UiStyle.dp(this, 4));
        LinearLayout.LayoutParams stampLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stampLp.setMargins(0, 0, 0, UiStyle.dp(this, 10));
        wordCardBox.addView(learnedStamp, stampLp);

        wordEn = new TextView(this);
        wordEn.setTextSize(44);
        wordEn.setTypeface(Typeface.DEFAULT_BOLD);
        wordEn.setTextColor(UiStyle.AMBER);
        wordEn.setGravity(Gravity.CENTER);
        wordCardBox.addView(wordEn);

        wordOkunus = new TextView(this);
        wordOkunus.setTextSize(18);
        wordOkunus.setTextColor(UiStyle.AMBER_DIM);
        wordOkunus.setGravity(Gravity.CENTER);
        wordOkunus.setPadding(0, UiStyle.dp(this, 4), 0, UiStyle.dp(this, 4));
        wordCardBox.addView(wordOkunus);

        wordTr = new TextView(this);
        wordTr.setTextSize(24);
        wordTr.setTextColor(UiStyle.TEXT_WHITE);
        wordTr.setGravity(Gravity.CENTER);
        wordTr.setPadding(0, UiStyle.dp(this, 10), 0, UiStyle.dp(this, 10));
        wordCardBox.addView(wordTr);

        wordEx = new TextView(this);
        wordEx.setTextSize(15);
        wordEx.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        wordEx.setTextColor(UiStyle.TEXT_SEC);
        wordEx.setGravity(Gravity.CENTER);
        wordEx.setPadding(UiStyle.dp(this, 6), UiStyle.dp(this, 4),
                UiStyle.dp(this, 6), UiStyle.dp(this, 10));
        wordEx.setOnClickListener(v -> {
            if (displayedWord != null) {
                speak(displayedWord.ex);
            }
        });
        wordCardBox.addView(wordEx);

        wordCounter = new TextView(this);
        wordCounter.setTextSize(12);
        wordCounter.setTextColor(UiStyle.TEXT_MUTED);
        wordCounter.setGravity(Gravity.CENTER);
        wordCounter.setPadding(0, UiStyle.dp(this, 2), 0, UiStyle.dp(this, 14));
        wordCardBox.addView(wordCounter);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        btnSpeak = UiStyle.button(this, "\uD83D\uDD0A Dinle", 13, UiStyle.AMBER,
                UiStyle.SURFACE_ALT, UiStyle.BORDER, 10);
        btnFav = UiStyle.button(this, "\u2606 Favori", 13, UiStyle.TEXT_SEC,
                UiStyle.SURFACE_ALT, UiStyle.BORDER, 10);
        btnLearned = UiStyle.button(this, "\u00D6\u011Frendim \u2713", 13, UiStyle.TEXT_SEC,
                UiStyle.SURFACE_ALT, UiStyle.BORDER, 10);

        btnSpeak.setOnClickListener(v -> {
            if (displayedWord != null) {
                speak(displayedWord.en);
            }
        });
        btnFav.setOnClickListener(v -> onToggleFavorite());
        btnLearned.setOnClickListener(v -> onToggleLearned());

        actionRow.addView(btnSpeak, weightedTabParams(0, 4));
        actionRow.addView(btnFav, weightedTabParams(4, 4));
        actionRow.addView(btnLearned, weightedTabParams(4, 0));
        wordCardBox.addView(actionRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navLp.setMargins(0, UiStyle.dp(this, 10), 0, 0);

        btnPrev = UiStyle.button(this, "\u2190 \u00D6nceki", 13, UiStyle.TEXT_WHITE,
                UiStyle.SURFACE_ALT, UiStyle.BORDER, 10);
        btnToday = UiStyle.button(this, "Bug\u00FCn", 13, UiStyle.AMBER,
                UiStyle.AMBER_BG, UiStyle.AMBER_DIM, 10);
        btnNext = UiStyle.button(this, "Sonraki \u2192", 13, UiStyle.TEXT_WHITE,
                UiStyle.SURFACE_ALT, UiStyle.BORDER, 10);

        btnPrev.setOnClickListener(v -> stepWord(-1));
        btnToday.setOnClickListener(v -> {
            allIndex = todayIndex;
            refreshWordPanel();
        });
        btnNext.setOnClickListener(v -> stepWord(1));

        navRow.addView(btnPrev, weightedTabParams(0, 4));
        navRow.addView(btnToday, weightedTabParams(4, 4));
        navRow.addView(btnNext, weightedTabParams(4, 0));
        wordCardBox.addView(navRow, navLp);

        panel.addView(wordCardBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(UiStyle.DIVIDER);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, UiStyle.dp(this, 1)));
        divLp.setMargins(0, UiStyle.dp(this, 18), 0, UiStyle.dp(this, 14));
        panel.addView(divider, divLp);

        LinearLayout utilRow = new LinearLayout(this);
        utilRow.setOrientation(LinearLayout.HORIZONTAL);

        btnLock = UiStyle.button(this, "Kilidi g\u00FCncelle", 13, UiStyle.TEXT_SEC,
                UiStyle.SURFACE, UiStyle.BORDER, 10);
        btnUpdate = UiStyle.button(this, "Kelimeleri g\u00FCncelle", 13, UiStyle.TEXT_SEC,
                UiStyle.SURFACE, UiStyle.BORDER, 10);

        btnLock.setOnClickListener(v -> triggerLockUpdate());
        btnUpdate.setOnClickListener(v -> triggerWordsUpdate());

        utilRow.addView(btnLock, weightedTabParams(0, 4));
        utilRow.addView(btnUpdate, weightedTabParams(4, 0));
        panel.addView(utilRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView footer = new TextView(this);
        footer.setText("Her sabah 07:00'de kilit ekran\u0131n otomatik de\u011Fi\u015Fir.");
        footer.setTextSize(12);
        footer.setTextColor(UiStyle.TEXT_MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, UiStyle.dp(this, 14), 0, 0);
        panel.addView(footer);

        return panel;
    }

    private LinearLayout buildQuizPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout scoreCard = new LinearLayout(this);
        scoreCard.setOrientation(LinearLayout.VERTICAL);
        scoreCard.setBackground(UiStyle.pill(this, UiStyle.SURFACE, UiStyle.BORDER, 14));
        int sPad = UiStyle.dp(this, 14);
        scoreCard.setPadding(sPad, sPad, sPad, sPad);

        quizScoreLine = new TextView(this);
        quizScoreLine.setTextSize(14);
        quizScoreLine.setTypeface(Typeface.DEFAULT_BOLD);
        quizScoreLine.setTextColor(UiStyle.TEXT_WHITE);
        quizScoreLine.setGravity(Gravity.CENTER);
        scoreCard.addView(quizScoreLine);

        quizStatsLine = new TextView(this);
        quizStatsLine.setTextSize(12);
        quizStatsLine.setTextColor(UiStyle.TEXT_SEC);
        quizStatsLine.setGravity(Gravity.CENTER);
        quizStatsLine.setPadding(0, UiStyle.dp(this, 4), 0, UiStyle.dp(this, 10));
        scoreCard.addView(quizStatsLine);

        LinearLayout ctrlRow = new LinearLayout(this);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);

        btnQuizAll = UiStyle.button(this, "T\u00FCm Kelimeler", 12, UiStyle.AMBER,
                UiStyle.AMBER_BG, UiStyle.AMBER, 8);
        btnQuizFavs = UiStyle.button(this, "\u2605 Favoriler (0)", 12, UiStyle.TEXT_SEC,
                UiStyle.SURFACE_ALT, UiStyle.BORDER, 8);
        btnQuizReset = UiStyle.button(this, "S\u0131f\u0131rla", 12, UiStyle.TEXT_SEC,
                UiStyle.SURFACE_ALT, UiStyle.BORDER, 8);

        btnQuizAll.setOnClickListener(v -> {
            if (quizFavsOnly) {
                quizFavsOnly = false;
                quizEngine.resetDeck();
                loadNextQuizQuestion();
            }
        });
        btnQuizFavs.setOnClickListener(v -> {
            if (favoriteWords().isEmpty()) {
                Toast.makeText(this, "\u00D6nce en az 1 kelimeyi favorilere ekle", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!quizFavsOnly) {
                quizFavsOnly = true;
                quizEngine.resetDeck();
                loadNextQuizQuestion();
            }
        });
        btnQuizReset.setOnClickListener(v -> {
            quizCorrect = 0;
            quizWrong = 0;
            quizCombo = 0;
            quizEngine.resetDeck();
            loadNextQuizQuestion();
        });

        ctrlRow.addView(btnQuizAll, weightedTabParams(0, 4));
        ctrlRow.addView(btnQuizFavs, weightedTabParams(4, 4));
        ctrlRow.addView(btnQuizReset, weightedTabParams(4, 0));
        scoreCard.addView(ctrlRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scoreLp.setMargins(0, 0, 0, UiStyle.dp(this, 16));
        panel.addView(scoreCard, scoreLp);

        TextView prompt = new TextView(this);
        prompt.setText("T\u00FCrk\u00E7e anlam\u0131 nedir?");
        prompt.setTextSize(13);
        prompt.setTextColor(UiStyle.TEXT_SEC);
        prompt.setGravity(Gravity.CENTER);
        panel.addView(prompt);

        quizWordEn = new TextView(this);
        quizWordEn.setTextSize(38);
        quizWordEn.setTypeface(Typeface.DEFAULT_BOLD);
        quizWordEn.setTextColor(UiStyle.AMBER);
        quizWordEn.setGravity(Gravity.CENTER);
        quizWordEn.setPadding(0, UiStyle.dp(this, 6), 0, UiStyle.dp(this, 2));
        panel.addView(quizWordEn);

        quizWordOkunus = new TextView(this);
        quizWordOkunus.setTextSize(16);
        quizWordOkunus.setTextColor(UiStyle.AMBER_DIM);
        quizWordOkunus.setGravity(Gravity.CENTER);
        quizWordOkunus.setPadding(0, 0, 0, UiStyle.dp(this, 10));
        panel.addView(quizWordOkunus);

        btnQuizSpeak = UiStyle.button(this, "\uD83D\uDD0A Dinle", 13, UiStyle.AMBER,
                UiStyle.SURFACE_ALT, UiStyle.BORDER, 20);
        btnQuizSpeak.setPadding(UiStyle.dp(this, 18), UiStyle.dp(this, 8),
                UiStyle.dp(this, 18), UiStyle.dp(this, 8));
        btnQuizSpeak.setOnClickListener(v -> {
            if (currentQuestion != null) {
                speak(currentQuestion.word.en);
            }
        });
        LinearLayout.LayoutParams speakLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        speakLp.setMargins(0, 0, 0, UiStyle.dp(this, 16));
        panel.addView(btnQuizSpeak, speakLp);

        for (int i = 0; i < 4; i++) {
            final int optionIdx = i;
            Button opt = UiStyle.button(this, "", 15, UiStyle.TEXT_WHITE,
                    UiStyle.SURFACE, UiStyle.BORDER, 12);
            opt.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            opt.setMinimumHeight(UiStyle.dp(this, 50));
            opt.setMinHeight(UiStyle.dp(this, 50));
            opt.setPadding(UiStyle.dp(this, 16), UiStyle.dp(this, 12),
                    UiStyle.dp(this, 16), UiStyle.dp(this, 12));
            opt.setOnClickListener(v -> handleQuizAnswer(optionIdx));

            LinearLayout.LayoutParams optLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            optLp.setMargins(0, 0, 0, UiStyle.dp(this, 10));
            panel.addView(opt, optLp);
            quizOptionBtns[i] = opt;
        }

        quizFeedback = new TextView(this);
        quizFeedback.setTextSize(14);
        quizFeedback.setGravity(Gravity.CENTER);
        quizFeedback.setPadding(UiStyle.dp(this, 8), UiStyle.dp(this, 6),
                UiStyle.dp(this, 8), UiStyle.dp(this, 12));
        quizFeedback.setVisibility(View.GONE);
        panel.addView(quizFeedback);

        btnQuizNext = UiStyle.button(this, "Sonraki Soru \u2192", 15, UiStyle.AMBER,
                UiStyle.AMBER_BG, UiStyle.AMBER, 12);
        btnQuizNext.setPadding(UiStyle.dp(this, 16), UiStyle.dp(this, 14),
                UiStyle.dp(this, 16), UiStyle.dp(this, 14));
        btnQuizNext.setOnClickListener(v -> loadNextQuizQuestion());
        btnQuizNext.setVisibility(View.GONE);
        panel.addView(btnQuizNext, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return panel;
    }

    private void switchMode(int newMode) {
        mode = newMode;
        if (mode == MODE_QUIZ) {
            if (quizFavsOnly && favoriteWords().isEmpty()) {
                quizFavsOnly = false;
                quizEngine.resetDeck();
                currentQuestion = null;
            }
            if (currentQuestion == null) {
                loadNextQuizQuestion();
            }
        }
        refreshAllUi();
    }

    private void refreshAllUi() {
        refreshHeader();
        wordPanel.setVisibility(mode == MODE_QUIZ ? View.GONE : View.VISIBLE);
        quizPanel.setVisibility(mode == MODE_QUIZ ? View.VISIBLE : View.GONE);
        if (mode == MODE_QUIZ) {
            refreshQuizPanel();
        } else {
            refreshWordPanel();
        }
    }

    private void refreshHeader() {
        int streak = PrefsHelper.getStreak(this);
        boolean activeToday = PrefsHelper.isStreakActiveToday(this);
        streakBadge.setText("\uD83D\uDD25 " + streak + " g\u00FCn");
        if (streak > 0) {
            int color = activeToday ? UiStyle.ORANGE : UiStyle.AMBER_DIM;
            streakBadge.setTextColor(color);
            streakBadge.setBackground(UiStyle.pill(this, UiStyle.AMBER_BG, color, 20));
        } else {
            streakBadge.setTextColor(UiStyle.TEXT_MUTED);
            streakBadge.setBackground(UiStyle.pill(this, UiStyle.SURFACE, UiStyle.BORDER, 20));
        }

        int learned = PrefsHelper.learnedCount(this);
        learnedBadge.setText("\u2713 " + learned + " / " + allWords.size());
        if (learned > 0) {
            learnedBadge.setTextColor(UiStyle.GREEN);
            learnedBadge.setBackground(UiStyle.pill(this, UiStyle.GREEN_BG, UiStyle.GREEN_BORDER, 20));
        } else {
            learnedBadge.setTextColor(UiStyle.TEXT_MUTED);
            learnedBadge.setBackground(UiStyle.pill(this, UiStyle.SURFACE, UiStyle.BORDER, 20));
        }

        int favCount = PrefsHelper.favoriteCount(this);
        tabFavs.setText("\u2605 Favoriler (" + favCount + ")");

        styleTab(tabAll, mode == MODE_ALL);
        styleTab(tabFavs, mode == MODE_FAVS);
        styleTab(tabQuiz, mode == MODE_QUIZ);
    }

    private void styleTab(Button tab, boolean active) {
        if (active) {
            UiStyle.styleButton(this, tab, UiStyle.AMBER, UiStyle.AMBER_BG, UiStyle.AMBER, 10);
        } else {
            UiStyle.styleButton(this, tab, UiStyle.TEXT_SEC, UiStyle.SURFACE, UiStyle.BORDER, 10);
        }
    }

    private List<Word> favoriteWords() {
        Set<String> favKeys = PrefsHelper.getFavorites(this);
        List<Word> list = new ArrayList<>();
        for (Word w : allWords) {
            String key = w.en == null ? "" : w.en.trim().toLowerCase(Locale.ROOT);
            if (favKeys.contains(key)) {
                list.add(w);
            }
        }
        return list;
    }

    private void refreshWordPanel() {
        if (mode == MODE_FAVS) {
            wordSubtitle.setText("\u2605 Sadece favori kelimeler");
            List<Word> favs = favoriteWords();
            if (favs.isEmpty()) {
                displayedWord = null;
                emptyFavBox.setVisibility(View.VISIBLE);
                wordCardBox.setVisibility(View.GONE);
                return;
            }
            emptyFavBox.setVisibility(View.GONE);
            wordCardBox.setVisibility(View.VISIBLE);
            favIndex = clampIndex(favIndex, favs.size());
            Word w = favs.get(favIndex);
            bindWordCard(w, (favIndex + 1) + " / " + favs.size() + " favori", false);
        } else {
            String date = new SimpleDateFormat("d MMMM yyyy, EEEE", new Locale("tr")).format(new Date());
            wordSubtitle.setText(allIndex == todayIndex ? date + "  \u2022  Bug\u00FCn" : date);
            emptyFavBox.setVisibility(View.GONE);
            wordCardBox.setVisibility(View.VISIBLE);
            allIndex = clampIndex(allIndex, allWords.size());
            Word w = allWords.get(allIndex);
            bindWordCard(w, (w.index + 1) + " / " + allWords.size(), true);
        }
    }

    private void bindWordCard(Word w, String counterText, boolean showTodayBtn) {
        displayedWord = w;
        wordEn.setText(w.en);
        if (w.okunus != null && !w.okunus.isEmpty()) {
            wordOkunus.setText("(" + w.okunus + ")");
            wordOkunus.setVisibility(View.VISIBLE);
        } else {
            wordOkunus.setVisibility(View.GONE);
        }
        wordTr.setText(w.tr);
        wordEx.setText("\u201C" + w.ex + "\u201D");
        wordCounter.setText(counterText);
        btnToday.setVisibility(showTodayBtn ? View.VISIBLE : View.GONE);

        boolean fav = PrefsHelper.isFavorite(this, w.en);
        btnFav.setText(fav ? "\u2605 Favori" : "\u2606 Favori");
        if (fav) {
            UiStyle.styleButton(this, btnFav, UiStyle.AMBER, UiStyle.AMBER_BG, UiStyle.AMBER, 10);
        } else {
            UiStyle.styleButton(this, btnFav, UiStyle.TEXT_SEC, UiStyle.SURFACE_ALT, UiStyle.BORDER, 10);
        }

        boolean learned = PrefsHelper.isLearned(this, w.en);
        learnedStamp.setVisibility(learned ? View.VISIBLE : View.GONE);
        btnLearned.setText(learned ? "\u2713 \u00D6\u011Frenildi" : "\u00D6\u011Frendim \u2713");
        if (learned) {
            UiStyle.styleButton(this, btnLearned, UiStyle.GREEN, UiStyle.GREEN_BG, UiStyle.GREEN_BORDER, 10);
        } else {
            UiStyle.styleButton(this, btnLearned, UiStyle.TEXT_SEC, UiStyle.SURFACE_ALT, UiStyle.BORDER, 10);
        }
    }

    private void stepWord(int delta) {
        if (mode == MODE_FAVS) {
            List<Word> favs = favoriteWords();
            if (favs.isEmpty()) {
                refreshWordPanel();
                return;
            }
            int size = favs.size();
            favIndex = ((favIndex + delta) % size + size) % size;
        } else {
            int size = allWords.size();
            allIndex = ((allIndex + delta) % size + size) % size;
        }
        refreshWordPanel();
    }

    private void onToggleFavorite() {
        if (displayedWord == null) {
            return;
        }
        boolean added = PrefsHelper.toggleFavorite(this, displayedWord.en);
        if (mode == MODE_FAVS && !added) {
            List<Word> remaining = favoriteWords();
            if (remaining.isEmpty()) {
                favIndex = 0;
            } else if (favIndex >= remaining.size()) {
                favIndex = remaining.size() - 1;
            }
        }
        refreshHeader();
        refreshWordPanel();
    }

    private void onToggleLearned() {
        if (displayedWord == null) {
            return;
        }
        PrefsHelper.toggleLearned(this, displayedWord.en);
        refreshHeader();
        refreshWordPanel();
    }

    private void loadNextQuizQuestion() {
        List<Word> pool = quizFavsOnly ? favoriteWords() : allWords;
        if (pool.isEmpty()) {
            quizFavsOnly = false;
            pool = allWords;
        }
        currentQuestion = quizEngine.nextQuestion(allWords, pool);
        quizAnswered = false;
        refreshQuizPanel();
    }

    private void refreshQuizPanel() {
        int totalSession = quizCorrect + quizWrong;
        StringBuilder scoreSb = new StringBuilder();
        scoreSb.append("Do\u011Fru: ").append(quizCorrect)
                .append("  \u2022  Yanl\u0131\u015F: ").append(quizWrong)
                .append("  \u2022  Seri: ").append(quizCombo);
        if (totalSession > 0) {
            int pct = Math.round((quizCorrect * 100f) / totalSession);
            scoreSb.append("  \u2022  %").append(pct);
        }
        quizScoreLine.setText(scoreSb.toString());

        int bestCombo = PrefsHelper.getQuizBestCombo(this);
        int totalCorrect = PrefsHelper.getQuizTotalCorrect(this);
        int totalAnswered = PrefsHelper.getQuizTotalAnswered(this);
        quizStatsLine.setText("En iyi seri: " + bestCombo + "  \u2022  Toplam: " + totalCorrect + "/" + totalAnswered);

        int favCount = favoriteWords().size();
        btnQuizFavs.setText("\u2605 Favoriler (" + favCount + ")");
        if (quizFavsOnly) {
            UiStyle.styleButton(this, btnQuizAll, UiStyle.TEXT_SEC, UiStyle.SURFACE_ALT, UiStyle.BORDER, 8);
            UiStyle.styleButton(this, btnQuizFavs, UiStyle.AMBER, UiStyle.AMBER_BG, UiStyle.AMBER, 8);
        } else {
            UiStyle.styleButton(this, btnQuizAll, UiStyle.AMBER, UiStyle.AMBER_BG, UiStyle.AMBER, 8);
            UiStyle.styleButton(this, btnQuizFavs, UiStyle.TEXT_SEC, UiStyle.SURFACE_ALT, UiStyle.BORDER, 8);
        }

        if (currentQuestion == null) {
            return;
        }

        quizWordEn.setText(currentQuestion.word.en);
        if (currentQuestion.word.okunus != null && !currentQuestion.word.okunus.isEmpty()) {
            quizWordOkunus.setText("(" + currentQuestion.word.okunus + ")");
            quizWordOkunus.setVisibility(View.VISIBLE);
        } else {
            quizWordOkunus.setVisibility(View.GONE);
        }

        String[] labels = {"A) ", "B) ", "C) ", "D) "};
        List<String> opts = currentQuestion.options;
        for (int i = 0; i < 4; i++) {
            Button opt = quizOptionBtns[i];
            if (i < opts.size()) {
                opt.setVisibility(View.VISIBLE);
                opt.setText(labels[i] + opts.get(i));
                if (!quizAnswered) {
                    UiStyle.styleButton(this, opt, UiStyle.TEXT_WHITE,
                            UiStyle.SURFACE, UiStyle.BORDER, 12);
                }
            } else {
                opt.setVisibility(View.GONE);
            }
        }

        if (!quizAnswered) {
            quizFeedback.setVisibility(View.GONE);
            btnQuizNext.setVisibility(View.GONE);
        }
    }

    private void handleQuizAnswer(int chosenIdx) {
        if (quizAnswered || currentQuestion == null) {
            return;
        }
        quizAnswered = true;

        boolean isRight = (chosenIdx == currentQuestion.correctIndex);
        if (isRight) {
            quizCorrect++;
            quizCombo++;
            UiStyle.styleButton(this, quizOptionBtns[chosenIdx], UiStyle.GREEN,
                    UiStyle.GREEN_BG, UiStyle.GREEN_BORDER, 12);
            quizFeedback.setTextColor(UiStyle.GREEN);
            quizFeedback.setText("Do\u011Fru! \u2713\n\u201C" + currentQuestion.word.ex + "\u201D");
        } else {
            quizWrong++;
            quizCombo = 0;
            UiStyle.styleButton(this, quizOptionBtns[chosenIdx], UiStyle.RED,
                    UiStyle.RED_BG, UiStyle.RED_BORDER, 12);
            if (currentQuestion.correctIndex >= 0
                    && currentQuestion.correctIndex < currentQuestion.options.size()) {
                UiStyle.styleButton(this, quizOptionBtns[currentQuestion.correctIndex], UiStyle.GREEN,
                        UiStyle.GREEN_BG, UiStyle.GREEN_BORDER, 12);
            }
            quizFeedback.setTextColor(UiStyle.RED);
            quizFeedback.setText("Yanl\u0131\u015F \u2014 Do\u011Fru: " + currentQuestion.word.tr
                    + "\n\u201C" + currentQuestion.word.ex + "\u201D");
        }

        PrefsHelper.recordQuizResult(this, isRight, quizCombo);
        quizFeedback.setVisibility(View.VISIBLE);
        btnQuizNext.setVisibility(View.VISIBLE);
        refreshQuizPanel();
    }

    private void triggerLockUpdate() {
        btnLock.setEnabled(false);
        new Thread(() -> {
            Word ww = WordProvider.today(this);
            boolean ok = WallpaperHelper.applyLock(this, ww);
            NotifyHelper.show(this, ww);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                btnLock.setEnabled(true);
                btnLock.setText(ok ? "Kilit g\u00FCncellendi \u2713" : "Olmad\u0131, tekrar dene");
            });
        }).start();
    }

    private void triggerWordsUpdate() {
        btnUpdate.setEnabled(false);
        btnUpdate.setText("Denetleniyor...");
        new Thread(() -> {
            String res = WordProvider.updateFromNetwork(this);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                btnUpdate.setEnabled(true);
                btnUpdate.setText("Kelimeleri g\u00FCncelle");
                if (res.startsWith("guncellendi:")) {
                    String n = res.substring("guncellendi:".length());
                    Toast.makeText(this, n + " kelime y\u00FCklendi, yeniden ba\u015Flat\u0131l\u0131yor",
                            Toast.LENGTH_LONG).show();
                    recreate();
                } else if (res.equals("guncel")) {
                    Toast.makeText(this, "Kelimeler zaten g\u00FCncel", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "\u0130nternet yok ya da sunucuya ula\u015F\u0131lamad\u0131",
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private static int clampIndex(int idx, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(idx, size - 1));
    }
}
