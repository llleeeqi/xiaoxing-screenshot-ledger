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

import java.nio.ByteBuffer;
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
    private int captureWidth;
    private int captureHeight;
    private int density;
    private final AtomicBoolean captureInFlight = new AtomicBoolean(false);
    private final AtomicBoolean outcomeSent = new AtomicBoolean(false);
    private ResultReceiver activeReceiver;
    private String activeSourcePackage = "";
    private boolean stopping;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        workerThread = new HandlerThread("xing-capture-worker");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            worker.post(this::stopCaptureService);
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (CaptureContract.ACTION_STOP.equals(action)) {
            worker.post(this::stopCaptureService);
            return START_NOT_STICKY;
        }
        ResultReceiver receiver = receiverFrom(intent);
        if (!CaptureContract.ACTION_START_AND_CAPTURE.equals(action)) {
            sendError(receiver, "请重新点击磁贴并授权本次截图");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ready || captureInFlight.get() || stopping) {
            sendError(receiver, "正在识别上一张截图，请稍候");
            return START_NOT_STICKY;
        }
        activeReceiver = receiver;
        activeSourcePackage = intent.getStringExtra(CaptureContract.EXTRA_SOURCE_PACKAGE);
        outcomeSent.set(false);
        try {
            startAsForeground();
            if (!startProjection(intent)) {
                failSession("系统截图授权已失效，请重新点击磁贴授权");
                return START_NOT_STICKY;
            }
            scheduleCapture(receiver, intent.getStringExtra(CaptureContract.EXTRA_SOURCE_APP),
                    activeSourcePackage);
            worker.postDelayed(() -> {
                if (!stopping && !outcomeSent.get()) failSession("截图等待超时，请重试");
            }, 12_000);
        } catch (RuntimeException error) {
            CaptureDiagnosticLog.record(this, "projection_start_failed", activeSourcePackage);
            failSession("无法启动系统截图，请重试或改用最近截图模式");
        }
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type);
    }

    private boolean startProjection(Intent source) {
        int resultCode = source.getIntExtra(CaptureContract.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent resultData = intentExtra(source, CaptureContract.EXTRA_RESULT_DATA);
        if (resultCode != Activity.RESULT_OK || resultData == null) return false;

        captureWidth = Math.max(1, source.getIntExtra(CaptureContract.EXTRA_WIDTH, 1080));
        captureHeight = Math.max(1, source.getIntExtra(CaptureContract.EXTRA_HEIGHT, 1920));
        density = Math.max(1, source.getIntExtra(CaptureContract.EXTRA_DENSITY, getResources().getDisplayMetrics().densityDpi));

        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            if (projection == null) return false;
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    worker.post(() -> {
                        if (!stopping) failSession("系统已结束屏幕共享，请重试");
                    });
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
            CaptureDiagnosticLog.record(this, "projection_started", activeSourcePackage);
            ScreenshotTileService.requestListeningState(this, new android.content.ComponentName(this, ScreenshotTileService.class));
            return true;
        } catch (RuntimeException error) {
            ready = false;
            CaptureDiagnosticLog.record(this, "projection_create_failed", activeSourcePackage);
            return false;
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
        Bitmap bitmap = null;
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
                    failSession("暂时没有取得屏幕画面，请重试");
                }
                return;
            }
            bitmap = imageToBitmap(image);
            image.close();
            image = null;
            CaptureDiagnosticLog.record(this, "projection_frame_captured", sourcePackage);
            if (receiver != null) receiver.send(CaptureContract.RESULT_PROCESSING, Bundle.EMPTY);
            // Release the projection and foreground notification before OCR starts.
            stopCaptureService();
            ImageOcrProcessor.process(getApplicationContext(), bitmap, sourceApp, sourcePackage,
                    new ImageOcrProcessor.Callback() {
                        @Override
                        public void onSuccess(TransactionDraft draft, String privateImagePath) {
                            if (!outcomeSent.compareAndSet(false, true)) return;
                            Bundle data = new Bundle();
                            data.putSerializable(CaptureContract.EXTRA_DRAFT, draft);
                            data.putString(CaptureContract.EXTRA_SCREENSHOT, privateImagePath);
                            if (receiver != null) receiver.send(CaptureContract.RESULT_CAPTURED, data);
                        }

                        @Override
                        public void onError(String message) {
                            if (outcomeSent.compareAndSet(false, true)) sendError(receiver, message);
                        }
                    });
            bitmap = null; // ImageOcrProcessor owns and recycles it.
        } catch (Exception error) {
            captureInFlight.set(false);
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            failSession("截图处理失败：" + safeMessage(error));
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

    private void closeLatestImage() {
        Image image = latestImage;
        latestImage = null;
        if (image != null) {
            try {
                image.close();
            } catch (RuntimeException ignored) {
                // Continue releasing the projection even if a frame is stale.
            }
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

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 41, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, CaptureService.class).setAction(CaptureContract.ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 42, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("正在截取当前画面")
                .setContentText("取得一帧后立即关闭屏幕共享")
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

    private void stopCaptureService() {
        if (stopping) return;
        stopping = true;
        ready = false;
        captureInFlight.set(false);
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (RuntimeException ignored) {
                // A system-terminated projection may have released it already.
            }
            virtualDisplay = null;
        }
        closeLatestImage();
        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (RuntimeException ignored) {
                // Continue removing the foreground notification.
            }
            imageReader = null;
        }
        MediaProjection currentProjection = projection;
        projection = null;
        if (currentProjection != null) {
            try {
                currentProjection.stop();
            } catch (RuntimeException ignored) {
                // The system may have stopped the session first.
            }
        }
        CaptureDiagnosticLog.record(this, "projection_stopped", activeSourcePackage);
        ScreenshotTileService.requestListeningState(this, new android.content.ComponentName(this, ScreenshotTileService.class));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void failSession(String message) {
        if (outcomeSent.compareAndSet(false, true)) sendError(activeReceiver, message);
        worker.post(this::stopCaptureService);
    }

    @Override
    public void onDestroy() {
        if (!stopping) stopCaptureService();
        ready = false;
        if (workerThread != null) workerThread.quitSafely();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
