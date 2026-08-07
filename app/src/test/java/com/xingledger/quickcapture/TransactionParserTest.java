package com.xingledger.quickcapture;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TransactionParserTest {
    @Test
    public void parsesAlipayPaymentAndIgnoresDiscount() {
        TransactionDraft draft = TransactionParser.parse(Arrays.asList(
                line("支付成功", 20),
                line("￥18.60", 80),
                line("麦当劳（中山店）", 130),
                line("优惠 5.00", 180),
                line("付款方式 花呗", 230),
                line("2026-08-06 12:31:09", 280),
                line("支付宝", 350)));

        assertEquals("支出", draft.type);
        assertEquals("18.6", draft.amount);
        assertEquals("麦当劳（中山店）", draft.shop);
        assertEquals("花呗", draft.account);
        assertEquals("支付宝", draft.channel);
        assertEquals("2026-08-06 12:31:09", draft.time);
    }

    @Test
    public void parsesWechatReceipt() {
        TransactionDraft draft = TransactionParser.parse(Arrays.asList(
                line("微信支付", 10),
                line("收款成功", 60),
                line("收款金额：¥128.00", 110),
                line("付款方：张三", 170)));

        assertEquals("收入", draft.type);
        assertEquals("128", draft.amount);
        assertEquals("微信零钱", draft.account);
        assertEquals("微信", draft.channel);
    }

    @Test
    public void parsesTransferAccounts() {
        TransactionDraft draft = TransactionParser.parse(Arrays.asList(
                line("转账成功", 20),
                line("转账金额 1,234.50", 80),
                line("转出账户 招商银行储蓄卡(1234)", 140),
                line("转入账户 浦发银行储蓄卡(5678)", 200)));

        assertEquals("转账", draft.type);
        assertEquals("1234.5", draft.amount);
        assertEquals("招商银行储蓄卡(1234)", draft.account);
        assertEquals("浦发银行储蓄卡(5678)", draft.account2);
    }

    @Test
    public void joinsLabelsAndValuesSplitIntoSeparateOcrLines() {
        TransactionDraft receipt = TransactionParser.parse(Arrays.asList(
                new OcrLine("微信支付", 10, 0),
                new OcrLine("收款成功", 60, 400),
                new OcrLine("¥128.00", 110, 430),
                new OcrLine("付款方", 300, 60),
                new OcrLine("张三", 300, 780),
                new OcrLine("收款方式", 400, 60),
                new OcrLine("微信零钱", 400, 760)));
        assertEquals("张三", receipt.shop);
        assertEquals("微信零钱", receipt.account);

        TransactionDraft transfer = TransactionParser.parse(Arrays.asList(
                new OcrLine("手机银行 转账成功", 10, 0),
                new OcrLine("￥1,234.50", 80, 400),
                new OcrLine("收款人", 300, 60),
                new OcrLine("李晓明", 300, 780),
                new OcrLine("转出账户", 400, 60),
                new OcrLine("招商银行储蓄卡(1234)", 400, 500),
                new OcrLine("浦发银行储蓄卡(5678)", 492, 500),
                new OcrLine("转入账户", 500, 60)));
        assertEquals("李晓明", transfer.shop);
        assertEquals("招商银行储蓄卡(1234)", transfer.account);
        assertEquals("浦发银行储蓄卡(5678)", transfer.account2);
        assertEquals("手机银行", transfer.channel);
    }

    @Test
    public void usesBillKeywordsWhenAmountIsMissing() {
        TransactionDraft bill = TransactionParser.parse(Arrays.asList(
                line("微信支付", 10),
                line("订单详情", 60),
                line("金额暂时无法识别", 110)));
        assertFalse(bill.hasAmount());
        assertTrue(bill.hasBillKeywords());

        TransactionDraft ordinaryScreen = TransactionParser.parse(Arrays.asList(
                line("今天天气晴朗", 10),
                line("存储空间还剩 2.22 GB", 60)));
        assertTrue(ordinaryScreen.hasAmount());
        assertFalse(ordinaryScreen.hasBillKeywords());
    }

    @Test
    public void detectsShoppingPlatformFromImportedScreenshotText() {
        TransactionDraft draft = TransactionParser.parse(Arrays.asList(
                line("手机淘宝 订单详情", 10),
                line("支付成功 ￥66.00", 60),
                line("支付宝付款", 110)));

        assertEquals("淘宝", draft.channel);
        assertEquals("66", draft.amount);
    }

    private static OcrLine line(String text, int top) {
        return new OcrLine(text, top, 0);
    }
}
