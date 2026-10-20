package com.xingledger.quickcapture;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public final class ScreenshotTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    @SuppressLint("StartActivityAndCollapseDeprecated")
    public void onClick() {
        super.onClick();
        Runnable launch = () -> {
            SourceApp source = ForegroundAppDetector.detect(this);
            CaptureDiagnosticLog.record(this, "tile_tap", source.packageName);
            Intent intent = new Intent(this, CaptureActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(CaptureContract.EXTRA_SOURCE_APP, source.label)
                    .putExtra(CaptureContract.EXTRA_SOURCE_PACKAGE, source.packageName);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    PendingIntent pendingIntent = PendingIntent.getActivity(
                            this, 31, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    startActivityAndCollapse(pendingIntent);
                } else {
                    //noinspection deprecation
                    startActivityAndCollapse(intent);
                }
            } catch (RuntimeException error) {
                CaptureDiagnosticLog.record(this, "tile_launch_failed", source.packageName);
                Toast.makeText(this, "截图记账未能启动，请重试", Toast.LENGTH_SHORT).show();
            }
        };
        if (isLocked()) unlockAndRun(launch); else launch.run();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean ready = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? AccessibilityCaptureService.isConnected() : CaptureService.isReady();
        tile.setState(ready ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(ready ? "点击识别" : "点击开启服务");
        }
        tile.updateTile();
    }
}
