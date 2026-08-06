package com.xingledger.quickcapture;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowMetrics;

public final class DisplayInfo {
    public final int width;
    public final int height;
    public final int density;

    private DisplayInfo(int width, int height, int density) {
        this.width = width;
        this.height = height;
        this.density = density;
    }

    public static DisplayInfo from(Activity activity) {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = activity.getWindowManager().getMaximumWindowMetrics();
            Rect bounds = windowMetrics.getBounds();
            width = bounds.width();
            height = bounds.height();
        }
        return new DisplayInfo(width, height, metrics.densityDpi);
    }

    public void putInto(android.content.Intent intent) {
        intent.putExtra(CaptureContract.EXTRA_WIDTH, width);
        intent.putExtra(CaptureContract.EXTRA_HEIGHT, height);
        intent.putExtra(CaptureContract.EXTRA_DENSITY, density);
    }
}
