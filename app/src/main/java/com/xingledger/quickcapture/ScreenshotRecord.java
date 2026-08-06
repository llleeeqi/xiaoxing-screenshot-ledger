package com.xingledger.quickcapture;

public final class ScreenshotRecord {
    public final String imagePath;
    public final long capturedAt;
    public final String appLabel;
    public final String appPackage;
    public final String channel;

    public ScreenshotRecord(String imagePath, long capturedAt, String appLabel, String appPackage, String channel) {
        this.imagePath = imagePath;
        this.capturedAt = capturedAt;
        this.appLabel = appLabel == null ? "" : appLabel;
        this.appPackage = appPackage == null ? "" : appPackage;
        this.channel = channel == null ? "" : channel;
    }
}
