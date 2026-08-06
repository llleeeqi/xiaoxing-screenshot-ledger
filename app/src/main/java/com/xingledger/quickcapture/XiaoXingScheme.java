package com.xingledger.quickcapture;

import android.net.Uri;

public final class XiaoXingScheme {
    private XiaoXingScheme() {}

    public static Uri buildDialogUri(TransactionDraft draft) {
        Uri.Builder builder = new Uri.Builder()
                .scheme("xxjz")
                .authority("api")
                .appendPath("dialog")
                .appendQueryParameter("type", valueOr(draft.type, "支出"));
        append(builder, "amount", draft.amount);
        append(builder, "shop", draft.shop);
        append(builder, "account", draft.account);
        append(builder, "account2", draft.account2);
        append(builder, "remark", draft.remark);
        append(builder, "channel", draft.channel);
        append(builder, "time", draft.time);
        return builder.build();
    }

    private static void append(Uri.Builder builder, String key, String value) {
        if (value != null && !value.trim().isEmpty()) builder.appendQueryParameter(key, value.trim());
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
