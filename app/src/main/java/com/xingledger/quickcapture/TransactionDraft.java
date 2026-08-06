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
}
