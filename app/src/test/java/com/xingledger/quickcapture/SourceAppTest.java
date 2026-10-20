package com.xingledger.quickcapture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SourceAppTest {
    @Test
    public void knownShoppingPackagesUseReadableLabels() {
        assertEquals("淘宝", new SourceApp("com.taobao.taobao", "com.taobao.taobao").label);
        assertEquals("拼多多", new SourceApp("com.xunmeng.pinduoduo",
                "com.xunmeng.pinduoduo").label);
        assertEquals("自定义名称", new SourceApp("自定义名称", "com.taobao.taobao").label);
    }
}
