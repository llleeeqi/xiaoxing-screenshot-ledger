package com.xingledger.quickcapture;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public final class ImportImageActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGES = 7301;
    private TextView status;
    private ProgressBar progress;
    private MaterialButton action;
    private boolean recentMode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        recentMode = CaptureContract.ACTION_RECENT_SCREENSHOT.equals(intent.getAction());
        if (recentMode) {
            if (hasPhotoAccess()) processRecent();
            else showPermissionRequired();
            return;
        }
        if (!Intent.ACTION_SEND.equals(intent.getAction())
                || intent.getType() == null || !intent.getType().startsWith("image/")) {
            showError("没有收到可识别的图片", false);
            return;
        }
        Uri stream = streamExtra(intent);
        if (stream == null && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
            stream = intent.getClipData().getItemAt(0).getUri();
        }
        if (stream == null) {
            showError("相册没有提供图片读取地址", false);
            return;
        }
        process(stream, "相册分享", "正在识别分享的图片…");
    }

    private void processRecent() {
        Uri uri = RecentScreenshotFinder.find(this);
        if (uri == null) {
            boolean partial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
            showError(partial
                    ? "已获准的照片中没有截图。请允许访问全部照片，或从相册直接分享这张图。"
                    : "媒体库里没有找到系统截图。你也可以从相册直接分享图片到本助手。", true);
            return;
        }
        process(uri, "最近截图", "正在识别最近一张截图…");
    }

    private void process(Uri uri, String sourceLabel, String message) {
        showLoading(message);
        ImageOcrProcessor.process(this, uri, sourceLabel, new ImageOcrProcessor.Callback() {
            @Override
            public void onSuccess(TransactionDraft draft, String privateImagePath) {
                if (!draft.hasBillKeywords()) {
                    showError("未识别到账单", recentMode);
                    return;
                }
                if (draft.hasAmount() && XiaoXingLauncher.openDialog(ImportImageActivity.this, draft)) {
                    moveTaskToBack(true);
                    return;
                }
                startActivity(new Intent(ImportImageActivity.this, ReviewActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(CaptureContract.EXTRA_DRAFT, draft)
                        .putExtra(CaptureContract.EXTRA_SCREENSHOT, privateImagePath));
                finish();
            }

            @Override
            public void onError(String message) {
                showError(message, true);
            }
        });
    }

    private void showPermissionRequired() {
        progress.setVisibility(View.GONE);
        status.setText("需要照片读取权限，才能自动找到系统媒体库中最近一张截图。\n不会读取视频，也不会申请全盘文件权限。");
        action.setText("授予照片权限");
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(view -> requestPhotoAccess());
    }

    private void showLoading(String message) {
        progress.setVisibility(View.VISIBLE);
        status.setText(message + "\nOCR 在本机离线完成，图片不会上传。" );
        action.setVisibility(View.GONE);
    }

    private void showError(String message, boolean retry) {
        progress.setVisibility(View.GONE);
        status.setText(message);
        action.setVisibility(View.VISIBLE);
        action.setText(retry ? "重试" : "返回");
        action.setOnClickListener(view -> {
            if (retry && recentMode) {
                if (hasPhotoAccess()) processRecent(); else requestPhotoAccess();
            } else if (retry) {
                handle(getIntent());
            } else {
                finish();
            }
        });
    }

    private void requestPhotoAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}, REQUEST_IMAGES);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_IMAGES);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_IMAGES);
        }
    }

    private boolean hasPhotoAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) return true;
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_IMAGES) return;
        if (hasPhotoAccess()) processRecent();
        else {
            status.setText("没有照片读取权限，无法自动定位最近截图。\n你仍然可以在相册中使用“分享”进入本助手。");
            action.setText("打开权限设置");
            action.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
        }
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(color(R.color.surface));

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(R.color.surface_card));
        card.setRadius(dp(24));
        card.setCardElevation(dp(2));
        card.setStrokeColor(color(R.color.outline_soft));
        card.setStrokeWidth(dp(1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(24));
        TextView title = text("图片识别记账", 23, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);
        TextView privacy = text("本机 OCR · 私密处理", 12, R.color.brand);
        content.addView(privacy, top(6));

        progress = new ProgressBar(this);
        progress.setIndeterminateTintList(ColorStateList.valueOf(color(R.color.brand)));
        content.addView(progress, centeredTop(48, 24));

        status = text("准备读取图片…", 14, R.color.text_secondary);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(dp(3), 1f);
        content.addView(status, top(18));

        action = new MaterialButton(this);
        action.setTextSize(15);
        action.setAllCaps(false);
        action.setCornerRadius(dp(15));
        action.setInsetTop(0);
        action.setInsetBottom(0);
        action.setBackgroundTintList(ColorStateList.valueOf(color(R.color.brand)));
        action.setTextColor(color(R.color.on_brand));
        action.setVisibility(View.GONE);
        content.addView(action, sizedTop(54, 22));
        card.addView(content);
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static Uri streamExtra(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        return intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    private TextView text(String value, int sp, int colorResource) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color(colorResource));
        return view;
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(margin);
        return params;
    }

    private LinearLayout.LayoutParams sizedTop(int height, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(height));
        params.topMargin = dp(margin);
        return params;
    }

    private LinearLayout.LayoutParams centeredTop(int size, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(size), dp(size));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(margin);
        return params;
    }

    private int color(int resource) { return ContextCompat.getColor(this, resource); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
