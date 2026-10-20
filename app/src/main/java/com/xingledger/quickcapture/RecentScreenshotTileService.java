package com.xingledger.quickcapture;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public final class RecentScreenshotTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_ACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.setSubtitle("最新截图");
        tile.updateTile();
    }

    @Override
    @SuppressLint("StartActivityAndCollapseDeprecated")
    public void onClick() {
        super.onClick();
        Runnable launch = () -> {
            CaptureDiagnosticLog.record(this, "recent_tile_tap", "");
            Intent intent = new Intent(this, ImportImageActivity.class)
                    .setAction(CaptureContract.ACTION_RECENT_SCREENSHOT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    PendingIntent pendingIntent = PendingIntent.getActivity(this, 32, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    startActivityAndCollapse(pendingIntent);
                } else {
                    //noinspection deprecation
                    startActivityAndCollapse(intent);
                }
            } catch (RuntimeException error) {
                CaptureDiagnosticLog.record(this, "recent_tile_launch_failed", "");
                Toast.makeText(this, "图片识别未能启动，请重试", Toast.LENGTH_SHORT).show();
            }
        };
        if (isLocked()) unlockAndRun(launch); else launch.run();
    }
}
