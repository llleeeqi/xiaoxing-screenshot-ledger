package com.xingledger.quickcapture;

import java.util.Locale;

public final class ScreenshotRecord {
    public final String imagePath;
    public final long capturedAt;
    public final String appLabel;
    public final String appPackage;
    public final String channel;
    public final String ocrText;

    public ScreenshotRecord(String imagePath, long capturedAt, String appLabel, String appPackage,
                            String channel, String ocrText) {
        this.imagePath = imagePath;
        this.capturedAt = capturedAt;
        this.appPackage = appPackage == null ? "" : appPackage;
        this.appLabel = new SourceApp(appLabel, this.appPackage).label;
        String savedChannel = channel == null ? "" : channel;
        this.channel = savedChannel.equals(this.appPackage) ? this.appLabel : savedChannel;
        this.ocrText = ocrText == null ? "" : ocrText;
    }

    public boolean matchesQuery(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return true;
        String haystack = (appLabel + '\n' + appPackage + '\n' + channel + '\n' + ocrText)
                .toLowerCase(Locale.ROOT);
        return haystack.contains(needle);
    }
}
