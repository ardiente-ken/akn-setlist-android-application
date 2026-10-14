package com.aknevs.set.domain.usecase;

import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.repository.SongRepository;

/** Finds the library song behind a request, so its chords can be opened. */
public class FindRequestedSongUseCase {

    private final SongRepository repository;

    public FindRequestedSongUseCase(SongRepository repository) {
        this.repository = repository;
    }

    /** The callback receives the local song id, or null if the song is not in the library. */
    public void execute(String songId, String title, String artist, RepositoryCallback<String> callback) {
        repository.findRequestedSongId(songId, title, artist, callback);
    }
}
