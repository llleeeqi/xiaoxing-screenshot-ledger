package com.xingledger.quickcapture;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ScreenshotRepository {
    public static final int MAX_RECORDS = 128;
    private static final String DIRECTORY = "screenshots";
    private static final int MAX_OCR_TEXT_LENGTH = 32 * 1024;

    private ScreenshotRepository() {}

    public static File newImageFile(Context context, long capturedAt) throws IOException {
        File directory = directory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("无法创建截图目录");
        }
        return new File(directory, "capture_" + capturedAt + ".jpg");
    }

    public static synchronized void saveMetadata(Context context, String imagePath, long capturedAt,
                                                 String appLabel, String appPackage, String channel,
                                                 String ocrText) throws IOException {
        String resolvedLabel = firstNonBlank(appLabel, channel, "未知应用");
        JSONObject json = new JSONObject();
        try {
            json.put("capturedAt", capturedAt);
            json.put("appLabel", resolvedLabel);
            json.put("appPackage", value(appPackage));
            json.put("channel", value(channel));
            json.put("ocrText", limited(value(ocrText)));
        } catch (JSONException error) {
            throw new IOException("无法保存截图信息", error);
        }

        AtomicFile atomic = new AtomicFile(metadataFile(new File(imagePath)));
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            output.write(json.toString().getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(output);
        } catch (IOException error) {
            if (output != null) atomic.failWrite(output);
            throw error;
        }
        pruneToLimit(context);
    }

    public static List<ScreenshotRecord> list(Context context) {
        List<ScreenshotRecord> records = new ArrayList<>();
        File[] files = directory(context).listFiles((dir, name) -> name.startsWith("capture_") && name.endsWith(".jpg"));
        if (files == null) return records;
        for (File image : files) records.add(readRecord(image));
        records.sort(Comparator.comparingLong((ScreenshotRecord record) -> record.capturedAt).reversed());
        if (records.size() > MAX_RECORDS) {
            for (int index = MAX_RECORDS; index < records.size(); index++) delete(records.get(index));
            return new ArrayList<>(records.subList(0, MAX_RECORDS));
        }
        return records;
    }

    public static ScreenshotRecord find(Context context, String imagePath) {
        if (imagePath == null) return null;
        File image = new File(imagePath);
        try {
            String expected = directory(context).getCanonicalPath() + File.separator;
            if (!image.getCanonicalPath().startsWith(expected) || !image.isFile()) return null;
        } catch (IOException error) {
            return null;
        }
        return readRecord(image);
    }

    public static boolean delete(ScreenshotRecord record) {
        if (record == null) return false;
        File image = new File(record.imagePath);
        File metadata = metadataFile(image);
        boolean imageDeleted = !image.exists() || image.delete();
        boolean metadataDeleted = !metadata.exists() || metadata.delete();
        return imageDeleted && metadataDeleted;
    }

    private static ScreenshotRecord readRecord(File image) {
        long capturedAt = capturedAtFromName(image);
        String appLabel = "未知应用";
        String appPackage = "";
        String channel = "";
        String ocrText = "";
        File metadata = metadataFile(image);
        if (metadata.isFile()) {
            try (FileInputStream input = new FileInputStream(metadata)) {
                byte[] bytes = new byte[(int) Math.min(metadata.length(), 64 * 1024L)];
                int read = input.read(bytes);
                if (read > 0) {
                    JSONObject json = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
                    capturedAt = json.optLong("capturedAt", capturedAt);
                    appLabel = json.optString("appLabel", appLabel);
                    appPackage = json.optString("appPackage", "");
                    channel = json.optString("channel", "");
                    ocrText = json.optString("ocrText", "");
                }
            } catch (IOException | JSONException ignored) {
                // Keep the image visible even if its optional metadata is damaged.
            }
        }
        return new ScreenshotRecord(image.getAbsolutePath(), capturedAt, appLabel, appPackage,
                channel, ocrText);
    }

    private static long capturedAtFromName(File image) {
        String name = image.getName();
        try {
            return Long.parseLong(name.substring("capture_".length(), name.length() - ".jpg".length()));
        } catch (RuntimeException ignored) {
            return image.lastModified();
        }
    }

    private static File metadataFile(File image) {
        String name = image.getName();
        int dot = name.lastIndexOf('.');
        return new File(image.getParentFile(), (dot < 0 ? name : name.substring(0, dot)) + ".json");
    }

    private static File directory(Context context) {
        return new File(context.getFilesDir(), DIRECTORY);
    }

    private static String firstNonBlank(String... values) {
        for (String item : values) if (item != null && !item.trim().isEmpty()) return item.trim();
        return "";
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String limited(String value) {
        return value.length() <= MAX_OCR_TEXT_LENGTH
                ? value : value.substring(0, MAX_OCR_TEXT_LENGTH);
    }

    private static void pruneToLimit(Context context) {
        list(context);
    }
}
