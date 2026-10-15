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
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.aknevs.set.AknApplication;
import com.aknevs.set.R;
import com.aknevs.set.domain.model.RequestsAvailability;
import com.aknevs.set.domain.model.RequestsConnection;
import com.aknevs.set.domain.model.SongRequest;
import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.repository.RequestListener;
import com.aknevs.set.domain.usecase.FindRequestedSongUseCase;
import com.aknevs.set.domain.usecase.ManageRequestsUseCase;
import com.aknevs.set.presentation.songdetail.SongDetailActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Requests (count)                         Live
 * [ Song requests: Open                (toggle) ]
 * 1. Title                     [Open] [Remove]
 * Updated live while it is open.
 */
public class RequestQueueSheet extends BottomSheetDialogFragment {

    private static final String TAG = "request_queue_sheet";

    public static void show(FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) == null) {
            new RequestQueueSheet().show(fragmentManager, TAG);
        }
    }

    private ManageRequestsUseCase requests;
    private FindRequestedSongUseCase findSong;
    private RequestAdapter adapter;

    private TextView titleText;
    private View dot;
    private TextView connText;
    private TextView detailText;
    private TextView settingText;
    private TextView adminText;
    private MaterialSwitch requestSwitch;
    private TextView signInButton;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyMessage;

    private RequestsAvailability availability = RequestsAvailability.LOADING;
    private RequestsConnection connection = RequestsConnection.CONNECTING;
    private String detail = "";
    private boolean admin = false;
    private String email = "";
    private boolean writing = false;
    private boolean bindingSwitch = false;
    private int count = 0;

    private final RequestListener listener = new RequestListener() {
        @Override
        public void onRequestsChanged(List<SongRequest> pending) {
            count = pending.size();
            titleText.setText(getString(R.string.queue_title_count, count));
            adapter.submit(pending);
            refreshEmptyState();
        }

        @Override
        public void onConnectionChanged(RequestsConnection c, String d) {
            connection = c;
            detail = d == null ? "" : d;
            refreshConnection();
        }

        @Override
        public void onAvailabilityChanged(RequestsAvailability a) {
            availability = a;
            refreshSetting();
            refreshEmptyState();
        }

        @Override
        public void onAdminChanged(boolean isAdmin, String adminEmail) {
            admin = isAdmin;
            email = adminEmail == null ? "" : adminEmail;
            adapter.setAdmin(isAdmin);
            refreshSetting();
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

        titleText = view.findViewById(R.id.text_sheet_title);
        dot = view.findViewById(R.id.view_sheet_dot);
        connText = view.findViewById(R.id.text_sheet_conn);
        detailText = view.findViewById(R.id.text_sheet_detail);
        settingText = view.findViewById(R.id.text_sheet_setting);
        adminText = view.findViewById(R.id.text_sheet_admin);
        requestSwitch = view.findViewById(R.id.switch_sheet_requests);
        signInButton = view.findViewById(R.id.button_sheet_signin);
        emptyState = view.findViewById(R.id.sheet_empty_state);
        emptyTitle = view.findViewById(R.id.text_sheet_empty_title);
        emptyMessage = view.findViewById(R.id.text_sheet_empty_message);

        signInButton.setOnClickListener(v -> AdminSignInDialog.show(requireContext(), requests));
        adminText.setOnClickListener(v -> requests.signOutAdmin());
        requestSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingSwitch) setRequestsOpen(checked);
        });

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
        if (recycler.getItemAnimator() instanceof SimpleItemAnimator) {
            // renumbering rows must not flash
            ((SimpleItemAnimator) recycler.getItemAnimator()).setSupportsChangeAnimations(false);
        }
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

    // ---------- header, setting card, empty state ----------

    private void refreshConnection() {
        Context context = requireContext();
        connText.setText(RequestsUi.connectionText(context, connection));
        connText.setTextColor(RequestsUi.connectionColor(context, connection));
        dot.getBackground().mutate().setTint(RequestsUi.connectionColor(context, connection));
        boolean showDetail = connection == RequestsConnection.OFFLINE && !detail.isEmpty();
        detailText.setVisibility(showDetail ? View.VISIBLE : View.GONE);
        detailText.setText(detail);
        refreshEmptyState();
    }

    private void refreshSetting() {
        settingText.setText(RequestsUi.settingText(requireContext(), availability));
        signInButton.setVisibility(admin ? View.GONE : View.VISIBLE);
        requestSwitch.setVisibility(admin ? View.VISIBLE : View.GONE);
        adminText.setVisibility(admin ? View.VISIBLE : View.GONE);
        if (admin) adminText.setText(getString(R.string.admin_signed_in_as, email));

        requestSwitch.setEnabled(admin && !writing && availability != RequestsAvailability.LOADING);
        if (!writing) { // during a change the switch keeps showing what the admin just chose
            bindingSwitch = true;
            requestSwitch.setChecked(availability == RequestsAvailability.OPEN);
            bindingSwitch = false;
        }
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

    // ---------- actions ----------

    private void setRequestsOpen(boolean open) {
        writing = true;
        refreshSetting();
        requests.setRequestsOpen(open, new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean value) {
                if (!isAdded()) return;
                writing = false;
                refreshSetting();
            }

            @Override
            public void onError(Exception error) {
                if (!isAdded()) return;
                writing = false;
                refreshSetting(); // snaps the switch back to the real value
                showMessage(RequestsUi.adminErrorMessage(error));
            }
        });
    }

    /** Opens the chord chart of the requested song, if it is in the local library. */
    private void openChords(SongRequest request) {
        findSong.execute(request.getSongId(), request.getTitle(), request.getArtist(),
                new RepositoryCallback<String>() {
                    @Override
                    public void onSuccess(String songId) {
                        if (!isAdded()) return;
                        if (songId == null) {
                            adapter.notifyDataSetChanged(); // re-enable the button
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
                        if (!isAdded()) return;
                        adapter.notifyDataSetChanged();
                        showMessage(R.string.request_song_missing);
                    }
                });
    }

    /** Removing changes the shared queue, so it needs the admin sign-in. */
    private void removeRequest(SongRequest request) {
        if (!admin) {
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
}
