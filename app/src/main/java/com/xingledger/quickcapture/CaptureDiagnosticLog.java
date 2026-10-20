package com.xingledger.quickcapture;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;

/** Small, app-private stage log. Never pass OCR text, transaction fields or image paths here. */
public final class CaptureDiagnosticLog {
    private static final Object LOCK = new Object();
    private static final int MAX_BYTES = 64 * 1024;
    private static final String FILE_NAME = "capture-events.log";

    private CaptureDiagnosticLog() {}

    public static void record(Context context, String event, String sourcePackage) {
        record(context, event, sourcePackage, -1);
    }

    public static void record(Context context, String event, String sourcePackage, int code) {
        String line = Instant.now() + " event=" + safe(event)
                + " source=" + safe(sourcePackage)
                + (code < 0 ? "" : " code=" + code) + "\n";
        synchronized (LOCK) {
            try {
                File directory = new File(context.getNoBackupFilesDir(), "diagnostics");
                if (!directory.isDirectory() && !directory.mkdirs()) return;
                File file = new File(directory, FILE_NAME);
                try (FileOutputStream output = new FileOutputStream(file, true)) {
                    output.write(line.getBytes(StandardCharsets.UTF_8));
                }
                if (file.length() > MAX_BYTES) {
                    byte[] content = Files.readAllBytes(file.toPath());
                    int start = Math.max(0, content.length - MAX_BYTES / 2);
                    if (start > 0) {
                        while (start < content.length && content[start - 1] != '\n') start++;
                    }
                    try (FileOutputStream output = new FileOutputStream(file, false)) {
                        output.write(Arrays.copyOfRange(content, start, content.length));
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // Diagnostics must never interrupt a screenshot.
            }
        }
    }

    public static void share(Activity activity) throws IOException {
        File exportDirectory = new File(activity.getCacheDir(), "diagnostics");
        if (!exportDirectory.isDirectory() && !exportDirectory.mkdirs()) {
            throw new IOException("无法创建临时导出目录");
        }
        File export = new File(exportDirectory, "xiaoxing-diagnostics.txt");
        synchronized (LOCK) {
            File log = new File(new File(activity.getNoBackupFilesDir(), "diagnostics"), FILE_NAME);
            String header = "小星记账助手诊断日志\n"
                    + "Android API: " + Build.VERSION.SDK_INT + "\n"
                    + "仅包含时间、流程阶段、来源 App 包名和系统错误码。"
                    + "不包含截图、OCR 文字、金额、商户或订单内容。\n\n";
            try (FileOutputStream output = new FileOutputStream(export, false)) {
                output.write(header.getBytes(StandardCharsets.UTF_8));
                if (log.isFile()) {
                    output.write(Files.readAllBytes(log.toPath()));
                } else {
                    output.write("暂无记录。\n".getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".diagnostics", export);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri("诊断日志", uri));
        activity.startActivity(Intent.createChooser(send, "导出诊断日志"));
    }

    private static String safe(String input) {
        if (input == null || input.isEmpty()) return "unknown";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length() && result.length() < 160; i++) {
            char c = input.charAt(i);
            result.append(Character.isLetterOrDigit(c) && c < 128 || c == '.' || c == '_' || c == '-'
                    ? c : '_');
        }
        return result.toString();
    }
}
