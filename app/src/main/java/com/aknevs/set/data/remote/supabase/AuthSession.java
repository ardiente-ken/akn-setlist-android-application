package com.aknevs.set.data.remote.supabase;

/** An immutable Supabase Auth session. */
public final class AuthSession {

    public final String accessToken;
    public final String refreshToken;
    public final long expiresAtMillis;
    public final String email;
    public final String userId;

    public AuthSession(String accessToken, String refreshToken, long expiresAtMillis,
                       String email, String userId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAtMillis = expiresAtMillis;
        this.email = email;
        this.userId = userId;
    }
}
