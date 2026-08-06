package com.xingledger.quickcapture;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;

public final class ForegroundAppDetector {
    private ForegroundAppDetector() {}

    public static boolean hasPermission(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode = ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static SourceApp detect(Context context) {
        if (!hasPermission(context)) return SourceApp.UNKNOWN;
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return SourceApp.UNKNOWN;

        long end = System.currentTimeMillis();
        UsageEvents events = manager.queryEvents(end - 30_000L, end);
        UsageEvents.Event event = new UsageEvents.Event();
        String candidate = "";
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            boolean foreground = type == UsageEvents.Event.MOVE_TO_FOREGROUND
                    || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.ACTIVITY_RESUMED;
            if (!foreground) continue;
            String packageName = event.getPackageName();
            if (packageName == null || packageName.equals(context.getPackageName())
                    || packageName.equals("com.android.systemui")) continue;
            candidate = packageName;
        }
        if (candidate.isEmpty()) return SourceApp.UNKNOWN;
        return new SourceApp(resolveLabel(context, candidate), candidate);
    }

    private static String resolveLabel(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        try {
            ApplicationInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = manager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0));
            } else {
                //noinspection deprecation
                info = manager.getApplicationInfo(packageName, 0);
            }
            return manager.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return packageName;
        }
    }
}
