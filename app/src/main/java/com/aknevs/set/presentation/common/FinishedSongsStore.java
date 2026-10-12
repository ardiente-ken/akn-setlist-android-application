package com.aknevs.set.presentation.common;

import java.util.HashSet;
import java.util.Set;

/**
 * "Finished" marks for the current session. Deliberately held in memory only: they are
 * cleared whenever the app process starts fresh. Main thread only.
 */
public class FinishedSongsStore {

    private final Set<String> finishedIds = new HashSet<>();

    public boolean isFinished(String songId) {
        return finishedIds.contains(songId);
    }

    /** @return the new state: true if the song is now marked finished. */
    public boolean toggle(String songId) {
        if (!finishedIds.remove(songId)) {
            finishedIds.add(songId);
            return true;
        }
        return false;
    }
}
