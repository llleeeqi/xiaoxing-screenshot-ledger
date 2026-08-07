package com.xingledger.quickcapture;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ScreenshotViewerActivity extends AppCompatActivity {
    private ScreenshotRecord record;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        record = ScreenshotRepository.find(this,
                getIntent().getStringExtra(CaptureContract.EXTRA_RECORD_PATH));
        if (record == null) {
            Toast.makeText(this, "截图不存在或已删除", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(buildContent());
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.setBackgroundColor(color(R.color.surface));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton back = iconButton(R.drawable.ic_arrow_back, R.color.surface_soft, "返回");
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(8), 0);
        TextView app = text(record.appLabel.isEmpty() ? "未知应用" : record.appLabel, 18, R.color.text_primary);
        app.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(app);
        String timestamp = new SimpleDateFormat("yyyy年MM月dd日  HH:mm:ss", Locale.CHINA)
                .format(new Date(record.capturedAt));
        TextView time = text(timestamp, 12, R.color.text_secondary);
        labels.addView(time);
        header.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton delete = iconButton(R.drawable.ic_delete, R.color.error_container, "删除截图");
        delete.setOnClickListener(view -> {
            ScreenshotRepository.delete(record);
            Toast.makeText(this, "截图已删除", Toast.LENGTH_SHORT).show();
            finish();
        });
        header.addView(delete, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(header);

        if (!record.channel.isEmpty()) {
            TextView channel = text("OCR 识别渠道 · " + record.channel, 12, R.color.brand);
            channel.setPadding(dp(12), dp(7), dp(12), dp(7));
            channel.setBackground(rounded(R.color.brand_container, 99));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.topMargin = dp(12);
            root.addView(channel, chipParams);
        }

        if (!record.ocrText.isEmpty()) {
            MaterialCardView ocrCard = new MaterialCardView(this);
            ocrCard.setRadius(dp(16));
            ocrCard.setCardBackgroundColor(color(R.color.surface_card));
            ocrCard.setStrokeColor(color(R.color.outline_soft));
            ocrCard.setStrokeWidth(dp(1));
            LinearLayout ocrBody = new LinearLayout(this);
            ocrBody.setOrientation(LinearLayout.VERTICAL);
            ocrBody.setPadding(dp(14), dp(11), dp(14), dp(11));
            TextView ocrTitle = text("OCR 文字索引", 12, R.color.brand);
            ocrTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            ocrBody.addView(ocrTitle);
            TextView ocrText = text(record.ocrText, 12, R.color.text_secondary);
            ocrText.setMaxLines(4);
            ocrText.setEllipsize(TextUtils.TruncateAt.END);
            ocrText.setTextIsSelectable(true);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.topMargin = dp(5);
            ocrBody.addView(ocrText, textParams);
            ocrCard.addView(ocrBody);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.topMargin = dp(10);
            root.addView(ocrCard, cardParams);
        }

        MaterialCardView imageCard = new MaterialCardView(this);
        imageCard.setRadius(dp(18));
        imageCard.setCardBackgroundColor(Color.rgb(27, 25, 31));
        imageCard.setCardElevation(dp(1));

        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap bitmap = BitmapFactory.decodeFile(record.imagePath);
        image.setImageBitmap(bitmap);
        imageCard.addView(image, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(imageCard, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(12);
        root.addView(scroll, scrollParams);
        return root;
    }

    private MaterialButton iconButton(int icon, int background, String description) {
        MaterialButton button = new MaterialButton(this);
        button.setIcon(ContextCompat.getDrawable(this, icon));
        button.setIconTint(ColorStateList.valueOf(color(icon == R.drawable.ic_delete
                ? R.color.error : R.color.brand)));
        button.setIconPadding(0);
        button.setBackgroundTintList(ColorStateList.valueOf(color(background)));
        button.setCornerRadius(dp(14));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setContentDescription(description);
        return button;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color(color));
        return view;
    }

    private android.graphics.drawable.GradientDrawable rounded(int colorResource, int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color(colorResource));
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int color(int resource) { return ContextCompat.getColor(this, resource); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
