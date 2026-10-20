package com.xingledger.quickcapture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public final class CaptureActivity extends AppCompatActivity {
    private static final int REQUEST_PROJECTION = 7101;
    private boolean started;
    private AlertDialog progressDialog;
    private AlertDialog feedbackDialog;
    private SourceApp sourceApp = SourceApp.UNKNOWN;
    private final ResultReceiver receiver = new ResultReceiver(new Handler()) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (isFinishing() || isDestroyed()) return;
            if (resultCode == CaptureContract.RESULT_PROCESSING) {
                CaptureDiagnosticLog.record(CaptureActivity.this, "ocr_started", sourceApp.packageName);
                showProgress();
                return;
            }
            dismissProgress();
            if (resultCode == CaptureContract.RESULT_CAPTURED) {
                if (resultData == null) {
                    CaptureDiagnosticLog.record(CaptureActivity.this, "missing_result", sourceApp.packageName);
                    showFeedback("截图识别失败", "没有收到识别结果，请重试。");
                    return;
                }
                resultData.setClassLoader(TransactionDraft.class.getClassLoader());
                TransactionDraft draft = (TransactionDraft) resultData.getSerializable(CaptureContract.EXTRA_DRAFT);
                if (draft == null) {
                    CaptureDiagnosticLog.record(CaptureActivity.this, "missing_draft", sourceApp.packageName);
                    showFeedback("截图识别失败", "没有收到识别结果，请重试。");
                    return;
                }
                if (!draft.hasBillKeywords()) {
                    boolean noText = draft.rawText == null || draft.rawText.trim().isEmpty();
                    CaptureDiagnosticLog.record(CaptureActivity.this,
                            noText ? "no_text" : "no_bill_keywords", sourceApp.packageName);
                    showFeedback("未识别到账单", noText
                            ? "截图已完成，但没有读到文字。当前页面可能禁止截图；请尝试订单详情或付款记录页。"
                            : "已读到文字，但没有匹配到账单关键词。请打开完整的支付详情或账单页后重试。");
                    return;
                }
                if (draft.hasAmount() && XiaoXingLauncher.openDialog(CaptureActivity.this, draft)) {
                    CaptureDiagnosticLog.record(CaptureActivity.this, "handoff_xiaoxing", sourceApp.packageName);
                    Toast.makeText(CaptureActivity.this, "已识别，正在打开小星记账", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                CaptureDiagnosticLog.record(CaptureActivity.this, "handoff_review", sourceApp.packageName);
                Toast.makeText(CaptureActivity.this, "已识别，请校正账单", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(CaptureActivity.this, ReviewActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(CaptureContract.EXTRA_DRAFT, draft)
                        .putExtra(CaptureContract.EXTRA_SCREENSHOT,
                                resultData.getString(CaptureContract.EXTRA_SCREENSHOT)));
                finish();
            } else {
                CaptureDiagnosticLog.record(CaptureActivity.this, "capture_error", sourceApp.packageName);
                String message = resultData == null ? "截图识别失败"
                        : resultData.getString(CaptureContract.EXTRA_ERROR, "截图识别失败");
                showFeedback("截图识别失败", message);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDimAmount(0f);
        loadSource(getIntent());
    }

    private void loadSource(Intent intent) {
        String label = intent.getStringExtra(CaptureContract.EXTRA_SOURCE_APP);
        String packageName = intent.getStringExtra(CaptureContract.EXTRA_SOURCE_PACKAGE);
        if (label == null || label.isEmpty()) {
            label = intent.getStringExtra(CaptureContract.EXTRA_AUTOMATION_SOURCE_APP);
        }
        if (packageName == null || packageName.isEmpty()) {
            packageName = intent.getStringExtra(CaptureContract.EXTRA_AUTOMATION_SOURCE_PACKAGE);
        }
        sourceApp = label == null || label.isEmpty()
                ? ForegroundAppDetector.detect(this)
                : new SourceApp(label, packageName);
        CaptureDiagnosticLog.record(this, "capture_opened", sourceApp.packageName);
        if (AppBlacklist.isBlocked(this, sourceApp.packageName)) {
            CaptureDiagnosticLog.record(this, "blacklist_skipped", sourceApp.packageName);
            started = true;
            String name = sourceApp.label.isEmpty() ? sourceApp.packageName : sourceApp.label;
            getWindow().getDecorView().post(() ->
                    showFeedback("已跳过截图", getString(R.string.blacklist_skipped, name)));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        CaptureDiagnosticLog.record(this, "capture_reopened", sourceApp.packageName);
        dismissProgress();
        if (feedbackDialog != null) {
            feedbackDialog.dismiss();
            feedbackDialog = null;
        }
        started = false;
        setIntent(intent);
        loadSource(intent);
        getWindow().getDecorView().postDelayed(() -> {
            if (!started && getWindow().getDecorView().hasWindowFocus()) scheduleCapture();
        }, 220);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || started) return;
        scheduleCapture();
    }

    private void scheduleCapture() {
        if (started) return;
        started = true;
        // Let the Quick Settings panel finish collapsing before reading the latest frame.
        getWindow().getDecorView().postDelayed(this::captureOrRequestPermission, 180);
    }

    private void captureOrRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (AccessibilityCaptureService.requestCapture(receiver, sourceApp)) return;
            CaptureDiagnosticLog.record(this, "accessibility_unavailable", sourceApp.packageName);
            new AlertDialog.Builder(this)
                    .setTitle("截图服务未就绪")
                    .setMessage(AccessibilityCaptureService.isEnabled(this)
                            ? "无障碍截图服务正在连接。请稍后重试；若一直如此，可在无障碍设置中重新开启。"
                            : "请先开启“小星记账助手”的无痕截图服务。")
                    .setNegativeButton("关闭", (dialog, which) -> finish())
                    .setPositiveButton("打开设置", (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .setData(Uri.parse("package:" + getPackageName())));
                        } catch (RuntimeException ignored) {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        }
                        finish();
                    })
                    .setOnCancelListener(dialog -> finish())
                    .show();
            return;
        }
        if (CaptureService.isReady()) {
            CaptureDiagnosticLog.record(this, "projection_capture_requested", sourceApp.packageName);
            Intent service = new Intent(this, CaptureService.class).setAction(CaptureContract.ACTION_CAPTURE);
            service.putExtra(CaptureContract.EXTRA_RECEIVER, receiver);
            putSource(service);
            DisplayInfo.from(this).putInto(service);
            startService(service);
            return;
        }
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        CaptureDiagnosticLog.record(this, "projection_consent_requested", sourceApp.packageName);
        startActivityForResult(ProjectionConsent.createIntent(manager), REQUEST_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROJECTION) return;
        if (resultCode != Activity.RESULT_OK || data == null) {
            CaptureDiagnosticLog.record(this, "projection_consent_denied", sourceApp.packageName);
            showFeedback("未授予截图权限", "系统没有授予屏幕截图权限，请重新点击磁贴后允许。");
            return;
        }
        Intent service = new Intent(this, CaptureService.class).setAction(CaptureContract.ACTION_START_AND_CAPTURE);
        CaptureDiagnosticLog.record(this, "projection_consent_granted", sourceApp.packageName);
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

    private void showProgress() {
        if (progressDialog != null) return;
        progressDialog = new AlertDialog.Builder(this)
                .setTitle("正在识别截图")
                .setMessage("正在本机读取图片文字，请稍候…")
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void dismissProgress() {
        if (progressDialog == null) return;
        progressDialog.dismiss();
        progressDialog = null;
    }

    private void showFeedback(String title, String message) {
        if (isFinishing() || isDestroyed()) return;
        if (feedbackDialog != null) feedbackDialog.dismiss();
        feedbackDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .create();
        feedbackDialog.show();
    }

    @Override
    protected void onDestroy() {
        dismissProgress();
        if (feedbackDialog != null) feedbackDialog.dismiss();
        super.onDestroy();
    }
}
