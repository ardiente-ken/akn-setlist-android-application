package com.aknevs.set.domain.model;

/** Why a sync failed. The local collection is never modified when this is thrown. */
public class SyncException extends Exception {

    public enum Reason {
        NOT_CONFIGURED, // Supabase URL/key still placeholders
        NETWORK,        // offline, DNS, timeout
        AUTH,           // 401/403
        SERVER,         // any other non-2xx
        INVALID_DATA,   // unparseable or incomplete response
        STORAGE,        // SQLite write failed (transaction rolled back)
        BUSY            // a sync is already running
    }

    private final Reason reason;

    public SyncException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public SyncException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
