package com.aknevs.set.data.local.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.aknevs.set.core.constants.AppConstants;

public class SyncPreferences {

    private final SharedPreferences prefs;

    public SyncPreferences(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(AppConstants.PREFS_SYNC, Context.MODE_PRIVATE);
    }

    public long getLastSyncMillis() {
        return prefs.getLong(AppConstants.KEY_LAST_SYNC_MILLIS, 0L);
    }

    public void setLastSyncMillis(long millis) {
        prefs.edit().putLong(AppConstants.KEY_LAST_SYNC_MILLIS, millis).apply();
    }
}
