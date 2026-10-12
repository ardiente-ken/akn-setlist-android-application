package com.aknevs.set.presentation.library;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.aknevs.set.AknApplication;
import com.aknevs.set.R;
import com.aknevs.set.core.utils.DateUtils;
import com.aknevs.set.domain.model.Song;
import com.aknevs.set.domain.model.SyncException;
import com.aknevs.set.domain.model.SyncResult;
import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.usecase.GetSongsUseCase;
import com.aknevs.set.domain.usecase.SyncSongsUseCase;
import com.aknevs.set.presentation.songdetail.SongDetailActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/** Song library: reads only from local storage; Sync refreshes that storage. */
public class SongListFragment extends Fragment {

    private static final long SEARCH_DEBOUNCE_MS = 200;

    private GetSongsUseCase getSongs;
    private SyncSongsUseCase syncSongs;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int requestId = 0;
    private int totalCount = 0;
    private boolean syncing = false;

    private SongAdapter adapter;
    private View root;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private TextView lastSyncText;
    private TextView countText;
    private EditText searchInput;
    private ImageButton clearButton;
    private MaterialButton syncButton;
    private LinearProgressIndicator progress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_song_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AknApplication app = (AknApplication) requireActivity().getApplication();
        getSongs = app.getGetSongsUseCase();
        syncSongs = app.getSyncSongsUseCase();

        root = view;
        emptyState = view.findViewById(R.id.empty_state);
        emptyTitle = view.findViewById(R.id.text_empty_title);
        emptyMessage = view.findViewById(R.id.text_empty_message);
        lastSyncText = view.findViewById(R.id.text_last_sync);
        countText = view.findViewById(R.id.text_song_count);
        searchInput = view.findViewById(R.id.edit_search);
        clearButton = view.findViewById(R.id.button_clear_search);
        syncButton = view.findViewById(R.id.button_sync);
        progress = view.findViewById(R.id.sync_progress);

        RecyclerView recycler = view.findViewById(R.id.recycler_songs);
        adapter = new SongAdapter(app.getFinishedSongsStore());
        recycler.setHasFixedSize(true);
        recycler.setAdapter(adapter);
        adapter.setOnSongClickListener(this::openSong);

        syncButton.setOnClickListener(v -> startSync());
        clearButton.setOnClickListener(v -> searchInput.setText(""));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                clearButton.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                scheduleSearch();
            }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager imm = (InputMethodManager)
                        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        updateLastSync();
        updateCount();
        setSyncing(syncing);
        loadSongs();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        requestId++; // invalidate any in-flight load
        super.onDestroyView();
    }

    // ---------- loading from SQLite ----------

    private String currentQuery() {
        return searchInput == null ? "" : searchInput.getText().toString().trim();
    }

    private void scheduleSearch() {
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        pendingSearch = this::loadSongs;
        handler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void loadSongs() {
        final String query = currentQuery();
        final int myRequest = ++requestId;
        getSongs.execute(query, new RepositoryCallback<List<Song>>() {
            @Override
            public void onSuccess(List<Song> songs) {
                if (!isViewAlive() || myRequest != requestId) return; // stale result
                adapter.submitList(songs);
                if (query.isEmpty()) {
                    totalCount = songs.size();
                    updateCount();
                }
                showEmptyState(songs.isEmpty(), !query.isEmpty());
            }

            @Override
            public void onError(Exception error) {
                if (!isViewAlive() || myRequest != requestId) return;
                showMessage(R.string.error_load);
            }
        });
    }

    // ---------- opening a song ----------

    /** Opens the detail screen on the songs currently shown, so Prev/Next follow the list. */
    private void openSong(Song song) {
        searchInput.clearFocus();
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);

        List<Song> shown = adapter.getCurrentList();
        ArrayList<String> ids = new ArrayList<>(shown.size());
        int index = 0;
        for (int i = 0; i < shown.size(); i++) {
            ids.add(shown.get(i).getId());
            if (shown.get(i).getId().equals(song.getId())) index = i;
        }
        startActivity(SongDetailActivity.newIntent(requireContext(), ids, index));
    }

    // ---------- sync ----------

    private void startSync() {
        if (syncing) return;
        setSyncing(true);
        syncSongs.execute(new RepositoryCallback<SyncResult>() {
            @Override
            public void onSuccess(SyncResult result) {
                if (!isViewAlive()) return;
                setSyncing(false);
                totalCount = result.getSongCount();
                updateCount();
                updateLastSync();
                loadSongs(); // re-read from SQLite, keeping the current search
                showMessage(getResources().getQuantityString(
                        R.plurals.sync_success, result.getSongCount(), result.getSongCount()));
            }

            @Override
            public void onError(Exception error) {
                if (!isViewAlive()) return;
                setSyncing(false);
                showMessage(messageFor(error)); // local songs were not touched
            }
        });
    }

    private void setSyncing(boolean value) {
        syncing = value;
        if (syncButton == null) return;
        syncButton.setEnabled(!value);
        syncButton.setAlpha(value ? 0.6f : 1f);
        syncButton.setText(value ? R.string.sync_syncing : R.string.action_sync);
        progress.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
    }

    @StringRes
    private int messageFor(Exception error) {
        if (error instanceof SyncException) {
            switch (((SyncException) error).getReason()) {
                case NOT_CONFIGURED: return R.string.error_not_configured;
                case NETWORK:        return R.string.error_network;
                case AUTH:           return R.string.error_auth;
                case SERVER:         return R.string.error_server;
                case INVALID_DATA:   return R.string.error_invalid_data;
                case STORAGE:        return R.string.error_storage;
                case BUSY:           return R.string.sync_syncing;
            }
        }
        return R.string.error_unknown;
    }

    // ---------- UI helpers ----------

    private boolean isViewAlive() {
        return isAdded() && getView() != null;
    }

    private void updateLastSync() {
        lastSyncText.setText(
                DateUtils.formatLastSync(requireContext(), syncSongs.getLastSyncMillis()));
    }

    private void updateCount() {
        countText.setText(getResources().getQuantityString(
                R.plurals.song_count, totalCount, totalCount));
    }

    private void showEmptyState(boolean empty, boolean searching) {
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;
        emptyTitle.setText(searching ? R.string.empty_search_title : R.string.empty_title);
        emptyMessage.setText(searching ? R.string.empty_search_message : R.string.empty_message);
    }

    private void showMessage(@StringRes int resId) {
        showMessage(getString(resId));
    }

    private void showMessage(String text) {
        Snackbar snackbar = Snackbar.make(root, text, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.akn_surface_pressed));
        snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.akn_text_primary));
        snackbar.setTextMaxLines(3);
        snackbar.show();
    }
}
