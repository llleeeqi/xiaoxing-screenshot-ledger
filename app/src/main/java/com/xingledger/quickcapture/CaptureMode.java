package com.xingledger.quickcapture;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/** Selected behavior of the primary quick-settings tile and capture Action. */
public enum CaptureMode {
    ACCESSIBILITY("无障碍单帧截图"),
    PROJECTION("系统授权截图"),
    RECENT("识别最近截图");

    private static final String PREFS = "capture_settings";
    private static final String KEY_MODE = "capture_mode";

    public final String label;

    CaptureMode(String label) {
        this.label = label;
    }

    public static CaptureMode current(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String fallback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? ACCESSIBILITY.name() : PROJECTION.name();
        try {
            CaptureMode selected = valueOf(preferences.getString(KEY_MODE, fallback));
            return selected == ACCESSIBILITY && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                    ? PROJECTION : selected;
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? ACCESSIBILITY : PROJECTION;
        }
    }

    public void select(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_MODE, name()).apply();
        ScreenshotTileService.requestListeningState(context,
                new android.content.ComponentName(context, ScreenshotTileService.class));
    }
}
