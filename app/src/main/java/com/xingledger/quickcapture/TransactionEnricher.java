package com.xingledger.quickcapture;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class TransactionEnricher {
    private static final Set<String> GENERIC_SOURCES = new HashSet<>(Arrays.asList(
            "", "未知应用", "小星截图记账助手", "小星记账助手-截图录入", "图库", "相册", "系统界面",
            "最近截图", "相册分享", "文件", "照片"));
    private static final String[] SHOPPING_PLATFORMS = {
            "淘宝", "天猫", "京东", "拼多多", "美团", "饿了么", "抖音",
            "快手", "闲鱼", "唯品会", "盒马", "携程", "去哪儿", "滴滴"
    };

    private TransactionEnricher() {}

    public static void addSource(TransactionDraft draft, String sourceApp) {
        String candidate = clean(sourceApp);
        boolean realSource = !GENERIC_SOURCES.contains(candidate);
        if (realSource) draft.channel = candidate;
        String channel = clean(draft.channel);
        if (channel.isEmpty()) return;
        String platform = realSource ? candidate : channel;
        String sourceNote = isShoppingPlatform(platform)
                ? "消费平台：" + platform : "来源：" + channel;
        String remark = clean(draft.remark);
        if (!remark.contains(sourceNote)) {
            draft.remark = remark.isEmpty() ? sourceNote : remark + " · " + sourceNote;
        }
    }

    private static boolean isShoppingPlatform(String source) {
        for (String platform : SHOPPING_PLATFORMS) {
            if (source.contains(platform)) return true;
        }
        return false;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
