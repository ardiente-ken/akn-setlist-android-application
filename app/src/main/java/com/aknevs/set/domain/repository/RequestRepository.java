package com.aknevs.set.domain.repository;

/** Main thread only. Adding a listener immediately replays the current state to it. */
public interface RequestRepository {

    void addListener(RequestListener listener);

    void removeListener(RequestListener listener);

    /** The live connection only runs while one of the app's screens is visible. */
    void setAppForeground(boolean foreground);

    /** Result true: signed in as admin. Result false: valid login, but not an admin (signed out again). */
    void signInAdmin(String email, String password, RepositoryCallback<Boolean> callback);

    void signOutAdmin();

    boolean isAdmin();

    String getAdminEmail();

    /** Admin only. Marks the request dismissed; it leaves the queue on success. */
    void removeRequest(String requestId, RepositoryCallback<Boolean> callback);

    /** Admin only. Writes the shared setting that the website also reads. */
    void setRequestsOpen(boolean open, RepositoryCallback<Boolean> callback);
}
