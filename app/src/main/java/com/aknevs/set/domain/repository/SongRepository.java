package com.aknevs.set.domain.repository;

import com.aknevs.set.domain.model.Song;
import com.aknevs.set.domain.model.SyncResult;

import java.util.List;

public interface SongRepository {

    /** Reads from local storage only. Empty/blank query returns every song, sorted by title. */
    void getSongs(String query, RepositoryCallback<List<Song>> callback);

    /** Loads one complete song (lyrics and chords included) from local storage. */
    void getSong(String id, RepositoryCallback<Song> callback);

    /** Finds the stored song a request refers to. Result is its id, or null if not in the library. */
    void findRequestedSongId(String songId, String title, String artist,
                             RepositoryCallback<String> callback);

    /** Fetches active songs from the remote and atomically replaces the local collection. */
    void syncSongs(RepositoryCallback<SyncResult> callback);

    long getLastSyncMillis();
}
