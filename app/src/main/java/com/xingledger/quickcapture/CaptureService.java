package com.xingledger.quickcapture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ResultReceiver;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CaptureService extends Service {
    private static final int NOTIFICATION_ID = 4201;
    private static final String CHANNEL_ID = "screen_capture";
    private static volatile boolean ready;

    private HandlerThread workerThread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Image latestImage;
    private TextRecognizer recognizer;
    private int captureWidth;
    private int captureHeight;
    private int density;
    private final AtomicBoolean captureInFlight = new AtomicBoolean(false);

    public static boolean isReady() {
        return ready;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        workerThread = new HandlerThread("xing-capture-worker");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (CaptureContract.ACTION_STOP.equals(action)) {
            stopCaptureService();
            return START_NOT_STICKY;
        }

        if (CaptureContract.ACTION_START.equals(action) || CaptureContract.ACTION_START_AND_CAPTURE.equals(action)) {
            startAsForeground();
            if (!ready) startProjection(intent);
        }

        if (CaptureContract.ACTION_CAPTURE.equals(action) || CaptureContract.ACTION_START_AND_CAPTURE.equals(action)) {
            ResultReceiver receiver = receiverFrom(intent);
            String sourceApp = intent.getStringExtra(CaptureContract.EXTRA_SOURCE_APP);
            String sourcePackage = intent.getStringExtra(CaptureContract.EXTRA_SOURCE_PACKAGE);
            if (!ready) {
                sendError(receiver, "屏幕捕获授权已失效，请重新授权");
            } else {
                scheduleCapture(receiver, sourceApp, sourcePackage);
            }
        }
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type);
    }

    private void startProjection(Intent source) {
        int resultCode = source.getIntExtra(CaptureContract.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent resultData = intentExtra(source, CaptureContract.EXTRA_RESULT_DATA);
        if (resultCode != Activity.RESULT_OK || resultData == null) return;

        captureWidth = Math.max(1, source.getIntExtra(CaptureContract.EXTRA_WIDTH, 1080));
        captureHeight = Math.max(1, source.getIntExtra(CaptureContract.EXTRA_HEIGHT, 1920));
        density = Math.max(1, source.getIntExtra(CaptureContract.EXTRA_DENSITY, getResources().getDisplayMetrics().densityDpi));

        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    worker.post(CaptureService.this::stopCaptureService);
                }

                @Override
                public void onCapturedContentResize(int width, int height) {
                    if (width > 0 && height > 0) worker.post(() -> resizeCapture(width, height));
                }
            }, worker);
            imageReader = newReader(captureWidth, captureHeight);
            virtualDisplay = projection.createVirtualDisplay(
                    "XingQuickLedger",
                    captureWidth,
                    captureHeight,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    worker);
            ready = true;
            ScreenshotTileService.requestListeningState(this, new android.content.ComponentName(this, ScreenshotTileService.class));
        } catch (RuntimeException error) {
            ready = false;
            stopSelf();
        }
    }

    private void resizeCapture(int width, int height) {
        if (!ready || virtualDisplay == null || width == captureWidth && height == captureHeight) return;
        ImageReader oldReader = imageReader;
        ImageReader replacement = newReader(width, height);
        virtualDisplay.setSurface(replacement.getSurface());
        virtualDisplay.resize(width, height, density);
        imageReader = replacement;
        captureWidth = width;
        captureHeight = height;
        closeLatestImage();
        if (oldReader != null) oldReader.close();
    }

    private ImageReader newReader(int width, int height) {
        ImageReader reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        reader.setOnImageAvailableListener(this::cacheLatestImage, worker);
        return reader;
    }

    private void scheduleCapture(ResultReceiver receiver, String sourceApp, String sourcePackage) {
        if (!captureInFlight.compareAndSet(false, true)) {
            sendError(receiver, "正在识别上一张截图，请稍候");
            return;
        }
        // Keep caching frames during the collapse animation, then freeze and use
        // the newest one. Holding one acquired image also makes static screens reliable.
        worker.postDelayed(() -> acquireImage(receiver, sourceApp, sourcePackage, 0), 380);
    }

    private void acquireImage(ResultReceiver receiver, String sourceApp, String sourcePackage, int attempt) {
        ImageReader reader = imageReader;
        Image image = null;
        try {
            if (reader != null) {
                reader.setOnImageAvailableListener(null, null);
                image = reader.acquireLatestImage();
            }
            if (image != null) {
                closeLatestImage();
            } else {
                image = latestImage;
                latestImage = null;
            }
            if (image == null) {
                if (attempt < 8) {
                    worker.postDelayed(() -> acquireImage(receiver, sourceApp, sourcePackage, attempt + 1), 90);
                } else {
                    captureInFlight.set(false);
                    restoreCacheListener();
                    sendError(receiver, "暂时没有取得屏幕画面，请重试");
                }
                return;
            }
            Bitmap bitmap = imageToBitmap(image);
            restoreCacheListener();
            long capturedAt = System.currentTimeMillis();
            String screenshotPath = saveScreenshot(bitmap, capturedAt);
            recognize(bitmap, screenshotPath, capturedAt, sourceApp, sourcePackage, receiver);
        } catch (Exception error) {
            captureInFlight.set(false);
            restoreCacheListener();
            sendError(receiver, "截图处理失败：" + safeMessage(error));
        } finally {
            if (image != null) image.close();
        }
    }

    private void cacheLatestImage(ImageReader reader) {
        Image newest = null;
        try {
            newest = reader.acquireLatestImage();
            if (newest != null) {
                closeLatestImage();
                latestImage = newest;
                newest = null;
            }
        } catch (IllegalStateException ignored) {
            // The reader may have been replaced during rotation.
        } finally {
            if (newest != null) newest.close();
        }
    }

    private void restoreCacheListener() {
        ImageReader reader = imageReader;
        if (reader != null) reader.setOnImageAvailableListener(this::cacheLatestImage, worker);
    }

    private void closeLatestImage() {
        if (latestImage != null) {
            latestImage.close();
            latestImage = null;
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowPadding = rowStride - pixelStride * width;
        int paddedWidth = width + rowPadding / pixelStride;
        Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        if (paddedWidth == width) return padded;
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
        padded.recycle();
        return cropped;
    }

    private String saveScreenshot(Bitmap bitmap, long capturedAt) throws IOException {
        File file = ScreenshotRepository.newImageFile(this, capturedAt);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                throw new IOException("无法编码截图");
            }
        }
        return file.getAbsolutePath();
    }

    private void recognize(Bitmap bitmap, String screenshotPath, long capturedAt,
                           String sourceApp, String sourcePackage, ResultReceiver receiver) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(result -> {
                    try {
                        List<OcrLine> lines = new ArrayList<>();
                        for (Text.TextBlock block : result.getTextBlocks()) {
                            for (Text.Line line : block.getLines()) {
                                Rect box = line.getBoundingBox();
                                lines.add(new OcrLine(line.getText(), box == null ? 0 : box.top, box == null ? 0 : box.left));
                            }
                        }
                        TransactionDraft draft = TransactionParser.parse(lines);
                        TransactionEnricher.addSource(draft, sourceApp);
                        try {
                            ScreenshotRepository.saveMetadata(
                                    this, screenshotPath, capturedAt,
                                    sourceApp, sourcePackage, draft.channel);
                        } catch (IOException ignored) {
                            // The image remains usable and will appear with fallback metadata.
                        }
                        Bundle data = new Bundle();
                        data.putSerializable(CaptureContract.EXTRA_DRAFT, draft);
                        data.putString(CaptureContract.EXTRA_SCREENSHOT, screenshotPath);
                        if (receiver != null) receiver.send(CaptureContract.RESULT_CAPTURED, data);
                    } finally {
                        bitmap.recycle();
                        captureInFlight.set(false);
                    }
                })
                .addOnFailureListener(error -> {
                    bitmap.recycle();
                    captureInFlight.set(false);
                    deleteFileQuietly(screenshotPath);
                    sendError(receiver, "OCR 识别失败：" + safeMessage(error));
                });
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 41, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, CaptureService.class).setAction(CaptureContract.ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 42, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("截图记账已就绪")
                .setContentText("点“截图记账”磁贴即可识别当前画面")
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, "停止", stop)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.projection_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.projection_channel_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void sendError(@Nullable ResultReceiver receiver, String message) {
        if (receiver == null) return;
        Bundle data = new Bundle();
        data.putString(CaptureContract.EXTRA_ERROR, message);
        receiver.send(CaptureContract.RESULT_ERROR, data);
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static ResultReceiver receiverFrom(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(CaptureContract.EXTRA_RECEIVER, ResultReceiver.class);
        }
        return intent.getParcelableExtra(CaptureContract.EXTRA_RECEIVER);
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static Intent intentExtra(Intent source, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return source.getParcelableExtra(key, Intent.class);
        }
        return source.getParcelableExtra(key);
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static void deleteFileQuietly(@Nullable String path) {
        if (path != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        }
    }

    private void stopCaptureService() {
        ready = false;
        captureInFlight.set(false);
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        closeLatestImage();
        MediaProjection currentProjection = projection;
        projection = null;
        if (currentProjection != null) currentProjection.stop();
        ScreenshotTileService.requestListeningState(this, new android.content.ComponentName(this, ScreenshotTileService.class));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        ready = false;
        if (recognizer != null) recognizer.close();
        if (workerThread != null) workerThread.quitSafely();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
