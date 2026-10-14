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

    // Song requests
    public static final String REQUESTS_PATH = "/rest/v1/song_requests";
    public static final String REQUESTS_STATUS_PENDING = "pending";

    // Shared request setting + admin
    public static final String APP_SETTINGS_PATH = "/rest/v1/app_settings";
    public static final String SETTING_REQUESTS_ENABLED = "requests_enabled";
    public static final String ADMINS_PATH = "/rest/v1/admins";
    public static final String AUTH_TOKEN_PATH = "/auth/v1/token";
    public static final String AUTH_LOGOUT_PATH = "/auth/v1/logout";
    public static final String PREFS_AUTH = "akn_auth_prefs";
}
