package com.aknevs.set.presentation.requests;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.aknevs.set.R;
import com.aknevs.set.core.utils.DateUtils;
import com.aknevs.set.domain.model.SongRequest;

import java.util.ArrayList;
import java.util.List;

public class RequestAdapter extends ListAdapter<SongRequest, RequestAdapter.RequestViewHolder> {

    public interface ActionListener {
        void onOpenChords(SongRequest request);

        /** Tapped Remove. When signed out, the same button reads "Sign in" and lands here too. */
        void onRemove(SongRequest request);
    }

    private static final DiffUtil.ItemCallback<SongRequest> DIFF =
            new DiffUtil.ItemCallback<SongRequest>() {
                @Override
                public boolean areItemsTheSame(@NonNull SongRequest a, @NonNull SongRequest b) {
                    return a.getId().equals(b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull SongRequest a, @NonNull SongRequest b) {
                    return a.getTitle().equals(b.getTitle())
                            && a.getArtist().equals(b.getArtist())
                            && a.getRequesterName().equals(b.getRequesterName())
                            && a.getStatus().equals(b.getStatus());
                }
            };

    private ActionListener actionListener;
    private boolean admin;

    public RequestAdapter() {
        super(DIFF);
    }

    public void setActionListener(ActionListener listener) {
        this.actionListener = listener;
    }

    /** Admins see Remove; everyone else sees Sign in on the same button. */
    public void setAdmin(boolean admin) {
        if (this.admin == admin) return;
        this.admin = admin;
        notifyItemRangeChanged(0, getItemCount());
    }

    /** Submits a new queue and renumbers the rows afterwards (1., 2., 3. ...). */
    public void submit(List<SongRequest> requests) {
        submitList(new ArrayList<>(requests), () -> notifyItemRangeChanged(0, getItemCount()));
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new RequestViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_request, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        SongRequest request = getItem(position);
        Context context = holder.itemView.getContext();

        holder.number.setText(context.getString(R.string.request_number, position + 1));
        holder.title.setText(request.getTitle());

        List<String> parts = new ArrayList<>();
        if (!request.getArtist().trim().isEmpty()) parts.add(request.getArtist().trim());
        parts.add(request.getRequesterName().trim().isEmpty()
                ? context.getString(R.string.request_anonymous)
                : request.getRequesterName().trim());
        String when = DateUtils.relativeTime(context, request.getCreatedAt());
        if (!when.isEmpty()) parts.add(when);
        holder.meta.setText(android.text.TextUtils.join(" \u00B7 ", parts));

        holder.chords.setEnabled(true);
        holder.chords.setAlpha(1f);
        holder.remove.setText(admin ? R.string.request_remove : R.string.request_sign_in);
        holder.remove.setEnabled(true); // a rebind restores a pressed button
        holder.remove.setAlpha(1f);
    }

    public class RequestViewHolder extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView title;
        final TextView meta;
        final TextView chords;
        final TextView remove;

        RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            number = itemView.findViewById(R.id.text_request_number);
            title = itemView.findViewById(R.id.text_request_title);
            meta = itemView.findViewById(R.id.text_request_meta);
            chords = itemView.findViewById(R.id.button_request_chords);
            remove = itemView.findViewById(R.id.button_request_remove);

            chords.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && actionListener != null) {
                    chords.setEnabled(false); // looking the song up; no double taps
                    chords.setAlpha(0.4f);
                    actionListener.onOpenChords(getItem(position));
                }
            });
            remove.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && actionListener != null) {
                    if (admin) { // no double taps while it is being removed
                        remove.setEnabled(false);
                        remove.setAlpha(0.4f);
                    }
                    actionListener.onRemove(getItem(position));
                }
            });
        }
    }
}
