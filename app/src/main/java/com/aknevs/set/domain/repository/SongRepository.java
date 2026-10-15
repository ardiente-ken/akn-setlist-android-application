package com.aknevs.set.domain.repository;

import com.aknevs.set.domain.model.RefreshResult;
import com.aknevs.set.domain.model.Song;
import com.aknevs.set.domain.model.SyncResult;

import java.util.List;

public interface SongRepository {

    /** Reads from local storage only. Empty/blank query returns every song, sorted by title. */
    void getSongs(String query, RepositoryCallback<List<Song>> callback);

    /** Loads one complete song (lyrics and chords included) from local storage. */
    void getSong(String id, RepositoryCallback<Song> callback);

    /**
     * Resolves the song a request refers to. Answers instantly from local storage; only if the
     * song is not stored yet does it fetch it from the server. Result is the local song id,
     * or null if the song is not in the library.
     */
    void findRequestedSongId(String songId, String title, String artist,
                             RepositoryCallback<String> callback);

    /** Fetches the latest copy of one song and stores it if anything changed. */
    void refreshSong(String id, RepositoryCallback<RefreshResult> callback);

    /** Fetches active songs from the remote and atomically replaces the local collection. */
    void syncSongs(RepositoryCallback<SyncResult> callback);

    long getLastSyncMillis();
}
