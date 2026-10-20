package com.xingledger.quickcapture;

public final class SourceApp {
    public static final SourceApp UNKNOWN = new SourceApp("", "");

    public final String label;
    public final String packageName;

    public SourceApp(String label, String packageName) {
        this.packageName = packageName == null ? "" : packageName;
        String name = label == null ? "" : label;
        if (name.isEmpty() || name.equals(this.packageName)) {
            if ("com.taobao.taobao".equals(this.packageName)) name = "淘宝";
            else if ("com.xunmeng.pinduoduo".equals(this.packageName)) name = "拼多多";
        }
        this.label = name;
    }
}
