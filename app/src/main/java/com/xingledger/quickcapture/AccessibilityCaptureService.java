package com.xingledger.quickcapture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.RequiresApi;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Performs one-shot screenshots without MediaProjection. Android exposes this
 * API only to a user-enabled accessibility service, so there is no persistent
 * screen-sharing session and no image is written to the system gallery.
 */
@RequiresApi(Build.VERSION_CODES.R)
public final class AccessibilityCaptureService extends AccessibilityService {
    private static volatile AccessibilityCaptureService instance;
    private static volatile SourceApp lastSource = SourceApp.UNKNOWN;

    private final AtomicBoolean captureInFlight = new AtomicBoolean(false);
    private ExecutorService captureExecutor;

    public static boolean isConnected() {
        return instance != null;
    }

    public static boolean isEnabled(Context context) {
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        ComponentName expected = new ComponentName(context, AccessibilityCaptureService.class);
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo service : services) {
            ComponentName actual = ComponentName.unflattenFromString(service.getId());
            if (expected.equals(actual)) return true;
        }
        return false;
    }

    public static SourceApp lastSource(Context context) {
        SourceApp source = lastSource;
        if (source == null || source.packageName.isEmpty()) return SourceApp.UNKNOWN;
        return source;
    }

    public static boolean requestCapture(ResultReceiver receiver, SourceApp requestedSource) {
        AccessibilityCaptureService service = instance;
        if (service == null) return false;
        service.capture(receiver, requestedSource);
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        captureExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "xing-accessibility-capture");
            thread.setDaemon(true);
            return thread;
        });
        instance = this;
        ScreenshotTileService.requestListeningState(
                this, new ComponentName(this, ScreenshotTileService.class));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String packageName = event.getPackageName().toString();
        if (packageName.isEmpty() || packageName.equals(getPackageName())
                || packageName.equals("com.android.systemui")) return;
        lastSource = new SourceApp(resolveLabel(packageName), packageName);
    }

    @Override
    public void onInterrupt() {
        // No continuous operation to interrupt.
    }

    private void capture(ResultReceiver receiver, SourceApp requestedSource) {
        if (!captureInFlight.compareAndSet(false, true)) {
            sendError(receiver, "正在识别上一张截图，请稍候");
            return;
        }
        SourceApp source = requestedSource == null || requestedSource.packageName.isEmpty()
                ? lastSource(this) : requestedSource;
        TakeScreenshotCallback callback = new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                Bitmap bitmap;
                try {
                    bitmap = copyToSoftwareBitmap(screenshot);
                } catch (RuntimeException error) {
                    captureInFlight.set(false);
                    sendError(receiver, "读取截图失败：" + safeMessage(error));
                    return;
                }
                if (bitmap == null) {
                    captureInFlight.set(false);
                    sendError(receiver, "无法读取系统返回的截图");
                    return;
                }
                ImageOcrProcessor.process(
                        AccessibilityCaptureService.this,
                        bitmap,
                        source.label,
                        source.packageName,
                        new ImageOcrProcessor.Callback() {
                            @Override
                            public void onSuccess(TransactionDraft draft, String privateImagePath) {
                                captureInFlight.set(false);
                                Bundle result = new Bundle();
                                result.putSerializable(CaptureContract.EXTRA_DRAFT, draft);
                                result.putString(CaptureContract.EXTRA_SCREENSHOT, privateImagePath);
                                if (receiver != null) {
                                    receiver.send(CaptureContract.RESULT_CAPTURED, result);
                                }
                            }

                            @Override
                            public void onError(String message) {
                                captureInFlight.set(false);
                                sendError(receiver, message);
                            }
                        });
            }

            @Override
            public void onFailure(int errorCode) {
                captureInFlight.set(false);
                sendError(receiver, screenshotError(errorCode));
            }
        };
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, captureExecutor, callback);
        } catch (RuntimeException error) {
            captureInFlight.set(false);
            sendError(receiver, "启动截图失败：" + safeMessage(error));
        }
    }

    private Bitmap copyToSoftwareBitmap(ScreenshotResult screenshot) {
        HardwareBuffer buffer = screenshot.getHardwareBuffer();
        try {
            ColorSpace colorSpace = screenshot.getColorSpace();
            if (colorSpace == null) colorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
            Bitmap wrapped = Bitmap.wrapHardwareBuffer(buffer, colorSpace);
            if (wrapped == null) return null;
            try {
                return wrapped.copy(Bitmap.Config.ARGB_8888, false);
            } finally {
                wrapped.recycle();
            }
        } finally {
            buffer.close();
        }
    }

    private String screenshotError(int errorCode) {
        if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
            return "截图间隔太短，请稍后重试";
        }
        if (errorCode == ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS) {
            return "无障碍截图权限已失效，请重新开启";
        }
        if (errorCode == ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY) {
            return "当前屏幕暂时无法截图";
        }
        if (errorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
            return "当前页面禁止截图";
        }
        return "系统截图失败（" + errorCode + "）";
    }

    private String resolveLabel(String packageName) {
        PackageManager manager = getPackageManager();
        try {
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            return manager.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return packageName;
        }
    }

    private static void sendError(ResultReceiver receiver, String message) {
        if (receiver == null) return;
        Bundle data = new Bundle();
        data.putString(CaptureContract.EXTRA_ERROR, message);
        receiver.send(CaptureContract.RESULT_ERROR, data);
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        if (captureExecutor != null) captureExecutor.shutdownNow();
        ScreenshotTileService.requestListeningState(
                this, new ComponentName(this, ScreenshotTileService.class));
        super.onDestroy();
    }
}
