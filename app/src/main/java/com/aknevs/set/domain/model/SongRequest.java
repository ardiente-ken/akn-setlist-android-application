package com.aknevs.set.domain.model;

/** A request sent from the web app. Mirrors a row of public.song_requests. */
public final class SongRequest {

    private final String id;
    private final String songId;        // null for "something else" requests
    private final String title;
    private final String artist;
    private final String requesterName;
    private final String status;        // pending | played | dismissed
    private final String createdAt;     // ISO string, kept as received
    private final String handledAt;     // null until handled

    public SongRequest(String id, String songId, String title, String artist,
                       String requesterName, String status, String createdAt, String handledAt) {
        this.id = id;
        this.songId = songId;
        this.title = title;
        this.artist = artist;
        this.requesterName = requesterName;
        this.status = status;
        this.createdAt = createdAt;
        this.handledAt = handledAt;
    }

    public String getId() { return id; }
    public String getSongId() { return songId; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getRequesterName() { return requesterName; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getHandledAt() { return handledAt; }
}
