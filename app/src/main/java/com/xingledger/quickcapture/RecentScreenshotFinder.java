package com.xingledger.quickcapture;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.ContentResolver;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

public final class RecentScreenshotFinder {
    private RecentScreenshotFinder() {}

    @Nullable
    public static Uri find(Context context) {
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection;
        String selection;
        String[] args = {"%Screenshots%", "%Screenshot%", "%截屏%", "%截图%"};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[]{MediaStore.Images.Media._ID};
            selection = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR "
                    + MediaStore.Images.Media.DISPLAY_NAME + " LIKE ? OR "
                    + MediaStore.Images.Media.DISPLAY_NAME + " LIKE ? OR "
                    + MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?";
        } else {
            projection = new String[]{MediaStore.Images.Media._ID};
            selection = MediaStore.Images.Media.DATA + " LIKE ? OR "
                    + MediaStore.Images.Media.DISPLAY_NAME + " LIKE ? OR "
                    + MediaStore.Images.Media.DISPLAY_NAME + " LIKE ? OR "
                    + MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?";
        }
        Bundle queryArgs = new Bundle();
        queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args);
        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS,
                new String[]{MediaStore.Images.Media.DATE_ADDED});
        queryArgs.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, 1);
        try (Cursor cursor = context.getContentResolver().query(
                collection, projection, queryArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return ContentUris.withAppendedId(collection, cursor.getLong(0));
            }
        } catch (RuntimeException ignored) {
            // Fall through for vendor MediaStore providers without structured query support.
        }
        try (Cursor cursor = context.getContentResolver().query(collection, projection,
                selection, args, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                return ContentUris.withAppendedId(collection, cursor.getLong(0));
            }
        } catch (RuntimeException ignored) {
            // Permission changes can invalidate a query while the Activity is opening.
        }
        return null;
    }
}
