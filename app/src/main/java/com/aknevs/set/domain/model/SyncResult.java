package com.aknevs.set.domain.model;

public final class SyncResult {

    private final int songCount;
    private final long syncedAtMillis;

    public SyncResult(int songCount, long syncedAtMillis) {
        this.songCount = songCount;
        this.syncedAtMillis = syncedAtMillis;
    }

    public int getSongCount() { return songCount; }
    public long getSyncedAtMillis() { return syncedAtMillis; }
}
