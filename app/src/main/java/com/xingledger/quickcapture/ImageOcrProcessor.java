package com.xingledger.quickcapture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageOcrProcessor {
    public interface Callback {
        void onSuccess(TransactionDraft draft, String privateImagePath);
        void onError(String message);
    }

    private ImageOcrProcessor() {}

    public static void process(Context context, Uri source, String sourceLabel, Callback callback) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        ExecutorService io = Executors.newSingleThreadExecutor();
        io.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = decode(app, source);
                if (bitmap == null) throw new IOException("无法读取这张图片");
                long capturedAt = System.currentTimeMillis();
                File privateFile = ScreenshotRepository.newImageFile(app, capturedAt);
                try (FileOutputStream output = new FileOutputStream(privateFile, false)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                        throw new IOException("无法保存图片副本");
                    }
                }
                Bitmap ready = bitmap;
                main.post(() -> recognize(app, ready, privateFile, capturedAt, sourceLabel, callback));
            } catch (Exception error) {
                if (bitmap != null) bitmap.recycle();
                String message = safeMessage(error);
                main.post(() -> callback.onError(message));
            } finally {
                io.shutdown();
            }
        });
    }

    private static void recognize(Context context, Bitmap bitmap, File privateFile, long capturedAt,
                                  String sourceLabel, Callback callback) {
        TextRecognizer recognizer = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build());
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(result -> {
                    try {
                        List<OcrLine> lines = new ArrayList<>();
                        for (Text.TextBlock block : result.getTextBlocks()) {
                            for (Text.Line line : block.getLines()) {
                                Rect box = line.getBoundingBox();
                                lines.add(new OcrLine(line.getText(),
                                        box == null ? 0 : box.top, box == null ? 0 : box.left));
                            }
                        }
                        TransactionDraft draft = TransactionParser.parse(lines);
                        TransactionEnricher.addSource(draft, sourceLabel);
                        if (!draft.hasBillKeywords()) {
                            delete(privateFile);
                            callback.onSuccess(draft, "");
                            return;
                        }
                        ScreenshotRepository.saveMetadata(context, privateFile.getAbsolutePath(), capturedAt,
                                sourceLabel, "", draft.channel, draft.rawText);
                        callback.onSuccess(draft, privateFile.getAbsolutePath());
                    } catch (IOException error) {
                        delete(privateFile);
                        callback.onError(safeMessage(error));
                    } finally {
                        bitmap.recycle();
                        recognizer.close();
                    }
                })
                .addOnFailureListener(error -> {
                    bitmap.recycle();
                    recognizer.close();
                    delete(privateFile);
                    callback.onError("OCR 识别失败：" + safeMessage(error));
                });
    }

    private static Bitmap decode(Context context, Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int width = info.getSize().getWidth();
                int height = info.getSize().getHeight();
                int max = 4096;
                if (width > max || height > max) {
                    float scale = Math.min(max / (float) width, max / (float) height);
                    decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                            Math.max(1, Math.round(height * scale)));
                }
            });
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("无法打开图片");
            return BitmapFactory.decodeStream(input);
        }
    }

    private static void delete(File file) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
