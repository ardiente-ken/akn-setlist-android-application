package com.aknevs.set;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.aknevs.set.data.local.database.SongDatabaseHelper;
import com.aknevs.set.data.local.prefs.AuthPreferences;
import com.aknevs.set.data.local.prefs.SyncPreferences;
import com.aknevs.set.data.remote.supabase.SupabaseAuthDataSource;
import com.aknevs.set.data.remote.supabase.SupabaseClient;
import com.aknevs.set.data.remote.supabase.SupabaseRequestDataSource;
import com.aknevs.set.data.remote.supabase.SupabaseSettingsDataSource;
import com.aknevs.set.data.remote.supabase.SupabaseSongDataSource;
import com.aknevs.set.data.repository.RequestRepositoryImpl;
import com.aknevs.set.data.repository.SongRepositoryImpl;
import com.aknevs.set.domain.repository.SongRepository;
import com.aknevs.set.domain.usecase.FindRequestedSongUseCase;
import com.aknevs.set.domain.usecase.GetSongUseCase;
import com.aknevs.set.domain.usecase.GetSongsUseCase;
import com.aknevs.set.domain.usecase.ManageRequestsUseCase;
import com.aknevs.set.domain.usecase.SyncSongsUseCase;
import com.aknevs.set.presentation.common.FinishedSongsStore;

/** Builds the object graph once. Screens only ever see the use cases. */
public class AknApplication extends Application {

    private GetSongsUseCase getSongsUseCase;
    private GetSongUseCase getSongUseCase;
    private FindRequestedSongUseCase findRequestedSongUseCase;
    private SyncSongsUseCase syncSongsUseCase;
    private ManageRequestsUseCase manageRequestsUseCase;
    private final FinishedSongsStore finishedSongsStore = new FinishedSongsStore();

    @Override
    public void onCreate() {
        super.onCreate();
        SupabaseClient supabase = new SupabaseClient(
                BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY);

        SongRepository songs = new SongRepositoryImpl(
                new SongDatabaseHelper(this),
                new SupabaseSongDataSource(supabase),
                new SyncPreferences(this));
        getSongsUseCase = new GetSongsUseCase(songs);
        getSongUseCase = new GetSongUseCase(songs);
        findRequestedSongUseCase = new FindRequestedSongUseCase(songs);
        syncSongsUseCase = new SyncSongsUseCase(songs);

        manageRequestsUseCase = new ManageRequestsUseCase(new RequestRepositoryImpl(
                supabase,
                new SupabaseRequestDataSource(supabase),
                new SupabaseSettingsDataSource(supabase),
                new SupabaseAuthDataSource(supabase),
                new AuthPreferences(this)));

        // The realtime connection only runs while one of our screens is visible.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private int started = 0;

            @Override public void onActivityStarted(Activity activity) {
                if (++started == 1) manageRequestsUseCase.setAppForeground(true);
            }

            @Override public void onActivityStopped(Activity activity) {
                if (--started == 0) manageRequestsUseCase.setAppForeground(false);
            }

            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    public GetSongsUseCase getGetSongsUseCase() { return getSongsUseCase; }

    public GetSongUseCase getGetSongUseCase() { return getSongUseCase; }

    public FindRequestedSongUseCase getFindRequestedSongUseCase() { return findRequestedSongUseCase; }

    public SyncSongsUseCase getSyncSongsUseCase() { return syncSongsUseCase; }

    public ManageRequestsUseCase getManageRequestsUseCase() { return manageRequestsUseCase; }

    public FinishedSongsStore getFinishedSongsStore() { return finishedSongsStore; }
}
