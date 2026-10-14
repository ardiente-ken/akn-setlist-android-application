package com.aknevs.set.data.remote.supabase;

import com.aknevs.set.core.constants.AppConstants;
import com.aknevs.set.domain.model.AdminException;
import com.aknevs.set.domain.model.SyncException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads and writes the shared 'requests_enabled' row in public.app_settings. Blocking. */
public class SupabaseSettingsDataSource {

    private final SupabaseClient client;

    public SupabaseSettingsDataSource(SupabaseClient client) {
        this.client = client;
    }

    /** true = open. A missing row means closed. Throws if the value could not be read. */
    public boolean fetchRequestsEnabled() throws SyncException {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("key", "eq." + AppConstants.SETTING_REQUESTS_ENABLED);
        query.put("select", "value");
        SupabaseClient.Page page = client.get(AppConstants.APP_SETTINGS_PATH, query);
        try {
            JSONArray rows = new JSONArray(page.body);
            if (rows.length() == 0) return false;
            Object value = rows.getJSONObject(0).opt("value");
            if (value instanceof Boolean) return (Boolean) value;
            return value instanceof String && Boolean.parseBoolean((String) value);
        } catch (JSONException e) {
            throw new SyncException(SyncException.Reason.INVALID_DATA, "Bad setting JSON", e);
        }
    }

    /**
     * Admin only. Upserts the row with the signed-in user's token. 401 means the token
     * expired (NOT_SIGNED_IN); 403 or an empty result means RLS refused it (NOT_ADMIN).
     */
    public void setRequestsEnabled(boolean open, String accessToken) throws AdminException {
        try {
            String body = new JSONObject()
                    .put("key", AppConstants.SETTING_REQUESTS_ENABLED)
                    .put("value", open)
                    .toString();
            SupabaseClient.Result r = client.send("POST", AppConstants.APP_SETTINGS_PATH,
                    Collections.singletonMap("on_conflict", "key"), body, accessToken,
                    Collections.singletonMap("Prefer", "resolution=merge-duplicates,return=representation"));
            if (r.code == 401) {
                throw new AdminException(AdminException.Reason.NOT_SIGNED_IN, "Token expired");
            }
            if (r.code == 403) {
                throw new AdminException(AdminException.Reason.NOT_ADMIN, "Refused by RLS");
            }
            if (!r.isSuccessful()) {
                throw new AdminException(AdminException.Reason.SERVER, "HTTP " + r.code);
            }
            if (new JSONArray(r.body).length() == 0) {
                throw new AdminException(AdminException.Reason.NOT_ADMIN, "Nothing was written");
            }
        } catch (SyncException e) {
            throw SupabaseAuthDataSource.map(e);
        } catch (JSONException e) {
            throw new AdminException(AdminException.Reason.SERVER, "Bad setting response", e);
        }
    }
}
