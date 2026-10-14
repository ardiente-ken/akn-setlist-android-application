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

public class RequestAdapter extends ListAdapter<SongRequest, RequestAdapter.RequestViewHolder> {

    public interface ActionListener {
        void onOpenChords(SongRequest request);

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

    public RequestAdapter() {
        super(DIFF);
    }

    public void setActionListener(ActionListener listener) {
        this.actionListener = listener;
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

        holder.title.setText(request.getTitle());
        boolean hasArtist = !request.getArtist().trim().isEmpty();
        holder.artist.setVisibility(hasArtist ? View.VISIBLE : View.GONE);
        holder.artist.setText(request.getArtist());

        String name = request.getRequesterName().trim().isEmpty()
                ? context.getString(R.string.request_anonymous)
                : request.getRequesterName();
        String when = DateUtils.relativeTime(context, request.getCreatedAt());
        holder.meta.setText(when.isEmpty() ? name
                : context.getString(R.string.request_meta, name, when));

        // A rebind (e.g. after a failed removal) restores a pressed button.
        holder.remove.setEnabled(true);
        holder.remove.setAlpha(1f);
    }

    public class RequestViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView artist;
        final TextView meta;
        final TextView chords;
        final TextView remove;

        RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_request_title);
            artist = itemView.findViewById(R.id.text_request_artist);
            meta = itemView.findViewById(R.id.text_request_meta);
            chords = itemView.findViewById(R.id.button_request_chords);
            remove = itemView.findViewById(R.id.button_request_remove);

            chords.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && actionListener != null) {
                    actionListener.onOpenChords(getItem(position));
                }
            });
            remove.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && actionListener != null) {
                    remove.setEnabled(false); // no double taps while it is being removed
                    remove.setAlpha(0.4f);
                    actionListener.onRemove(getItem(position));
                }
            });
        }
    }
}
