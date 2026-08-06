package com.xingledger.quickcapture;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class TransactionEnricher {
    private static final Set<String> GENERIC_SOURCES = new HashSet<>(Arrays.asList(
            "", "未知应用", "小星截图记账助手", "图库", "相册", "系统界面",
            "最近截图", "相册分享", "文件", "照片"));

    private TransactionEnricher() {}

    public static void addSource(TransactionDraft draft, String sourceApp) {
        String candidate = clean(sourceApp);
        if (!GENERIC_SOURCES.contains(candidate)) draft.channel = candidate;
        String channel = clean(draft.channel);
        if (channel.isEmpty()) return;
        String sourceNote = "来源：" + channel;
        String remark = clean(draft.remark);
        if (!remark.contains(sourceNote)) {
            draft.remark = remark.isEmpty() ? sourceNote : remark + " · " + sourceNote;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
