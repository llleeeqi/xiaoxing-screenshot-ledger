package com.xingledger.quickcapture;

import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;

public final class ProjectionConsent {
    private ProjectionConsent() {}

    public static Intent createIntent(MediaProjectionManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // This tool must keep seeing whichever payment app is currently on screen.
            // Capturing one selected app would stop working after switching apps.
            return manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        }
        return manager.createScreenCaptureIntent();
    }
}
