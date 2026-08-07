package com.xingledger.quickcapture;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlacklistActivity extends AppCompatActivity {
    private final List<AppItem> allApps = new ArrayList<>();
    private AppAdapter adapter;
    private TextView summary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        loadApps();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), 0);
        root.setBackgroundColor(color(R.color.surface));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton back = iconButton();
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        TextView title = text("截图黑名单", 23, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titles.addView(title);
        summary = text("加载应用中…", 12, R.color.text_secondary);
        titles.addView(summary);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);

        TextInputLayout searchBox = new TextInputLayout(this,
                null, com.google.android.material.R.attr.textInputOutlinedStyle);
        searchBox.setHint("搜索应用名称或包名");
        searchBox.setBoxCornerRadii(dp(16), dp(16), dp(16), dp(16));
        searchBox.setStartIconDrawable(R.drawable.ic_search);
        TextInputEditText search = new TextInputEditText(searchBox.getContext());
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                filter(value == null ? "" : value.toString());
            }
            @Override public void afterTextChanged(Editable value) {}
        });
        searchBox.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = dp(16);
        root.addView(searchBox, searchParams);

        TextView tip = text("启用后，磁贴和当前屏幕 am 入口在这些应用前台时会直接跳过。需要授予“使用情况访问权限”才能自动判断前台 App。",
                12, R.color.text_secondary);
        tip.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipParams.topMargin = dp(10);
        root.addView(tip, tipParams);

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setPadding(0, dp(12), 0, dp(24));
        adapter = new AppAdapter(this::updateSummary);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void loadApps() {
        PackageManager manager = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolved = manager.queryIntentActivities(launcher,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL));
        } else {
            //noinspection deprecation
            resolved = manager.queryIntentActivities(launcher, PackageManager.MATCH_ALL);
        }
        Map<String, AppItem> unique = new LinkedHashMap<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) continue;
            String packageName = info.activityInfo.packageName;
            if (getPackageName().equals(packageName) || unique.containsKey(packageName)) continue;
            CharSequence label = info.loadLabel(manager);
            unique.put(packageName, new AppItem(packageName,
                    label == null ? packageName : label.toString(), info.loadIcon(manager)));
        }
        allApps.clear();
        allApps.addAll(unique.values());
        Collator collator = Collator.getInstance(Locale.CHINA);
        allApps.sort((left, right) -> collator.compare(left.label, right.label));
        adapter.replace(allApps);
        updateSummary();
    }

    private void filter(String query) {
        if (adapter == null) return;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            adapter.replace(allApps);
            return;
        }
        List<AppItem> filtered = new ArrayList<>();
        for (AppItem app : allApps) {
            if (app.label.toLowerCase(Locale.ROOT).contains(needle)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.add(app);
            }
        }
        adapter.replace(filtered);
    }

    private void updateSummary() {
        summary.setText(getString(R.string.blacklist_summary, AppBlacklist.count(this)));
    }

    private MaterialButton iconButton() {
        MaterialButton button = new MaterialButton(this);
        button.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_arrow_back));
        button.setIconTint(ColorStateList.valueOf(color(R.color.brand)));
        button.setIconPadding(0);
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.surface_soft)));
        button.setCornerRadius(dp(14));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setContentDescription("返回");
        return button;
    }

    private TextView text(String value, int size, int colorResource) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(colorResource));
        return view;
    }

    private int color(int resource) { return ContextCompat.getColor(this, resource); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
        private final List<AppItem> shown = new ArrayList<>();
        private final Runnable onChanged;

        AppAdapter(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        void replace(List<AppItem> apps) {
            shown.clear();
            shown.addAll(apps);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(parent.getContext());
            card.setRadius(dp(17));
            card.setCardBackgroundColor(color(R.color.surface_card));
            card.setStrokeColor(color(R.color.outline_soft));
            card.setStrokeWidth(dp(1));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(76));
            params.bottomMargin = dp(8);
            card.setLayoutParams(params);

            LinearLayout row = new LinearLayout(parent.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(10), dp(8));
            ImageView icon = new ImageView(parent.getContext());
            row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            LinearLayout labels = new LinearLayout(parent.getContext());
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(12), 0, dp(8), 0);
            TextView name = text("", 15, R.color.text_primary);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            name.setSingleLine(true);
            labels.addView(name);
            TextView packageName = text("", 11, R.color.text_secondary);
            packageName.setSingleLine(true);
            labels.addView(packageName);
            row.addView(labels, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialSwitch toggle = new MaterialSwitch(parent.getContext());
            row.addView(toggle);
            card.addView(row);
            return new Holder(card, icon, name, packageName, toggle);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            AppItem item = shown.get(position);
            holder.icon.setImageDrawable(item.icon);
            holder.name.setText(item.label);
            holder.packageName.setText(item.packageName);
            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(AppBlacklist.isBlocked(BlacklistActivity.this, item.packageName));
            holder.toggle.setOnCheckedChangeListener((button, checked) -> {
                AppBlacklist.setBlocked(BlacklistActivity.this, item.packageName, checked);
                onChanged.run();
            });
            holder.itemView.setOnClickListener(view -> holder.toggle.setChecked(!holder.toggle.isChecked()));
        }

        @Override public int getItemCount() { return shown.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView packageName;
            final MaterialSwitch toggle;

            Holder(View itemView, ImageView icon, TextView name,
                   TextView packageName, MaterialSwitch toggle) {
                super(itemView);
                this.icon = icon;
                this.name = name;
                this.packageName = packageName;
                this.toggle = toggle;
            }
        }
    }

    private static final class AppItem {
        final String packageName;
        final String label;
        final Drawable icon;

        AppItem(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }
}
