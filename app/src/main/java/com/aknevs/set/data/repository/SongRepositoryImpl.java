package com.aknevs.set.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aknevs.set.data.local.database.SongDatabaseHelper;
import com.aknevs.set.data.local.prefs.SyncPreferences;
import com.aknevs.set.data.remote.supabase.SupabaseSongDataSource;
import com.aknevs.set.domain.model.Song;
import com.aknevs.set.domain.model.SyncException;
import com.aknevs.set.domain.model.SyncResult;
import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.repository.SongRepository;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SongRepositoryImpl implements SongRepository {

    private static final String TAG = "SongRepository";

    private final SongDatabaseHelper database;
    private final SupabaseSongDataSource remote;
    private final SyncPreferences prefs;

    // Separate executors so a slow network call never delays local reads/searches.
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    public SongRepositoryImpl(SongDatabaseHelper database, SupabaseSongDataSource remote,
                              SyncPreferences prefs) {
        this.database = database;
        this.remote = remote;
        this.prefs = prefs;
    }

    @Override
    public void getSongs(String query, RepositoryCallback<List<Song>> callback) {
        dbExecutor.execute(() -> {
            try {
                List<Song> songs = database.querySongs(query);
                deliverSuccess(callback, songs);
            } catch (Exception e) {
                Log.w(TAG, "Local query failed", e);
                deliverError(callback, e);
            }
        });
    }

    @Override
    public void getSong(String id, RepositoryCallback<Song> callback) {
        dbExecutor.execute(() -> {
            try {
                Song song = database.getSongById(id);
                if (song == null) {
                    deliverError(callback, new NoSuchElementException("Song not found: " + id));
                } else {
                    deliverSuccess(callback, song);
                }
            } catch (Exception e) {
                Log.w(TAG, "Local song read failed", e);
                deliverError(callback, e);
            }
        });
    }

    @Override
    public void findRequestedSongId(String songId, String title, String artist,
                                    RepositoryCallback<String> callback) {
        dbExecutor.execute(() -> {
            try {
                deliverSuccess(callback, database.findSongId(songId, title, artist));
            } catch (Exception e) {
                Log.w(TAG, "Song lookup failed", e);
                deliverError(callback, e);
            }
        });
    }

    @Override
    public void syncSongs(RepositoryCallback<SyncResult> callback) {
        if (!syncing.compareAndSet(false, true)) {
            deliverError(callback, new SyncException(SyncException.Reason.BUSY, "Sync running"));
            return;
        }
        networkExecutor.execute(() -> {
            final List<Song> remoteSongs;
            try {
                // Nothing local is touched until this returns a complete, valid collection.
                remoteSongs = remote.fetchActiveSongs();
            } catch (Exception e) {
                Log.w(TAG, "Remote fetch failed; local songs untouched", e);
                syncing.set(false);
                deliverError(callback, e);
                return;
            }
            dbExecutor.execute(() -> {
                try {
                    database.replaceAllSongs(remoteSongs);
                    long now = System.currentTimeMillis();
                    prefs.setLastSyncMillis(now);
                    syncing.set(false);
                    deliverSuccess(callback, new SyncResult(remoteSongs.size(), now));
                } catch (Exception e) {
                    Log.w(TAG, "Local write failed; transaction rolled back", e);
                    syncing.set(false);
                    deliverError(callback, new SyncException(
                            SyncException.Reason.STORAGE, String.valueOf(e.getMessage()), e));
                }
            });
        });
    }

    @Override
    public long getLastSyncMillis() {
        return prefs.getLastSyncMillis();
    }

    private <T> void deliverSuccess(RepositoryCallback<T> cb, T result) {
        mainHandler.post(() -> cb.onSuccess(result));
    }

    private <T> void deliverError(RepositoryCallback<T> cb, Exception e) {
        mainHandler.post(() -> cb.onError(e));
    }
}
