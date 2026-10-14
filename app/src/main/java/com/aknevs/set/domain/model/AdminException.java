package com.aknevs.set.domain.model;

public class AdminException extends Exception {

    public enum Reason {
        NOT_CONFIGURED,
        NOT_SIGNED_IN,        // no session, or it can no longer be refreshed
        INVALID_CREDENTIALS,
        NOT_ADMIN,            // signed in, but the database refused the change (RLS)
        NETWORK,
        SERVER
    }

    private final Reason reason;

    public AdminException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AdminException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
