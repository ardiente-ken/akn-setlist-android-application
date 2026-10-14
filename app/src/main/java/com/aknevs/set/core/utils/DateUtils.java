package com.aknevs.set.core.utils;

import android.content.Context;

import com.aknevs.set.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DateUtils {

    private static final Pattern OFFSET = Pattern.compile("([+-])(\\d{2}):?(\\d{2})$");

    private DateUtils() {}

    /** "Never synced", "Synced just now", "Synced 5 min. ago", ... */
    public static String formatLastSync(Context context, long syncedAtMillis) {
        if (syncedAtMillis <= 0) {
            return context.getString(R.string.last_sync_never);
        }
        return context.getString(R.string.last_sync_format, relative(context, syncedAtMillis));
    }

    /** "just now", "5 min. ago" for an ISO-8601 timestamp such as Supabase returns. */
    public static String relativeTime(Context context, String isoTimestamp) {
        long millis = parseIsoMillis(isoTimestamp);
        return millis < 0 ? "" : relative(context, millis);
    }

    /** Parses "2026-10-13T06:12:33.123456+00:00" (or Z). Returns -1 if unreadable. */
    public static long parseIsoMillis(String iso) {
        if (iso == null || iso.length() < 19) return -1;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = format.parse(iso.substring(0, 19));
            if (date == null) return -1;
            long millis = date.getTime();
            Matcher m = OFFSET.matcher(iso.substring(19));
            if (m.find()) { // no offset or "Z" means UTC
                int sign = "-".equals(m.group(1)) ? -1 : 1;
                long offset = (Integer.parseInt(m.group(2)) * 60L + Integer.parseInt(m.group(3))) * 60_000L;
                millis -= sign * offset;
            }
            return millis;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String relative(Context context, long millis) {
        long now = System.currentTimeMillis();
        if (now - millis < android.text.format.DateUtils.MINUTE_IN_MILLIS) {
            return context.getString(R.string.sync_just_now);
        }
        return android.text.format.DateUtils.getRelativeTimeSpanString(
                millis, now,
                android.text.format.DateUtils.MINUTE_IN_MILLIS,
                android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE).toString();
    }
}
