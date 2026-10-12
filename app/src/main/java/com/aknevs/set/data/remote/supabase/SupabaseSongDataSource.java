package com.aknevs.set.data.remote.supabase;

import com.aknevs.set.core.constants.AppConstants;
import com.aknevs.set.domain.model.Song;
import com.aknevs.set.domain.model.SyncException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SupabaseSongDataSource {

    private final SupabaseClient client;

    public SupabaseSongDataSource(SupabaseClient client) {
        this.client = client;
    }

    /**
     * Downloads every active song, page by page. Returns only if the COMPLETE collection
     * was retrieved and parsed; otherwise throws, so the caller never replaces local data
     * with a partial or failed result. An empty list is a legitimate successful result.
     */
    public List<Song> fetchActiveSongs() throws SyncException {
        Map<String, Song> songs = new LinkedHashMap<>(); // de-dupes if rows shift between pages
        int offset = 0;
        int total = -1;

        while (true) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("select", AppConstants.SONG_SELECT_FIELDS);
            query.put("is_active", "eq.true");
            query.put("order", "title.asc,id.asc");
            query.put("limit", String.valueOf(AppConstants.SYNC_PAGE_SIZE));
            query.put("offset", String.valueOf(offset));

            SupabaseClient.Page page = client.get(AppConstants.SONGS_PATH, query);
            List<Song> rows = parse(page.body);
            for (Song song : rows) {
                songs.put(song.getId(), song);
            }
            if (page.totalCount >= 0) {
                total = page.totalCount;
            }
            offset += rows.size();

            if (rows.isEmpty()) break;
            // Advance by rows actually received: copes with a server-side max-rows cap.
            boolean done = total >= 0
                    ? offset >= total
                    : rows.size() < AppConstants.SYNC_PAGE_SIZE;
            if (done) break;
        }

        if (total >= 0 && offset < total) {
            throw new SyncException(SyncException.Reason.INVALID_DATA,
                    "Received " + offset + " of " + total + " songs");
        }
        return new ArrayList<>(songs.values());
    }

    private static List<Song> parse(String json) throws SyncException {
        try {
            JSONArray array = new JSONArray(json);
            List<Song> result = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                result.add(new Song(
                        required(o, "id"),
                        required(o, "title"),
                        required(o, "artist"),
                        o.optBoolean("is_active", true),
                        required(o, "created_at"),
                        optional(o, "lyrics"),
                        optional(o, "chords"),
                        required(o, "updated_at")));
            }
            return result;
        } catch (JSONException e) {
            throw new SyncException(SyncException.Reason.INVALID_DATA,
                    "Bad JSON: " + e.getMessage(), e);
        }
    }

    private static String required(JSONObject o, String key) throws JSONException {
        if (o.isNull(key)) {
            throw new JSONException("Missing " + key);
        }
        return o.getString(key);
    }

    private static String optional(JSONObject o, String key) throws JSONException {
        return o.isNull(key) ? "" : o.getString(key);
    }
}
