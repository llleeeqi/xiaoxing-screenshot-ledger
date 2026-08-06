package com.xingledger.quickcapture;

public final class OcrLine {
    public final String text;
    public final int top;
    public final int left;

    public OcrLine(String text, int top, int left) {
        this.text = text == null ? "" : text.trim();
        this.top = top;
        this.left = left;
    }
}
