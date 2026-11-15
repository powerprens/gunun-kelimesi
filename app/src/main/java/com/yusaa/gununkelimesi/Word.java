package com.yusaa.gununkelimesi;

public class Word {
    public final String en;
    public final String tr;
    public final String ex;
    public final String okunus;
    public final int index;
    public final int total;

    public Word(String en, String tr, String ex, String okunus, int index, int total) {
        this.en = en;
        this.tr = tr;
        this.ex = ex;
        this.okunus = okunus == null ? "" : okunus;
        this.index = index;
        this.total = total;
    }
}
