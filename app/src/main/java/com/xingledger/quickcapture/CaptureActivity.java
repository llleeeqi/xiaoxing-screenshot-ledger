package com.xingledger.quickcapture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public final class CaptureActivity extends AppCompatActivity {
    private static final int REQUEST_PROJECTION = 7101;
    private boolean started;
    private SourceApp sourceApp = SourceApp.UNKNOWN;
    private final ResultReceiver receiver = new ResultReceiver(new Handler()) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (isFinishing() || isDestroyed()) return;
            if (resultCode == CaptureContract.RESULT_CAPTURED) {
                resultData.setClassLoader(TransactionDraft.class.getClassLoader());
                TransactionDraft draft = (TransactionDraft) resultData.getSerializable(CaptureContract.EXTRA_DRAFT);
                if (draft != null && draft.hasAmount() && XiaoXingLauncher.openDialog(CaptureActivity.this, draft)) {
                    // Keep this capture task behind XiaoXing so emulator task cleanup
                    // cannot kill the long-lived MediaProjection service.
                    moveTaskToBack(true);
                    return;
                }
                startActivity(new Intent(CaptureActivity.this, ReviewActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(CaptureContract.EXTRA_DRAFT, draft)
                        .putExtra(CaptureContract.EXTRA_SCREENSHOT,
                                resultData.getString(CaptureContract.EXTRA_SCREENSHOT)));
            } else {
                Toast.makeText(CaptureActivity.this,
                        resultData.getString(CaptureContract.EXTRA_ERROR, "截图识别失败"),
                        Toast.LENGTH_LONG).show();
            }
            finish();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDimAmount(0f);
        String label = getIntent().getStringExtra(CaptureContract.EXTRA_SOURCE_APP);
        String packageName = getIntent().getStringExtra(CaptureContract.EXTRA_SOURCE_PACKAGE);
        if (label == null || label.isEmpty()) {
            label = getIntent().getStringExtra(CaptureContract.EXTRA_AUTOMATION_SOURCE_APP);
        }
        if (packageName == null || packageName.isEmpty()) {
            packageName = getIntent().getStringExtra(CaptureContract.EXTRA_AUTOMATION_SOURCE_PACKAGE);
        }
        sourceApp = label == null || label.isEmpty()
                ? ForegroundAppDetector.detect(this)
                : new SourceApp(label, packageName);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || started) return;
        started = true;
        // Let the Quick Settings panel finish collapsing before reading the latest frame.
        getWindow().getDecorView().postDelayed(this::captureOrRequestPermission, 180);
    }

    private void captureOrRequestPermission() {
        if (CaptureService.isReady()) {
            Intent service = new Intent(this, CaptureService.class).setAction(CaptureContract.ACTION_CAPTURE);
            service.putExtra(CaptureContract.EXTRA_RECEIVER, receiver);
            putSource(service);
            DisplayInfo.from(this).putInto(service);
            startService(service);
            return;
        }
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(ProjectionConsent.createIntent(manager), REQUEST_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROJECTION) return;
        if (resultCode != Activity.RESULT_OK || data == null) {
            Toast.makeText(this, "未授予截图权限", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent service = new Intent(this, CaptureService.class).setAction(CaptureContract.ACTION_START_AND_CAPTURE);
        service.putExtra(CaptureContract.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(CaptureContract.EXTRA_RESULT_DATA, data);
        service.putExtra(CaptureContract.EXTRA_RECEIVER, receiver);
        putSource(service);
        DisplayInfo.from(this).putInto(service);
        ContextCompat.startForegroundService(this, service);
    }

    private void putSource(Intent intent) {
        intent.putExtra(CaptureContract.EXTRA_SOURCE_APP, sourceApp.label);
        intent.putExtra(CaptureContract.EXTRA_SOURCE_PACKAGE, sourceApp.packageName);
    }
}
