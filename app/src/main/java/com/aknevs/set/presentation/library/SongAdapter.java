package com.aknevs.set.presentation.library;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.aknevs.set.R;
import com.aknevs.set.domain.model.Song;
import com.aknevs.set.presentation.common.FinishedSongsStore;

import java.util.List;

public class SongAdapter extends ListAdapter<Song, SongAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(Song song);
    }

    private static final Object PAYLOAD_FINISHED = new Object();
    private static final float FINISHED_ALPHA = 0.4f;

    private static final DiffUtil.ItemCallback<Song> DIFF = new DiffUtil.ItemCallback<Song>() {
        @Override
        public boolean areItemsTheSame(@NonNull Song a, @NonNull Song b) {
            return a.getId().equals(b.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Song a, @NonNull Song b) {
            return a.getTitle().equals(b.getTitle())
                    && a.getArtist().equals(b.getArtist())
                    && a.getUpdatedAt().equals(b.getUpdatedAt());
        }
    };

    private final FinishedSongsStore finishedSongs;
    private OnSongClickListener listener;

    public SongAdapter(FinishedSongsStore finishedSongs) {
        super(DIFF);
        this.finishedSongs = finishedSongs;
    }

    public void setOnSongClickListener(OnSongClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = getItem(position);
        holder.title.setText(song.getTitle());
        holder.artist.setText(song.getArtist());
        bindFinished(holder, song);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_FINISHED)) {
            bindFinished(holder, getItem(position)); // only the mark changed: skip the rest
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void bindFinished(SongViewHolder holder, Song song) {
        boolean finished = finishedSongs.isFinished(song.getId());
        holder.mark.setSelected(finished);
        holder.mark.setContentDescription(holder.itemView.getContext().getString(
                finished ? R.string.finished_undo : R.string.finished_mark));
        holder.textColumn.setAlpha(finished ? FINISHED_ALPHA : 1f);
    }

    public class SongViewHolder extends RecyclerView.ViewHolder {
        final View textColumn;
        final TextView title;
        final TextView artist;
        final ImageView mark;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            textColumn = itemView.findViewById(R.id.column_text);
            title = itemView.findViewById(R.id.text_title);
            artist = itemView.findViewById(R.id.text_artist);
            mark = itemView.findViewById(R.id.button_finished);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSongClick(getItem(position));
                }
            });
            mark.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                finishedSongs.toggle(getItem(position).getId());
                notifyItemChanged(position, PAYLOAD_FINISHED);
            });
        }
    }
}
