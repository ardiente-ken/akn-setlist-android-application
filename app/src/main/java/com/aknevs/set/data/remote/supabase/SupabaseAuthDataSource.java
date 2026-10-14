package com.aknevs.set.data.remote.supabase;

import com.aknevs.set.core.constants.AppConstants;
import com.aknevs.set.domain.model.AdminException;
import com.aknevs.set.domain.model.SyncException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;

/** Supabase Auth (email + password) and the admin check. Blocking calls. */
public class SupabaseAuthDataSource {

    private final SupabaseClient client;

    public SupabaseAuthDataSource(SupabaseClient client) {
        this.client = client;
    }

    public AuthSession signIn(String email, String password) throws AdminException {
        try {
            String body = new JSONObject().put("email", email).put("password", password).toString();
            SupabaseClient.Result r = client.send("POST", AppConstants.AUTH_TOKEN_PATH,
                    Collections.singletonMap("grant_type", "password"), body, null, null);
            if (r.code == 400 || r.code == 401 || r.code == 422) {
                throw new AdminException(AdminException.Reason.INVALID_CREDENTIALS, "HTTP " + r.code);
            }
            return parseSession(r);
        } catch (SyncException e) {
            throw map(e);
        } catch (JSONException e) {
            throw new AdminException(AdminException.Reason.SERVER, "Bad sign-in request", e);
        }
    }

    /** Throws NOT_SIGNED_IN if the refresh token is no longer accepted. */
    public AuthSession refresh(String refreshToken) throws AdminException {
        try {
            String body = new JSONObject().put("refresh_token", refreshToken).toString();
            SupabaseClient.Result r = client.send("POST", AppConstants.AUTH_TOKEN_PATH,
                    Collections.singletonMap("grant_type", "refresh_token"), body, null, null);
            if (r.code == 400 || r.code == 401) {
                throw new AdminException(AdminException.Reason.NOT_SIGNED_IN, "Session expired");
            }
            return parseSession(r);
        } catch (SyncException e) {
            throw map(e);
        } catch (JSONException e) {
            throw new AdminException(AdminException.Reason.SERVER, "Bad refresh request", e);
        }
    }

    /** True only if public.admins lists the signed-in user (RLS shows each user only their own row). */
    public boolean isAdmin(String accessToken) throws AdminException {
        try {
            SupabaseClient.Result r = client.send("GET", AppConstants.ADMINS_PATH,
                    Collections.singletonMap("select", "user_id"), null, accessToken, null);
            if (r.code == 401) {
                throw new AdminException(AdminException.Reason.NOT_SIGNED_IN, "Token rejected");
            }
            if (!r.isSuccessful()) {
                throw new AdminException(AdminException.Reason.SERVER, "HTTP " + r.code);
            }
            return new JSONArray(r.body).length() > 0;
        } catch (SyncException e) {
            throw map(e);
        } catch (JSONException e) {
            throw new AdminException(AdminException.Reason.SERVER, "Bad admin response", e);
        }
    }

    /** Best effort: ends the session server-side. Failures are ignored. */
    public void logoutQuietly(String accessToken) {
        try {
            client.send("POST", AppConstants.AUTH_LOGOUT_PATH, null, null, accessToken, null);
        } catch (Exception ignored) {
            // the local session is already gone
        }
    }

    private AuthSession parseSession(SupabaseClient.Result r) throws AdminException {
        if (!r.isSuccessful()) {
            throw new AdminException(AdminException.Reason.SERVER, "HTTP " + r.code);
        }
        try {
            JSONObject o = new JSONObject(r.body);
            JSONObject user = o.optJSONObject("user");
            long expiresIn = o.optLong("expires_in", 3600L);
            return new AuthSession(
                    o.getString("access_token"),
                    o.getString("refresh_token"),
                    System.currentTimeMillis() + expiresIn * 1000L,
                    user == null ? "" : user.optString("email", ""),
                    user == null ? "" : user.optString("id", ""));
        } catch (JSONException e) {
            throw new AdminException(AdminException.Reason.SERVER, "Bad auth response", e);
        }
    }

    static AdminException map(SyncException e) {
        switch (e.getReason()) {
            case NETWORK:        return new AdminException(AdminException.Reason.NETWORK, e.getMessage(), e);
            case NOT_CONFIGURED: return new AdminException(AdminException.Reason.NOT_CONFIGURED, e.getMessage(), e);
            default:             return new AdminException(AdminException.Reason.SERVER, e.getMessage(), e);
        }
    }
}
