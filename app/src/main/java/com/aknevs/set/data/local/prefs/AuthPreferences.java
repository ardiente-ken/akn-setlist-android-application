package com.aknevs.set.data.local.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.aknevs.set.core.constants.AppConstants;
import com.aknevs.set.data.remote.supabase.AuthSession;

/**
 * The admin session, in app-private storage (excluded from backups). The password is
 * never stored; only the tokens Supabase issued.
 */
public class AuthPreferences {

    private final SharedPreferences prefs;

    public AuthPreferences(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(AppConstants.PREFS_AUTH, Context.MODE_PRIVATE);
    }

    public AuthSession loadSession() {
        String access = prefs.getString("access_token", null);
        String refresh = prefs.getString("refresh_token", null);
        if (access == null || refresh == null) return null;
        return new AuthSession(access, refresh, prefs.getLong("expires_at", 0L),
                prefs.getString("email", ""), prefs.getString("user_id", ""));
    }

    public void saveSession(AuthSession s) {
        prefs.edit()
                .putString("access_token", s.accessToken)
                .putString("refresh_token", s.refreshToken)
                .putLong("expires_at", s.expiresAtMillis)
                .putString("email", s.email)
                .putString("user_id", s.userId)
                .apply();
    }

    public boolean isAdmin() { return prefs.getBoolean("is_admin", false); }

    public void setAdmin(boolean admin) { prefs.edit().putBoolean("is_admin", admin).apply(); }

    public void clear() { prefs.edit().clear().apply(); }
}
