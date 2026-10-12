package com.aknevs.set.core.utils;

import android.content.Context;

import com.aknevs.set.R;

public final class DateUtils {

    private DateUtils() {}

    /** "Never synced", "Synced just now", "Synced 5 min. ago", ... */
    public static String formatLastSync(Context context, long syncedAtMillis) {
        if (syncedAtMillis <= 0) {
            return context.getString(R.string.last_sync_never);
        }
        long now = System.currentTimeMillis();
        String relative;
        if (now - syncedAtMillis < android.text.format.DateUtils.MINUTE_IN_MILLIS) {
            relative = context.getString(R.string.sync_just_now);
        } else {
            relative = android.text.format.DateUtils.getRelativeTimeSpanString(
                    syncedAtMillis, now,
                    android.text.format.DateUtils.MINUTE_IN_MILLIS,
                    android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE).toString();
        }
        return context.getString(R.string.last_sync_format, relative);
    }
}
