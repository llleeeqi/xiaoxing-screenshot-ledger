package com.xingledger.quickcapture;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AppBlacklist {
    private static final String PREFERENCES = "capture_blacklist";
    private static final String KEY_PACKAGES = "packages";

    private AppBlacklist() {}

    public static Set<String> packages(Context context) {
        Set<String> stored = preferences(context).getStringSet(KEY_PACKAGES, Collections.emptySet());
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public static boolean isBlocked(Context context, String packageName) {
        return packageName != null && !packageName.isEmpty() && packages(context).contains(packageName);
    }

    public static void setBlocked(Context context, String packageName, boolean blocked) {
        if (packageName == null || packageName.isEmpty()) return;
        Set<String> updated = packages(context);
        if (blocked) updated.add(packageName); else updated.remove(packageName);
        preferences(context).edit().putStringSet(KEY_PACKAGES, updated).apply();
    }

    public static int count(Context context) {
        return packages(context).size();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
