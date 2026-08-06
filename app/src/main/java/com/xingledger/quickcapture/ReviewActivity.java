package com.xingledger.quickcapture;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;

public final class ReviewActivity extends AppCompatActivity {
    private String selectedType = "支出";
    private EditText amount;
    private EditText shop;
    private EditText account;
    private EditText account2;
    private EditText time;
    private EditText channel;
    private EditText remark;
    private TransactionDraft source;
    private String screenshotPath;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showIntent(intent);
    }

    private void showIntent(Intent intent) {
        source = draftExtra(intent);
        if (source == null) source = new TransactionDraft();
        screenshotPath = intent.getStringExtra(CaptureContract.EXTRA_SCREENSHOT);
        setContentView(buildContent(screenshotPath));
    }

    private View buildContent(@Nullable String screenshotPath) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(12), dp(20), dp(36));
        body.setBackgroundColor(color(R.color.surface));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton back = iconButton(R.drawable.ic_arrow_back, "返回");
        back.setOnClickListener(view -> finish());
        topBar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = text("确认这笔账", 23, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title);
        heading.addView(text("识别结果可以直接修正", 12, R.color.text_secondary));
        topBar.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(topBar);

        Bitmap previewBitmap = decodePreview(screenshotPath, 1200, 800);
        if (previewBitmap != null) {
            MaterialCardView previewCard = card(R.color.surface_card, 18);
            ShapeableImageView preview = new ShapeableImageView(this);
            preview.setImageBitmap(previewBitmap);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                    .setAllCornerSizes(dp(18)).build());
            previewCard.addView(preview, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(154)));
            body.addView(previewCard, top(18));
        }

        TextView typeLabel = text("账目类型", 13, R.color.text_secondary);
        typeLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(typeLabel, top(20));

        MaterialButtonToggleGroup typeGroup = new MaterialButtonToggleGroup(this);
        typeGroup.setSingleSelection(true);
        typeGroup.setSelectionRequired(true);
        String[] types = {"支出", "收入", "转账", "借贷"};
        for (String candidate : types) {
            MaterialButton choice = new MaterialButton(this);
            choice.setId(View.generateViewId());
            choice.setText(candidate);
            choice.setTextSize(13);
            choice.setAllCaps(false);
            choice.setInsetTop(0);
            choice.setInsetBottom(0);
            choice.setTag(candidate);
            typeGroup.addView(choice, new LinearLayout.LayoutParams(0, dp(46), 1f));
            if (candidate.equals(source.type)) {
                selectedType = candidate;
                typeGroup.check(choice.getId());
            }
        }
        if (typeGroup.getCheckedButtonId() == View.NO_ID) typeGroup.check(typeGroup.getChildAt(0).getId());
        typeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) selectedType = String.valueOf(group.findViewById(checkedId).getTag());
        });
        body.addView(typeGroup, top(8));

        amount = field(body, "金额", source.amount,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, true);
        shop = field(body, "商户 / 对方", source.shop, InputType.TYPE_CLASS_TEXT, false);
        account = field(body, "账户", source.account, InputType.TYPE_CLASS_TEXT, false);
        account2 = field(body, "转入账户（转账 / 借贷）", source.account2, InputType.TYPE_CLASS_TEXT, false);
        time = field(body, "时间", source.time, InputType.TYPE_CLASS_TEXT, false);
        channel = field(body, "渠道", source.channel, InputType.TYPE_CLASS_TEXT, false);
        remark = field(body, "备注", source.remark,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, false);

        MaterialButton submit = new MaterialButton(this);
        submit.setText("交给小星记账确认");
        submit.setTextSize(16);
        submit.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        submit.setAllCaps(false);
        submit.setCornerRadius(dp(16));
        submit.setInsetTop(0);
        submit.setInsetBottom(0);
        submit.setBackgroundTintList(ColorStateList.valueOf(color(R.color.brand)));
        submit.setTextColor(color(R.color.on_brand));
        submit.setOnClickListener(view -> openXiaoXing());
        body.addView(submit, sizedTop(58, 22));

        TextView raw = text(source.rawText == null ? "" : source.rawText, 12, R.color.text_secondary);
        raw.setVisibility(View.GONE);
        raw.setTextIsSelectable(true);
        raw.setPadding(dp(14), dp(12), dp(14), dp(12));
        raw.setBackground(rounded(R.color.surface_soft, 14));
        MaterialButton toggle = new MaterialButton(this);
        toggle.setText(R.string.show_ocr_text);
        toggle.setTextSize(13);
        toggle.setAllCaps(false);
        toggle.setTextColor(color(R.color.brand));
        toggle.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        toggle.setOnClickListener(view -> {
            boolean show = raw.getVisibility() != View.VISIBLE;
            raw.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? R.string.hide_ocr_text : R.string.show_ocr_text);
        });
        body.addView(toggle, top(8));
        body.addView(raw, top(4));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        return scroll;
    }

    private EditText field(LinearLayout parent, String hint, String value, int inputType, boolean prominent) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(color(R.color.surface_card));
        layout.setBoxStrokeColor(color(prominent ? R.color.brand : R.color.outline_soft));
        layout.setBoxCornerRadii(dp(14), dp(14), dp(14), dp(14));
        layout.setHintTextColor(ColorStateList.valueOf(color(R.color.text_secondary)));
        if (prominent) layout.setSuffixText("元");

        TextInputEditText field = new TextInputEditText(this);
        field.setText(value == null ? "" : value);
        field.setTextSize(prominent ? 22 : 15);
        field.setTypeface(Typeface.DEFAULT, prominent ? Typeface.BOLD : Typeface.NORMAL);
        field.setSingleLine(!"备注".equals(hint));
        field.setInputType(inputType);
        layout.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, prominent ? dp(62) : dp(56)));
        parent.addView(layout, top(12));
        return field;
    }

    private void openXiaoXing() {
        TransactionDraft draft = new TransactionDraft();
        draft.type = selectedType;
        draft.amount = value(amount);
        draft.shop = value(shop);
        draft.account = value(account);
        draft.account2 = value(account2);
        draft.time = value(time);
        draft.channel = value(channel);
        draft.remark = value(remark);

        if (draft.amount.isEmpty()) {
            amount.setError("请填写金额");
            amount.requestFocus();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, XiaoXingScheme.buildDialogUri(draft)));
            // Keep this task alive behind XiaoXing. Some Android emulators kill the
            // whole package (including the MediaProjection foreground service) as
            // soon as its last task is removed, which would force consent per entry.
            // The next capture replaces this screen through the capture task.
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "没有找到小星记账，请先安装，并在扩展功能中开启 URL Scheme", Toast.LENGTH_LONG).show();
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法唤起小星记账：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private MaterialButton iconButton(int icon, String description) {
        MaterialButton button = new MaterialButton(this);
        button.setIcon(ContextCompat.getDrawable(this, icon));
        button.setIconTint(ColorStateList.valueOf(color(R.color.brand)));
        button.setIconPadding(0);
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.surface_soft)));
        button.setCornerRadius(dp(14));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setContentDescription(description);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        return button;
    }

    private MaterialCardView card(int background, int radius) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(background));
        card.setRadius(dp(radius));
        card.setCardElevation(dp(1));
        return card;
    }

    private String value(EditText field) { return field.getText().toString().trim(); }

    private TextView text(String value, int sp, int colorResource) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color(colorResource));
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    private android.graphics.drawable.GradientDrawable rounded(int colorResource, int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color(colorResource));
        shape.setCornerRadius(dp(radius));
        return shape;
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

    private int color(int resource) { return ContextCompat.getColor(this, resource); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Nullable
    @SuppressWarnings("deprecation")
    private static TransactionDraft draftExtra(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getSerializableExtra(CaptureContract.EXTRA_DRAFT, TransactionDraft.class);
        }
        return (TransactionDraft) intent.getSerializableExtra(CaptureContract.EXTRA_DRAFT);
    }

    @Nullable
    private static Bitmap decodePreview(@Nullable String path, int maxWidth, int maxHeight) {
        if (path == null || !new File(path).isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > maxWidth || bounds.outHeight / sample > maxHeight) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, options);
    }
}
