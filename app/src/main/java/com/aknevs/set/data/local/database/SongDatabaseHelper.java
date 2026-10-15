package com.aknevs.set.data.local.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import com.aknevs.set.core.constants.AppConstants;
import com.aknevs.set.domain.model.Song;

import java.util.ArrayList;
import java.util.List;

public class SongDatabaseHelper extends SQLiteOpenHelper {

    private static final String CREATE_TABLE =
            "CREATE TABLE " + AppConstants.TABLE_SONGS + " ("
                    + "id TEXT PRIMARY KEY, "
                    + "title TEXT NOT NULL, "
                    + "artist TEXT NOT NULL, "
                    + "is_active INTEGER NOT NULL, "
                    + "created_at TEXT NOT NULL, "
                    + "lyrics TEXT NOT NULL DEFAULT '', "
                    + "chords TEXT NOT NULL DEFAULT '', "
                    + "updated_at TEXT NOT NULL)";

    private static final String CREATE_TITLE_INDEX =
            "CREATE INDEX idx_songs_title ON " + AppConstants.TABLE_SONGS
                    + " (title COLLATE NOCASE)";

    private static final String INSERT =
            "INSERT OR REPLACE INTO " + AppConstants.TABLE_SONGS
                    + " (id, title, artist, is_active, created_at, lyrics, chords, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    public SongDatabaseHelper(Context context) {
        super(context.getApplicationContext(), AppConstants.DB_NAME, null,
                AppConstants.DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
        db.execSQL(CREATE_TITLE_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // The table is only a cache of the remote data. While there are no migrations yet,
        // rebuild it; the next sync restores the songs. Replace with ALTER TABLE steps
        // once users could lose offline-only data by upgrading.
        db.execSQL("DROP TABLE IF EXISTS " + AppConstants.TABLE_SONGS);
        onCreate(db);
    }

    /**
     * Replaces the whole collection atomically. If anything throws, the transaction is
     * rolled back and the previous songs remain exactly as they were. An empty list is a
     * valid input and produces an empty table.
     */
    public void replaceAllSongs(List<Song> songs) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(AppConstants.TABLE_SONGS, null, null);
            SQLiteStatement insert = db.compileStatement(INSERT);
            try {
                for (Song song : songs) {
                    bindSong(insert, song);
                    insert.executeInsert();
                }
            } finally {
                insert.close();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Adds or updates a single song, leaving the rest of the library untouched. */
    public void upsertSong(Song song) {
        SQLiteStatement insert = getWritableDatabase().compileStatement(INSERT);
        try {
            bindSong(insert, song);
            insert.executeInsert();
        } finally {
            insert.close();
        }
    }

    private static void bindSong(SQLiteStatement insert, Song song) {
        insert.clearBindings();
        insert.bindString(1, song.getId());
        insert.bindString(2, song.getTitle());
        insert.bindString(3, song.getArtist());
        insert.bindLong(4, song.isActive() ? 1L : 0L);
        insert.bindString(5, song.getCreatedAt());
        insert.bindString(6, song.getLyrics());
        insert.bindString(7, song.getChords());
        insert.bindString(8, song.getUpdatedAt());
    }

    /**
     * Songs sorted alphabetically by title, optionally filtered by title/artist.
     * Lyrics and chords are NOT loaded here: a list needs neither, and large text columns
     * across hundreds of rows would waste memory and risk the cursor window limit.
     */
    public List<Song> querySongs(String query) {
        String trimmed = query == null ? "" : query.trim();
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, artist, is_active, created_at, updated_at FROM "
                        + AppConstants.TABLE_SONGS);
        String[] args = null;
        if (!trimmed.isEmpty()) {
            sql.append(" WHERE title LIKE ? ESCAPE '\\' OR artist LIKE ? ESCAPE '\\'");
            String pattern = likePattern(trimmed);
            args = new String[]{pattern, pattern};
        }
        sql.append(" ORDER BY title COLLATE NOCASE ASC, id ASC");

        List<Song> songs = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql.toString(), args)) {
            while (c.moveToNext()) {
                songs.add(new Song(
                        c.getString(0), c.getString(1), c.getString(2),
                        c.getInt(3) == 1, c.getString(4),
                        "", "", c.getString(5)));
            }
        }
        return songs;
    }

    /** One complete song including lyrics and chords, or null if it isn't stored locally. */
    public Song getSongById(String id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, title, artist, is_active, created_at, lyrics, chords, updated_at FROM "
                        + AppConstants.TABLE_SONGS + " WHERE id = ?", new String[]{id})) {
            if (c.moveToFirst()) {
                return new Song(c.getString(0), c.getString(1), c.getString(2),
                        c.getInt(3) == 1, c.getString(4), c.getString(5), c.getString(6),
                        c.getString(7));
            }
        }
        return null;
    }

    /**
     * The local song a request refers to: by id when the request came from the library,
     * otherwise (or if that id is not stored) by title, preferring a matching artist.
     */
    public String findSongId(String songId, String title, String artist) {
        SQLiteDatabase db = getReadableDatabase();
        if (songId != null) {
            try (Cursor c = db.rawQuery("SELECT id FROM " + AppConstants.TABLE_SONGS
                    + " WHERE id = ? LIMIT 1", new String[]{songId})) {
                if (c.moveToFirst()) return c.getString(0);
            }
        }
        String wantedTitle = title == null ? "" : title.trim();
        if (wantedTitle.isEmpty()) return null;
        try (Cursor c = db.rawQuery("SELECT id FROM " + AppConstants.TABLE_SONGS
                        + " WHERE title = ? COLLATE NOCASE"
                        + " ORDER BY (artist = ? COLLATE NOCASE) DESC,"
                        + " (length(lyrics) + length(chords) > 0) DESC, id ASC LIMIT 1",
                new String[]{wantedTitle, artist == null ? "" : artist.trim()})) {
            if (c.moveToFirst()) return c.getString(0);
        }
        return null;
    }

    private static String likePattern(String q) {
        String escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
