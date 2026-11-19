package com.yusaa.gununkelimesi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Generates shuffled 4-choice Turkish meaning quiz questions without duplicate answers. */
public class QuizEngine {

    public static class Question {
        public final Word word;
        public final List<String> options;
        public final int correctIndex;

        public Question(Word word, List<String> options, int correctIndex) {
            this.word = word;
            this.options = options;
            this.correctIndex = correctIndex;
        }
    }

    private final Random rng = new Random();
    private final List<Word> deck = new ArrayList<>();
    private int deckPos = 0;
    private String lastWordEn = null;

    public void resetDeck() {
        deck.clear();
        deckPos = 0;
    }

    public Question nextQuestion(List<Word> allWords, List<Word> pool) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        if (deck.isEmpty() || deckPos >= deck.size()) {
            deck.clear();
            deck.addAll(pool);
            Collections.shuffle(deck, rng);
            deckPos = 0;
            if (deck.size() > 1 && lastWordEn != null && deck.get(0).en.equals(lastWordEn)) {
                Collections.swap(deck, 0, deck.size() - 1);
            }
        }

        Word target = deck.get(deckPos++);
        lastWordEn = target.en;

        List<String> options = new ArrayList<>();
        Set<String> usedTr = new HashSet<>();
        String targetTr = target.tr == null ? "" : target.tr.trim();
        options.add(targetTr);
        if (!targetTr.isEmpty()) {
            usedTr.add(targetTr.toLowerCase(Locale.ROOT));
        }

        List<Word> distractorPool = (allWords != null && !allWords.isEmpty())
                ? new ArrayList<>(allWords)
                : new ArrayList<>(pool);
        Collections.shuffle(distractorPool, rng);

        for (Word candidate : distractorPool) {
            if (options.size() >= 4) {
                break;
            }
            if (candidate == null || candidate.tr == null) {
                continue;
            }
            String candTr = candidate.tr.trim();
            if (candTr.isEmpty()) {
                continue;
            }
            String norm = candTr.toLowerCase(Locale.ROOT);
            if (!usedTr.contains(norm)) {
                usedTr.add(norm);
                options.add(candTr);
            }
        }

        Collections.shuffle(options, rng);
        int correctIndex = options.indexOf(targetTr);
        if (correctIndex < 0) {
            correctIndex = 0;
        }
        return new Question(target, options, correctIndex);
    }
}
