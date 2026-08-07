package com.xingledger.quickcapture;

import java.io.Serializable;

public final class TransactionDraft implements Serializable {
    public String type = "支出";
    public String amount = "";
    public String shop = "";
    public String account = "";
    public String account2 = "";
    public String time = "";
    public String channel = "";
    public String remark = "";
    public String rawText = "";

    public boolean hasAmount() {
        return amount != null && !amount.trim().isEmpty() && !"0".equals(amount.trim());
    }

    public boolean hasBillKeywords() {
        String text = rawText == null ? "" : rawText.replace(" ", "");
        String[] keywords = {
                "支付", "付款", "收款", "转账", "交易", "订单", "账单",
                "退款", "扣款", "入账", "到账", "实付", "应付", "商户"
        };
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
