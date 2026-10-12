package com.aknevs.set;

import android.app.Application;

import com.aknevs.set.data.local.database.SongDatabaseHelper;
import com.aknevs.set.data.local.prefs.SyncPreferences;
import com.aknevs.set.data.remote.supabase.SupabaseClient;
import com.aknevs.set.data.remote.supabase.SupabaseSongDataSource;
import com.aknevs.set.data.repository.SongRepositoryImpl;
import com.aknevs.set.domain.repository.SongRepository;
import com.aknevs.set.domain.usecase.GetSongUseCase;
import com.aknevs.set.domain.usecase.GetSongsUseCase;
import com.aknevs.set.domain.usecase.SyncSongsUseCase;
import com.aknevs.set.presentation.common.FinishedSongsStore;

/** Builds the object graph once. Screens only ever see the use cases. */
public class AknApplication extends Application {

    private GetSongsUseCase getSongsUseCase;
    private GetSongUseCase getSongUseCase;
    private SyncSongsUseCase syncSongsUseCase;
    private final FinishedSongsStore finishedSongsStore = new FinishedSongsStore();

    @Override
    public void onCreate() {
        super.onCreate();
        SongRepository repository = new SongRepositoryImpl(
                new SongDatabaseHelper(this),
                new SupabaseSongDataSource(
                        new SupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)),
                new SyncPreferences(this));
        getSongsUseCase = new GetSongsUseCase(repository);
        getSongUseCase = new GetSongUseCase(repository);
        syncSongsUseCase = new SyncSongsUseCase(repository);
    }

    public GetSongsUseCase getGetSongsUseCase() { return getSongsUseCase; }

    public GetSongUseCase getGetSongUseCase() { return getSongUseCase; }

    public SyncSongsUseCase getSyncSongsUseCase() { return syncSongsUseCase; }

    public FinishedSongsStore getFinishedSongsStore() { return finishedSongsStore; }
}
