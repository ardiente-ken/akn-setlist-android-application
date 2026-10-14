package com.aknevs.set.domain.repository;

import com.aknevs.set.domain.model.RequestsAvailability;
import com.aknevs.set.domain.model.RequestsConnection;
import com.aknevs.set.domain.model.SongRequest;

import java.util.List;

/** All callbacks arrive on the main thread. Implement only what you need. */
public interface RequestListener {

    /** The full pending queue, oldest first. */
    default void onRequestsChanged(List<SongRequest> pending) {}

    default void onConnectionChanged(RequestsConnection connection, String detail) {}

    /** Requests opened or closed (live, from the shared database setting). */
    default void onAvailabilityChanged(RequestsAvailability availability) {}

    /** The performer signed in as admin, or out. */
    default void onAdminChanged(boolean admin, String email) {}

    /** A request that arrived live (not part of the initial load). */
    default void onNewRequest(SongRequest request) {}
}
