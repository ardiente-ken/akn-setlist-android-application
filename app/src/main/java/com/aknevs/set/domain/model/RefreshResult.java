package com.aknevs.set.domain.model;

/** Outcome of fetching the latest copy of one song from the server. */
public enum RefreshResult {
    UPDATED,    // the server had newer lyrics/chords/title/artist; saved on this phone
    UNCHANGED,  // the phone already had exactly what the server has
    NOT_FOUND,  // the server has no active song with this id
    OFFLINE     // could not reach the server in time
}
