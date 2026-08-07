package com.xingledger.quickcapture;

import android.Manifest;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends AppCompatActivity implements ScreenshotAdapter.Listener {
    private static final int REQUEST_PROJECTION = 7001;
    private TextView status;
    private TextView recordCount;
    private TextView recordTitle;
    private TextView empty;
    private TextView usageStatus;
    private ScreenshotAdapter adapter;
    private ChipGroup appFilters;
    private TextInputEditText searchInput;
    private final List<ScreenshotRecord> allRecords = new ArrayList<>();
    private String selectedAppKey = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        reloadRecords();
        updateUsageStatus();
    }

    private View buildContent() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(14), pad, 0);
        root.setBackgroundColor(color(R.color.surface));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("小星记账助手", 12, color(R.color.brand));
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eyebrow.setLetterSpacing(.08f);
        titles.addView(eyebrow);
        TextView title = text("截图记录", 28, color(R.color.text_primary));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titles.addView(title, top(2));
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton settings = iconButton(R.drawable.ic_settings, "设置");
        settings.setOnClickListener(view -> showSettings());
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, matchWrap());

        MaterialCardView hero = card(color(R.color.brand_container), 22, 0);
        LinearLayout heroBody = new LinearLayout(this);
        heroBody.setOrientation(LinearLayout.HORIZONTAL);
        heroBody.setGravity(Gravity.CENTER_VERTICAL);
        heroBody.setPadding(dp(18), dp(18), dp(18), dp(18));

        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        TextView heroLabel = text("屏幕快记", 13, color(R.color.on_brand_container));
        heroLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heroText.addView(heroLabel);
        status = text("", 16, color(R.color.on_brand_container));
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heroText.addView(status, top(4));
        TextView privacy = text("图片仅保存在应用内部，不进相册", 12, color(R.color.text_secondary));
        heroText.addView(privacy, top(4));
        heroBody.addView(heroText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout countBox = new LinearLayout(this);
        countBox.setOrientation(LinearLayout.VERTICAL);
        countBox.setGravity(Gravity.CENTER);
        countBox.setPadding(dp(13), dp(8), dp(13), dp(8));
        countBox.setBackground(rounded(color(R.color.surface_card), 16));
        recordCount = text("0", 24, color(R.color.brand));
        recordCount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        countBox.addView(recordCount);
        TextView unit = text("张记录", 11, color(R.color.text_secondary));
        countBox.addView(unit);
        heroBody.addView(countBox, new LinearLayout.LayoutParams(dp(76), dp(72)));
        hero.addView(heroBody);
        root.addView(hero, top(18));

        TextInputLayout searchBox = new TextInputLayout(this,
                null, com.google.android.material.R.attr.textInputOutlinedStyle);
        searchBox.setHint("搜索 App、商户、金额或 OCR 文字");
        searchBox.setBoxCornerRadii(dp(16), dp(16), dp(16), dp(16));
        searchBox.setStartIconDrawable(R.drawable.ic_search);
        searchInput = new TextInputEditText(searchBox.getContext());
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                applyFilters();
            }
            @Override public void afterTextChanged(Editable value) {}
        });
        searchBox.addView(searchInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(searchBox, top(16));

        appFilters = new ChipGroup(this);
        appFilters.setSingleSelection(true);
        appFilters.setSelectionRequired(true);
        appFilters.setChipSpacingHorizontal(dp(8));
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.addView(appFilters, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(filterScroll, top(8));

        LinearLayout section = new LinearLayout(this);
        section.setGravity(Gravity.BOTTOM);
        TextView recent = text("最近截图", 18, color(R.color.text_primary));
        recent.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.addView(recent, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        recordTitle = text("左右滑动删除", 12, color(R.color.text_secondary));
        section.addView(recordTitle);
        root.addView(section, top(22));

        FrameLayout listArea = new FrameLayout(this);
        RecyclerView recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setClipToPadding(false);
        recycler.setPadding(0, dp(10), 0, dp(24));
        adapter = new ScreenshotAdapter(this);
        recycler.setAdapter(adapter);
        listArea.addView(recycler, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = text("还没有截图记录\n开启截图授权并添加磁贴后，\n在账单页面点一下就能识别。", 15, color(R.color.text_secondary));
        empty.setGravity(Gravity.CENTER);
        empty.setLineSpacing(dp(4), 1f);
        listArea.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                ScreenshotRecord record = adapter.get(viewHolder.getBindingAdapterPosition());
                if (record != null) deleteRecord(record);
            }
        }).attachToRecyclerView(recycler);
        root.addView(listArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void showSettings() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(10), dp(20), dp(30));
        panel.setBackgroundColor(color(R.color.surface_card));

        View handle = new View(this);
        handle.setBackground(rounded(color(R.color.outline_soft), 99));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(38), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(handle, handleParams);

        TextView title = text("设置与授权", 23, color(R.color.text_primary));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(title, top(18));
        panel.addView(text("先授权一次，之后用快捷磁贴即可快速截图记账。", 13,
                color(R.color.text_secondary)), top(4));

        MaterialCardView serviceCard = card(color(R.color.surface), 18, 0);
        LinearLayout service = new LinearLayout(this);
        service.setOrientation(LinearLayout.VERTICAL);
        service.setPadding(dp(14), dp(14), dp(14), dp(14));
        TextView serviceTitle = text("截图服务", 15, color(R.color.text_primary));
        serviceTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        service.addView(serviceTitle);

        MaterialButton enable = actionButton("开启持续截图授权", true);
        enable.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_camera));
        enable.setIconTint(ColorStateList.valueOf(color(R.color.on_brand)));
        enable.setOnClickListener(view -> requestProjection());
        service.addView(enable, top(12));

        MaterialButton tile = actionButton("添加“截图记账”磁贴", false);
        tile.setOnClickListener(view -> addTile(ScreenshotTileService.class,
                R.string.tile_label, R.drawable.ic_tile));
        service.addView(tile, top(8));

        MaterialButton recentTile = actionButton("添加“识别最近截图”磁贴", false);
        recentTile.setOnClickListener(view -> addTile(RecentScreenshotTileService.class,
                R.string.recent_tile_label, R.drawable.ic_recent_tile));
        service.addView(recentTile, top(8));

        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton test = actionButton("测试截图", false);
        test.setOnClickListener(view -> {
            dialog.dismiss();
            startActivity(new Intent(this, CaptureActivity.class)
                    .putExtra(CaptureContract.EXTRA_SOURCE_APP, getString(R.string.app_name))
                    .putExtra(CaptureContract.EXTRA_SOURCE_PACKAGE, getPackageName()));
        });
        secondary.addView(test, new LinearLayout.LayoutParams(0, dp(48), 1f));
        MaterialButton stop = actionButton("停止服务", false);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        stopParams.leftMargin = dp(8);
        stop.setOnClickListener(view -> {
            startService(new Intent(this, CaptureService.class).setAction(CaptureContract.ACTION_STOP));
            status.postDelayed(this::updateStatus, 250);
        });
        secondary.addView(stop, stopParams);
        service.addView(secondary, top(8));
        serviceCard.addView(service);
        panel.addView(serviceCard, top(18));

        TextView sourceTitle = text("来源 App 标注", 15, color(R.color.text_primary));
        sourceTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(sourceTitle, top(22));
        usageStatus = text("", 13, color(R.color.text_secondary));
        updateUsageStatus();
        panel.addView(usageStatus, top(5));
        MaterialButton usage = actionButton("授权读取当前应用名称", false);
        usage.setOnClickListener(view -> openUsageAccessSettings());
        panel.addView(usage, top(10));

        MaterialButton blacklist = actionButton(
                getString(R.string.blacklist_manage, AppBlacklist.count(this)), false);
        blacklist.setOnClickListener(view -> {
            dialog.dismiss();
            startActivity(new Intent(this, BlacklistActivity.class));
        });
        panel.addView(blacklist, top(8));

        panel.addView(text(
                "这是可选权限；未授权时会用 OCR 推断微信、支付宝等渠道，但无法可靠执行截图黑名单。\n\n小星记账还需要开启：侧边栏 → 设置 → 扩展功能 → URL Scheme。",
                12, color(R.color.text_secondary)), top(16));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        dialog.setContentView(scroll);
        dialog.show();
    }

    private void reloadRecords() {
        if (adapter == null) return;
        allRecords.clear();
        allRecords.addAll(ScreenshotRepository.list(this));
        rebuildAppFilters();
        applyFilters();
        recordCount.setText(String.valueOf(allRecords.size()));
    }

    private void rebuildAppFilters() {
        if (appFilters == null) return;
        Map<String, String> apps = new LinkedHashMap<>();
        for (ScreenshotRecord record : allRecords) {
            apps.putIfAbsent(appKey(record), record.appLabel.isEmpty() ? "未知应用" : record.appLabel);
        }
        if (!selectedAppKey.isEmpty() && !apps.containsKey(selectedAppKey)) selectedAppKey = "";
        appFilters.removeAllViews();
        addAppFilter("", "全部");
        for (Map.Entry<String, String> item : apps.entrySet()) addAppFilter(item.getKey(), item.getValue());
    }

    private void addAppFilter(String key, String label) {
        Chip chip = new Chip(this);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(key.equals(selectedAppKey));
        chip.setOnClickListener(view -> {
            selectedAppKey = key;
            applyFilters();
        });
        appFilters.addView(chip);
    }

    private void applyFilters() {
        if (adapter == null || empty == null) return;
        String query = searchInput == null || searchInput.getText() == null
                ? "" : searchInput.getText().toString();
        List<ScreenshotRecord> filtered = new ArrayList<>();
        for (ScreenshotRecord record : allRecords) {
            if (!selectedAppKey.isEmpty() && !selectedAppKey.equals(appKey(record))) continue;
            if (record.matchesQuery(query)) filtered.add(record);
        }
        adapter.replace(filtered);
        empty.setText(allRecords.isEmpty()
                ? "还没有截图记录\n开启截图授权并添加磁贴后，\n在账单页面点一下就能识别。"
                : "没有匹配的截图\n换个 App 或关键词试试");
        empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        recordTitle.setText(allRecords.isEmpty() ? "等待第一张"
                : filtered.size() + " / " + allRecords.size() + " · 时间线新到旧");
    }

    private static String appKey(ScreenshotRecord record) {
        return record.appPackage.isEmpty() ? "label:" + record.appLabel : "package:" + record.appPackage;
    }

    @Override
    public void onOpen(ScreenshotRecord record) {
        startActivity(new Intent(this, ScreenshotViewerActivity.class)
                .putExtra(CaptureContract.EXTRA_RECORD_PATH, record.imagePath));
    }

    @Override
    public void onDelete(ScreenshotRecord record) {
        deleteRecord(record);
    }

    private void deleteRecord(ScreenshotRecord record) {
        ScreenshotRepository.delete(record);
        reloadRecords();
        Toast.makeText(this, "截图已删除", Toast.LENGTH_SHORT).show();
    }

    private void requestProjection() {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(ProjectionConsent.createIntent(manager), REQUEST_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROJECTION) return;
        if (resultCode != Activity.RESULT_OK || data == null) {
            Toast.makeText(this, "未授予截图权限", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent service = new Intent(this, CaptureService.class).setAction(CaptureContract.ACTION_START);
        service.putExtra(CaptureContract.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(CaptureContract.EXTRA_RESULT_DATA, data);
        DisplayInfo.from(this).putInto(service);
        ContextCompat.startForegroundService(this, service);
        status.postDelayed(this::updateStatus, 600);
    }

    private void addTile(Class<?> serviceClass, int labelResource, int iconResource) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StatusBarManager manager = getSystemService(StatusBarManager.class);
            if (manager == null) return;
            manager.requestAddTileService(
                    new ComponentName(this, serviceClass),
                    getString(labelResource), Icon.createWithResource(this, iconResource),
                    getMainExecutor(), result -> Toast.makeText(this, tileResultMessage(result), Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(this, "请在快捷设置编辑页手动添加对应磁贴", Toast.LENGTH_LONG).show();
        }
    }

    private String tileResultMessage(int result) {
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) return "磁贴已添加";
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) return "磁贴已经存在";
        return "未添加磁贴，可在快捷设置编辑页手动添加";
    }

    private void openUsageAccessSettings() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    private void updateStatus() {
        if (status == null) return;
        status.setText(CaptureService.isReady() ? "已就绪，点磁贴即可" : "等待开启截图授权");
        status.setTextColor(color(CaptureService.isReady() ? R.color.success : R.color.on_brand_container));
    }

    private void updateUsageStatus() {
        if (usageStatus == null) return;
        usageStatus.setText(ForegroundAppDetector.hasPermission(this)
                ? "已授权 · 会显示真实来源 App" : "未授权 · 当前使用 OCR 渠道推断");
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7002);
        }
    }

    private MaterialCardView card(int background, int radius, int elevation) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(background);
        card.setRadius(dp(radius));
        card.setCardElevation(dp(elevation));
        card.setStrokeWidth(0);
        return card;
    }

    private MaterialButton iconButton(int icon, String description) {
        MaterialButton button = new MaterialButton(this);
        button.setIcon(ContextCompat.getDrawable(this, icon));
        button.setIconTint(ColorStateList.valueOf(color(R.color.brand)));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(0);
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.surface_soft)));
        button.setCornerRadius(dp(16));
        button.setContentDescription(description);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        return button;
    }

    private MaterialButton actionButton(String label, boolean primary) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setCornerRadius(dp(14));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(dp(48));
        button.setBackgroundTintList(ColorStateList.valueOf(color(primary ? R.color.brand : R.color.surface_card)));
        button.setTextColor(color(primary ? R.color.on_brand : R.color.brand));
        if (!primary) {
            button.setStrokeColor(ColorStateList.valueOf(color(R.color.outline_soft)));
            button.setStrokeWidth(dp(1));
        }
        return button;
    }

    private android.graphics.drawable.GradientDrawable rounded(int color, int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.18f);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(margin);
        return params;
    }

    private int color(int resource) {
        return ContextCompat.getColor(this, resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
