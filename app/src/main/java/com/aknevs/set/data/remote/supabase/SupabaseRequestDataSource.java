package com.aknevs.set.data.remote.supabase;

import com.aknevs.set.core.constants.AppConstants;
import com.aknevs.set.domain.model.AdminException;
import com.aknevs.set.domain.model.SongRequest;
import com.aknevs.set.domain.model.SyncException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Loads the pending queue over REST. Realtime handles everything after that. */
public class SupabaseRequestDataSource {

    private final SupabaseClient client;

    public SupabaseRequestDataSource(SupabaseClient client) {
        this.client = client;
    }

    /** Blocking. Throws if the queue could not be loaded completely. */
    public List<SongRequest> fetchPending() throws SyncException {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("select", "*");
        query.put("status", "eq." + AppConstants.REQUESTS_STATUS_PENDING);
        query.put("order", "created_at.asc");
        query.put("limit", "500");

        SupabaseClient.Page page = client.get(AppConstants.REQUESTS_PATH, query);
        try {
            JSONArray array = new JSONArray(page.body);
            List<SongRequest> result = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                result.add(parse(array.getJSONObject(i)));
            }
            return result;
        } catch (JSONException e) {
            throw new SyncException(SyncException.Reason.INVALID_DATA,
                    "Bad request JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Admin only (enforced by RLS): marks a request dismissed so it leaves the pending queue,
     * exactly as the website's admin does. 403 or an empty result means RLS refused it.
     */
    public void dismiss(String requestId, String accessToken) throws AdminException {
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            String body = new JSONObject()
                    .put("status", "dismissed")
                    .put("handled_at", iso.format(new Date()))
                    .toString();
            SupabaseClient.Result r = client.send("PATCH", AppConstants.REQUESTS_PATH,
                    Collections.singletonMap("id", "eq." + requestId), body, accessToken,
                    Collections.singletonMap("Prefer", "return=representation"));
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
                throw new AdminException(AdminException.Reason.NOT_ADMIN, "Nothing was updated");
            }
        } catch (SyncException e) {
            throw SupabaseAuthDataSource.map(e);
        } catch (JSONException e) {
            throw new AdminException(AdminException.Reason.SERVER, "Bad dismiss response", e);
        }
    }

    /** Shared with the realtime client: one JSON row -> SongRequest. */
    public static SongRequest parse(JSONObject o) throws JSONException {
        return new SongRequest(
                text(o, "id", true),
                text(o, "song_id", false),
                text(o, "title", true),
                orEmpty(text(o, "artist", false)),
                orEmpty(text(o, "requester_name", false)),
                text(o, "status", true),
                orEmpty(text(o, "created_at", false)),
                text(o, "handled_at", false));
    }

    private static String text(JSONObject o, String key, boolean required) throws JSONException {
        if (o.isNull(key)) {
            if (required) throw new JSONException("Missing " + key);
            return null;
        }
        return o.getString(key);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
