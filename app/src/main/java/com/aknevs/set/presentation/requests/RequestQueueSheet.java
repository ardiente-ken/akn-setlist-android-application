package com.aknevs.set.presentation.requests;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aknevs.set.AknApplication;
import com.aknevs.set.R;
import com.aknevs.set.domain.model.RequestsAvailability;
import com.aknevs.set.domain.model.RequestsConnection;
import com.aknevs.set.domain.model.SongRequest;
import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.repository.RequestListener;
import com.aknevs.set.domain.usecase.FindRequestedSongUseCase;
import com.aknevs.set.domain.usecase.ManageRequestsUseCase;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;
import com.aknevs.set.presentation.songdetail.SongDetailActivity;

import java.util.ArrayList;
import java.util.List;

/** The pending request queue, updated live while it is open. */
public class RequestQueueSheet extends BottomSheetDialogFragment {

    private static final String TAG = "request_queue_sheet";

    public static void show(FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) == null) {
            new RequestQueueSheet().show(fragmentManager, TAG);
        }
    }

    private ManageRequestsUseCase requests;
    private FindRequestedSongUseCase findSong;
    private boolean admin = false;
    private RequestAdapter adapter;
    private View dot;
    private TextView statusText;
    private TextView detailText;
    private TextView signOutButton;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyMessage;

    private RequestsAvailability availability = RequestsAvailability.LOADING;
    private RequestsConnection connection = RequestsConnection.CONNECTING;
    private String detail = "";
    private int count = 0;

    private final RequestListener listener = new RequestListener() {
        @Override
        public void onRequestsChanged(List<SongRequest> pending) {
            count = pending.size();
            adapter.submitList(pending);
            refreshEmptyState();
        }

        @Override
        public void onConnectionChanged(RequestsConnection c, String d) {
            connection = c;
            detail = d == null ? "" : d;
            refreshStatus();
        }

        @Override
        public void onAvailabilityChanged(RequestsAvailability a) {
            availability = a;
            refreshStatus();
        }

        @Override
        public void onAdminChanged(boolean admin, String email) {
            RequestQueueSheet.this.admin = admin;
            signOutButton.setVisibility(admin ? View.VISIBLE : View.GONE);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_request_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AknApplication app = (AknApplication) requireActivity().getApplication();
        requests = app.getManageRequestsUseCase();
        findSong = app.getFindRequestedSongUseCase();

        dot = view.findViewById(R.id.view_sheet_dot);
        statusText = view.findViewById(R.id.text_sheet_status);
        detailText = view.findViewById(R.id.text_sheet_detail);
        signOutButton = view.findViewById(R.id.button_sheet_signout);
        emptyState = view.findViewById(R.id.sheet_empty_state);
        emptyTitle = view.findViewById(R.id.text_sheet_empty_title);
        emptyMessage = view.findViewById(R.id.text_sheet_empty_message);

        signOutButton.setOnClickListener(v -> requests.signOutAdmin());

        RecyclerView recycler = view.findViewById(R.id.recycler_requests);
        adapter = new RequestAdapter();
        adapter.setActionListener(new RequestAdapter.ActionListener() {
            @Override
            public void onOpenChords(SongRequest request) {
                openChords(request);
            }

            @Override
            public void onRemove(SongRequest request) {
                removeRequest(request);
            }
        });
        recycler.setAdapter(adapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        requests.observe(listener); // replays the current state immediately
    }

    @Override
    public void onStop() {
        requests.stopObserving(listener);
        super.onStop();
    }

    /** Opens the chord chart of the requested song, if it is in the local library. */
    private void openChords(SongRequest request) {
        findSong.execute(request.getSongId(), request.getTitle(), request.getArtist(),
                new RepositoryCallback<String>() {
                    @Override
                    public void onSuccess(String songId) {
                        if (!isAdded()) return;
                        if (songId == null) {
                            showMessage(R.string.request_song_missing);
                            return;
                        }
                        ArrayList<String> ids = new ArrayList<>();
                        ids.add(songId);
                        Context context = requireContext();
                        dismiss(); // so the queue is reachable again when you come back
                        context.startActivity(SongDetailActivity.newIntent(context, ids, 0));
                    }

                    @Override
                    public void onError(Exception error) {
                        if (isAdded()) showMessage(R.string.request_song_missing);
                    }
                });
    }

    /** Removing changes the shared queue, so it needs the admin sign-in. */
    private void removeRequest(SongRequest request) {
        if (!admin) {
            adapter.notifyDataSetChanged(); // un-press the button
            AdminSignInDialog.show(requireContext(), requests);
            return;
        }
        requests.removeRequest(request.getId(), new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean removed) {
                // The queue listener drops the row; nothing else to do.
            }

            @Override
            public void onError(Exception error) {
                if (!isAdded()) return;
                adapter.notifyDataSetChanged(); // un-press the button
                showMessage(RequestsUi.removeErrorMessage(error));
            }
        });
    }

    private void showMessage(@StringRes int message) {
        Snackbar snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.akn_surface_pressed));
        snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.akn_text_primary));
        snackbar.setTextMaxLines(3);
        snackbar.show();
    }

    private void refreshStatus() {
        statusText.setText(RequestsUi.statusText(requireContext(), availability));
        dot.getBackground().mutate()
                .setTint(RequestsUi.dotColor(requireContext(), availability, connection));
        boolean showDetail = connection == RequestsConnection.OFFLINE && !detail.isEmpty();
        detailText.setVisibility(showDetail ? View.VISIBLE : View.GONE);
        detailText.setText(detail);
        refreshEmptyState();
    }

    private void refreshEmptyState() {
        emptyState.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        if (count != 0) return;
        if (connection != RequestsConnection.LIVE) {
            emptyTitle.setText(R.string.queue_waiting_title);
            emptyMessage.setText(R.string.queue_waiting_message);
        } else if (availability == RequestsAvailability.CLOSED) {
            emptyTitle.setText(R.string.queue_closed_title);
            emptyMessage.setText(R.string.queue_closed_message);
        } else {
            emptyTitle.setText(R.string.queue_empty_title);
            emptyMessage.setText(R.string.queue_empty_message);
        }
    }
}
