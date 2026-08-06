package com.xingledger.quickcapture;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ScreenshotAdapter extends RecyclerView.Adapter<ScreenshotAdapter.Holder> {
    public interface Listener {
        void onOpen(ScreenshotRecord record);
        void onDelete(ScreenshotRecord record);
    }

    private final List<ScreenshotRecord> records = new ArrayList<>();
    private final Listener listener;
    private final LruCache<String, Bitmap> thumbnails;

    public ScreenshotAdapter(Listener listener) {
        this.listener = listener;
        int cacheKb = Math.max(4 * 1024, (int) (Runtime.getRuntime().maxMemory() / 1024L / 16L));
        thumbnails = new LruCache<String, Bitmap>(cacheKb) {
            @Override protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
                return value.getAllocationByteCount() / 1024;
            }
        };
        setHasStableIds(true);
    }

    public void replace(List<ScreenshotRecord> updated) {
        records.clear();
        records.addAll(updated);
        notifyDataSetChanged();
    }

    public ScreenshotRecord get(int position) {
        return position >= 0 && position < records.size() ? records.get(position) : null;
    }

    @Override public long getItemId(int position) { return records.get(position).capturedAt; }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(color(context, R.color.surface_card));
        card.setRadius(dp(context, 18));
        card.setCardElevation(dp(context, 1));
        card.setStrokeColor(color(context, R.color.outline_soft));
        card.setStrokeWidth(dp(context, 1));
        RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 126));
        cardParams.bottomMargin = dp(context, 10);
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 10), dp(context, 10), dp(context, 8), dp(context, 10));

        ShapeableImageView image = new ShapeableImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                .setAllCornerSizes(dp(context, 12)).build());
        image.setStrokeColor(ColorStateList.valueOf(color(context, R.color.outline_soft)));
        image.setStrokeWidth(dp(context, 1));
        image.setBackgroundColor(color(context, R.color.surface_soft));
        row.addView(image, new LinearLayout.LayoutParams(dp(context, 78), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        details.setPadding(dp(context, 13), 0, dp(context, 6), 0);
        row.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView app = text(context, 17, R.color.text_primary);
        app.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        app.setSingleLine(true);
        details.addView(app);

        TextView time = text(context, 12, R.color.text_secondary);
        LinearLayout.LayoutParams timeParams = wrap();
        timeParams.topMargin = dp(context, 5);
        details.addView(time, timeParams);

        TextView channel = text(context, 11, R.color.brand);
        channel.setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3));
        channel.setBackground(pill(context));
        LinearLayout.LayoutParams channelParams = wrap();
        channelParams.topMargin = dp(context, 7);
        details.addView(channel, channelParams);

        MaterialButton delete = new MaterialButton(context);
        delete.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_delete));
        delete.setIconTint(ColorStateList.valueOf(color(context, R.color.error)));
        delete.setIconPadding(0);
        delete.setBackgroundTintList(ColorStateList.valueOf(color(context, R.color.error_container)));
        delete.setCornerRadius(dp(context, 14));
        delete.setInsetTop(0);
        delete.setInsetBottom(0);
        delete.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        delete.setContentDescription("删除截图");
        row.addView(delete, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));

        card.addView(row);
        return new Holder(card, image, app, time, channel, delete);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ScreenshotRecord record = records.get(position);
        holder.app.setText(record.appLabel.isEmpty() ? "未知应用" : record.appLabel);
        holder.time.setText(new SimpleDateFormat("MM月dd日  HH:mm:ss", Locale.CHINA)
                .format(new Date(record.capturedAt)));
        holder.channel.setText(record.channel.isEmpty() ? "" : record.channel);
        holder.channel.setVisibility(record.channel.isEmpty() ? View.GONE : View.VISIBLE);

        Bitmap bitmap = thumbnails.get(record.imagePath);
        if (bitmap == null || bitmap.isRecycled()) {
            bitmap = decodeSampled(record.imagePath, 240, 360);
            if (bitmap != null) thumbnails.put(record.imagePath, bitmap);
        }
        holder.image.setImageBitmap(bitmap);
        holder.itemView.setOnClickListener(view -> listener.onOpen(record));
        holder.delete.setOnClickListener(view -> listener.onDelete(record));
    }

    @Override public int getItemCount() { return records.size(); }

    private static Bitmap decodeSampled(String path, int maxWidth, int maxHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > maxWidth || bounds.outHeight / sample > maxHeight) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, options);
    }

    private static TextView text(Context context, int sp, int color) {
        TextView view = new TextView(context);
        view.setTextSize(sp);
        view.setTextColor(color(context, color));
        return view;
    }

    private static GradientDrawable pill(Context context) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color(context, R.color.brand_container));
        shape.setCornerRadius(dp(context, 99));
        return shape;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int color(Context context, int resource) { return ContextCompat.getColor(context, resource); }
    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView app;
        final TextView time;
        final TextView channel;
        final MaterialButton delete;

        Holder(View itemView, ImageView image, TextView app, TextView time,
               TextView channel, MaterialButton delete) {
            super(itemView);
            this.image = image;
            this.app = app;
            this.time = time;
            this.channel = channel;
            this.delete = delete;
        }
    }
}
