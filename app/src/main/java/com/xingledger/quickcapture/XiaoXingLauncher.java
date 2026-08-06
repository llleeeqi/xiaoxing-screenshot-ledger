package com.xingledger.quickcapture;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;

public final class XiaoXingLauncher {
    private XiaoXingLauncher() {}

    public static boolean openDialog(Activity activity, TransactionDraft draft) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, XiaoXingScheme.buildDialogUri(draft))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            return false;
        }
    }
}
