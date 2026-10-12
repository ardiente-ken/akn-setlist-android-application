package com.aknevs.set.domain.model;

/**
 * A song. Timestamps stay as the ISO strings Supabase returned, so nothing is lost.
 * Note: list queries leave lyrics/chords empty on purpose (see SongDatabaseHelper.querySongs).
 */
public final class Song {

    private final String id;
    private final String title;
    private final String artist;
    private final boolean active;
    private final String createdAt;
    private final String lyrics;
    private final String chords;
    private final String updatedAt;

    public Song(String id, String title, String artist, boolean active, String createdAt,
                String lyrics, String chords, String updatedAt) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.active = active;
        this.createdAt = createdAt;
        this.lyrics = lyrics;
        this.chords = chords;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public boolean isActive() { return active; }
    public String getCreatedAt() { return createdAt; }
    public String getLyrics() { return lyrics; }
    public String getChords() { return chords; }
    public String getUpdatedAt() { return updatedAt; }
}
