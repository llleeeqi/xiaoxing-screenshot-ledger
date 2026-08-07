package com.xingledger.quickcapture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class TransactionEnricherTest {
    @Test
    public void realSourceOverridesOcrChannelAndAddsRemark() {
        TransactionDraft draft = new TransactionDraft();
        draft.channel = "手机银行";
        draft.remark = "房租";

        TransactionEnricher.addSource(draft, "招商银行");

        assertEquals("招商银行", draft.channel);
        assertEquals("房租 · 来源：招商银行", draft.remark);
    }

    @Test
    public void genericImportSourceKeepsDetectedChannel() {
        TransactionDraft draft = new TransactionDraft();
        draft.channel = "支付宝";

        TransactionEnricher.addSource(draft, "相册分享");

        assertEquals("支付宝", draft.channel);
        assertEquals("来源：支付宝", draft.remark);
    }

    @Test
    public void shoppingAppIsWrittenAsConsumptionPlatform() {
        TransactionDraft draft = new TransactionDraft();
        draft.channel = "支付宝";
        draft.remark = "日用品";

        TransactionEnricher.addSource(draft, "手机淘宝");
        TransactionEnricher.addSource(draft, "手机淘宝");

        assertEquals("手机淘宝", draft.channel);
        assertEquals("日用品 · 消费平台：手机淘宝", draft.remark);
    }
}
