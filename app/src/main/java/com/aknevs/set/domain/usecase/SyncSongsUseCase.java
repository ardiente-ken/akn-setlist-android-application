package com.aknevs.set.domain.usecase;

import com.aknevs.set.domain.model.SyncResult;
import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.repository.SongRepository;

public class SyncSongsUseCase {

    private final SongRepository repository;

    public SyncSongsUseCase(SongRepository repository) {
        this.repository = repository;
    }

    public void execute(RepositoryCallback<SyncResult> callback) {
        repository.syncSongs(callback);
    }

    public long getLastSyncMillis() {
        return repository.getLastSyncMillis();
    }
}
