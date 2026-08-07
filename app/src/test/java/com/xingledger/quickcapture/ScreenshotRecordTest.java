package com.xingledger.quickcapture;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ScreenshotRecordTest {
    private final ScreenshotRecord record = new ScreenshotRecord(
            "/private/capture.jpg", 1L, "支付宝", "com.eg.android.AlipayGphone",
            "支付宝", "支付成功\n麦当劳（中山店）\n实付 ￥18.60\n付款方式 花呗");

    @Test
    public void searchesAppChannelAndOcrText() {
        assertTrue(record.matchesQuery("支付宝"));
        assertTrue(record.matchesQuery("AlipayGphone"));
        assertTrue(record.matchesQuery("麦当劳"));
        assertTrue(record.matchesQuery("18.60"));
        assertTrue(record.matchesQuery("花呗"));
        assertTrue(record.matchesQuery("  "));
        assertFalse(record.matchesQuery("微信收款"));
    }
}
