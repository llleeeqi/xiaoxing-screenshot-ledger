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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public final class CaptureActivity extends AppCompatActivity {
    private static final int REQUEST_PROJECTION = 7101;
    private boolean started;
    private AlertDialog progressDialog;
    private SourceApp sourceApp = SourceApp.UNKNOWN;
    private final ResultReceiver receiver = new ResultReceiver(new Handler()) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (isFinishing() || isDestroyed()) return;
            if (resultCode == CaptureContract.RESULT_PROCESSING) {
                showProgress();
                return;
            }
            dismissProgress();
            if (resultCode == CaptureContract.RESULT_CAPTURED) {
                if (resultData == null) {
                    showFeedback("截图识别失败", "没有收到识别结果，请重试。");
                    return;
                }
                resultData.setClassLoader(TransactionDraft.class.getClassLoader());
                TransactionDraft draft = (TransactionDraft) resultData.getSerializable(CaptureContract.EXTRA_DRAFT);
                if (draft == null) {
                    showFeedback("截图识别失败", "没有收到识别结果，请重试。");
                    return;
                }
                if (!draft.hasBillKeywords()) {
                    boolean noText = draft.rawText == null || draft.rawText.trim().isEmpty();
                    showFeedback("未识别到账单", noText
                            ? "截图已完成，但没有读到文字。当前页面可能禁止截图；请尝试订单详情或付款记录页。"
                            : "已读到文字，但没有匹配到账单关键词。请打开完整的支付详情或账单页后重试。");
                    return;
                }
                if (draft.hasAmount() && XiaoXingLauncher.openDialog(CaptureActivity.this, draft)) {
                    finish();
                    return;
                }
                startActivity(new Intent(CaptureActivity.this, ReviewActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(CaptureContract.EXTRA_DRAFT, draft)
                        .putExtra(CaptureContract.EXTRA_SCREENSHOT,
                                resultData.getString(CaptureContract.EXTRA_SCREENSHOT)));
                finish();
            } else {
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
        if (AppBlacklist.isBlocked(this, sourceApp.packageName)) {
            started = true;
            String name = sourceApp.label.isEmpty() ? sourceApp.packageName : sourceApp.label;
            getWindow().getDecorView().post(() ->
                    showFeedback("已跳过截图", getString(R.string.blacklist_skipped, name)));
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (AccessibilityCaptureService.requestCapture(receiver, sourceApp)) return;
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
            showFeedback("未授予截图权限", "系统没有授予屏幕截图权限，请重新点击磁贴后允许。");
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
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    @Override
    protected void onDestroy() {
        dismissProgress();
        super.onDestroy();
    }
}
