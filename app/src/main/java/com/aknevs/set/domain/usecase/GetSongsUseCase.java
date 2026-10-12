package com.aknevs.set.domain.usecase;

import com.aknevs.set.domain.model.Song;
import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.repository.SongRepository;

import java.util.List;

public class GetSongsUseCase {

    private final SongRepository repository;

    public GetSongsUseCase(SongRepository repository) {
        this.repository = repository;
    }

    public void execute(String query, RepositoryCallback<List<Song>> callback) {
        repository.getSongs(query, callback);
    }
}
