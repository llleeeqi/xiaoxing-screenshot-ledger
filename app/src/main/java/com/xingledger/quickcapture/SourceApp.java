package com.xingledger.quickcapture;

public final class SourceApp {
    public static final SourceApp UNKNOWN = new SourceApp("", "");

    public final String label;
    public final String packageName;

    public SourceApp(String label, String packageName) {
        this.label = label == null ? "" : label;
        this.packageName = packageName == null ? "" : packageName;
    }
}
