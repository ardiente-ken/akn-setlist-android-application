package com.aknevs.set.core.constants;

public final class AppConstants {

    private AppConstants() {}

    // Local database
    public static final String DB_NAME = "akn_setlist.db";
    public static final int DB_VERSION = 1;
    public static final String TABLE_SONGS = "songs";

    // Local sync metadata
    public static final String PREFS_SYNC = "akn_sync_prefs";
    public static final String KEY_LAST_SYNC_MILLIS = "last_sync_millis";

    // Supabase REST
    public static final String SONGS_PATH = "/rest/v1/songs";
    public static final String SONG_SELECT_FIELDS =
            "id,title,artist,is_active,created_at,lyrics,chords,updated_at";
    public static final int SYNC_PAGE_SIZE = 1000;
}
