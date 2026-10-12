package com.aknevs.set.domain.usecase;

import com.aknevs.set.domain.model.Song;
import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.repository.SongRepository;

/** Loads one full song (with lyrics and chords) from local storage. */
public class GetSongUseCase {

    private final SongRepository repository;

    public GetSongUseCase(SongRepository repository) {
        this.repository = repository;
    }

    public void execute(String id, RepositoryCallback<Song> callback) {
        repository.getSong(id, callback);
    }
}
