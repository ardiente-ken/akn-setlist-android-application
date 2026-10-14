package com.aknevs.set.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aknevs.set.core.constants.AppConstants;
import com.aknevs.set.data.local.prefs.AuthPreferences;
import com.aknevs.set.data.remote.supabase.AuthSession;
import com.aknevs.set.data.remote.supabase.SupabaseAuthDataSource;
import com.aknevs.set.data.remote.supabase.SupabaseClient;
import com.aknevs.set.data.remote.supabase.SupabaseRealtimeClient;
import com.aknevs.set.data.remote.supabase.SupabaseRequestDataSource;
import com.aknevs.set.data.remote.supabase.SupabaseSettingsDataSource;
import com.aknevs.set.domain.model.AdminException;
import com.aknevs.set.domain.model.RequestsAvailability;
import com.aknevs.set.domain.model.RequestsConnection;
import com.aknevs.set.domain.model.SongRequest;
import com.aknevs.set.domain.repository.RepositoryCallback;
import com.aknevs.set.domain.repository.RequestListener;
import com.aknevs.set.domain.repository.RequestRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Three things, all kept live while the app is on screen:
 *  1. the pending request queue (song_requests),
 *  2. whether requests are open (app_settings 'requests_enabled', shared with the website),
 *  3. the admin session that is allowed to change that setting.
 *
 * Realtime delivers changes. If the socket drops it reconnects with backoff, reloads
 * everything on reconnect, and polls the setting every 20 s while it is down.
 * All state lives on the main thread; background work is posted back onto it.
 */
public class RequestRepositoryImpl implements RequestRepository {

    private static final String TAG = "RequestRepository";
    private static final long MAX_RETRY_MS = 15_000L;
    private static final long POLL_MS = 20_000L;
    private static final long TOKEN_MARGIN_MS = 60_000L;

    private static final class ChangeEvent {
        final String type;
        final SongRequest record;
        final String id;

        ChangeEvent(String type, SongRequest record, String id) {
            this.type = type;
            this.record = record;
            this.id = id;
        }
    }

    private final SupabaseClient client;
    private final SupabaseRequestDataSource requestSource;
    private final SupabaseSettingsDataSource settingsSource;
    private final SupabaseAuthDataSource authSource;
    private final AuthPreferences authPrefs;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<RequestListener> listeners = new ArrayList<>();
    private final Map<String, SongRequest> pending = new LinkedHashMap<>();
    private final List<ChangeEvent> buffered = new ArrayList<>();

    // connection
    private boolean foreground;
    private boolean fetching;
    private boolean retryScheduled;
    private boolean polling;
    private int attempt;
    private RequestsConnection connection = RequestsConnection.CONNECTING;
    private String detail = "";
    private SupabaseRealtimeClient socket;

    // requests open/closed
    private RequestsAvailability availability = RequestsAvailability.LOADING;
    private boolean availabilityKnown;
    private int availabilityVersion; // bumped by every realtime/own change, to drop stale REST reads

    // admin
    private volatile AuthSession session;
    private boolean admin;

    private final Runnable retryRunnable = () -> {
        retryScheduled = false;
        if (foreground && socket == null) connect();
    };

    private final Runnable pollRunnable = () -> {
        polling = false;
        if (foreground && connection != RequestsConnection.LIVE) {
            refreshAvailability();
            updatePolling();
        }
    };

    private final SupabaseRealtimeClient.Listener realtimeListener =
            new SupabaseRealtimeClient.Listener() {
                @Override
                public void onJoined(SupabaseRealtimeClient source) {
                    main.post(() -> handleJoined(source));
                }

                @Override
                public void onRequestChange(SupabaseRealtimeClient source, String type,
                                            SongRequest record, String oldId) {
                    main.post(() -> handleRequestChange(source, new ChangeEvent(type, record, oldId)));
                }

                @Override
                public void onSettingChange(SupabaseRealtimeClient source, String type,
                                            String key, Boolean value) {
                    main.post(() -> handleSettingChange(source, type, key, value));
                }

                @Override
                public void onDisconnected(SupabaseRealtimeClient source, String reason) {
                    main.post(() -> handleDisconnected(source, reason));
                }
            };

    public RequestRepositoryImpl(SupabaseClient client, SupabaseRequestDataSource requestSource,
                                 SupabaseSettingsDataSource settingsSource,
                                 SupabaseAuthDataSource authSource, AuthPreferences authPrefs) {
        this.client = client;
        this.requestSource = requestSource;
        this.settingsSource = settingsSource;
        this.authSource = authSource;
        this.authPrefs = authPrefs;
        this.session = authPrefs.loadSession();
        this.admin = session != null && authPrefs.isAdmin();
    }

    // ---------- RequestRepository ----------

    @Override
    public void addListener(RequestListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
        listener.onConnectionChanged(connection, detail);
        listener.onAvailabilityChanged(availability);
        listener.onAdminChanged(admin, getAdminEmail());
        listener.onRequestsChanged(snapshot());
    }

    @Override
    public void removeListener(RequestListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void setAppForeground(boolean fg) {
        if (fg == foreground) return;
        foreground = fg;
        if (fg) {
            attempt = 0;
            refreshAvailability(); // read the real value on every return to the app
            verifyAdmin();
        }
        reconcile();
        updatePolling();
    }

    @Override
    public boolean isAdmin() {
        return admin;
    }

    @Override
    public String getAdminEmail() {
        AuthSession s = session;
        return s == null ? "" : s.email;
    }

    // ---------- admin: sign in / out ----------

    @Override
    public void signInAdmin(String email, String password, RepositoryCallback<Boolean> callback) {
        io.execute(() -> {
            try {
                AuthSession s = authSource.signIn(email, password);
                if (!authSource.isAdmin(s.accessToken)) {
                    authSource.logoutQuietly(s.accessToken); // valid login, not an admin: keep nothing
                    main.post(() -> callback.onSuccess(false));
                    return;
                }
                session = s;
                authPrefs.saveSession(s);
                authPrefs.setAdmin(true);
                main.post(() -> {
                    setAdmin(true);
                    callback.onSuccess(true);
                });
            } catch (AdminException e) {
                main.post(() -> callback.onError(e));
            }
        });
    }

    @Override
    public void signOutAdmin() {
        AuthSession old = session;
        clearSession();
        if (old != null) io.execute(() -> authSource.logoutQuietly(old.accessToken));
    }

    private void clearSession() {
        session = null;
        authPrefs.clear();
        setAdmin(false);
    }

    /** On return to the app: confirm the saved session still belongs to an admin. */
    private void verifyAdmin() {
        if (session == null) return;
        io.execute(() -> {
            try {
                boolean isAdmin = authSource.isAdmin(freshToken());
                main.post(() -> {
                    if (session == null) return;
                    if (isAdmin) {
                        authPrefs.setAdmin(true);
                        setAdmin(true);
                    } else {
                        clearSession();
                    }
                });
            } catch (AdminException e) {
                if (e.getReason() == AdminException.Reason.NOT_SIGNED_IN) {
                    main.post(this::clearSession);
                } // offline etc.: keep the saved session
            }
        });
    }

    private void setAdmin(boolean value) {
        admin = value;
        String email = getAdminEmail();
        for (RequestListener l : new ArrayList<>(listeners)) l.onAdminChanged(value, email);
    }

    /** A valid access token, refreshing it first if it is about to expire. Background thread. */
    private synchronized String freshToken() throws AdminException {
        AuthSession s = session;
        if (s == null) throw new AdminException(AdminException.Reason.NOT_SIGNED_IN, "Not signed in");
        if (s.expiresAtMillis - System.currentTimeMillis() > TOKEN_MARGIN_MS) return s.accessToken;
        return refreshSession(s).accessToken;
    }

    private synchronized AuthSession refreshSession(AuthSession s) throws AdminException {
        AuthSession fresh = authSource.refresh(s.refreshToken);
        session = fresh;
        authPrefs.saveSession(fresh);
        return fresh;
    }

    // ---------- admin: open / close requests ----------

    @Override
    public void setRequestsOpen(boolean open, RepositoryCallback<Boolean> callback) {
        if (session == null || !admin) {
            callback.onError(new AdminException(AdminException.Reason.NOT_SIGNED_IN, "Sign in first"));
            return;
        }
        io.execute(() -> {
            try {
                try {
                    settingsSource.setRequestsEnabled(open, freshToken());
                } catch (AdminException e) {
                    AuthSession s = session;
                    if (e.getReason() != AdminException.Reason.NOT_SIGNED_IN || s == null) throw e;
                    // The token was rejected: refresh once and retry.
                    settingsSource.setRequestsEnabled(open, refreshSession(s).accessToken);
                }
                main.post(() -> {
                    availabilityVersion++;
                    setAvailability(open ? RequestsAvailability.OPEN : RequestsAvailability.CLOSED, true);
                    callback.onSuccess(open);
                });
            } catch (AdminException e) {
                main.post(() -> {
                    if (e.getReason() == AdminException.Reason.NOT_SIGNED_IN) clearSession();
                    callback.onError(e);
                });
            }
        });
    }

    // ---------- admin: remove a request from the queue ----------

    @Override
    public void removeRequest(String requestId, RepositoryCallback<Boolean> callback) {
        if (session == null || !admin) {
            callback.onError(new AdminException(AdminException.Reason.NOT_SIGNED_IN, "Sign in first"));
            return;
        }
        io.execute(() -> {
            try {
                try {
                    requestSource.dismiss(requestId, freshToken());
                } catch (AdminException e) {
                    AuthSession s = session;
                    if (e.getReason() != AdminException.Reason.NOT_SIGNED_IN || s == null) throw e;
                    requestSource.dismiss(requestId, refreshSession(s).accessToken); // refresh once, retry
                }
                main.post(() -> {
                    // Drop it now; the realtime UPDATE that follows changes nothing further.
                    if (pending.remove(requestId) != null) notifyRequestsChanged();
                    callback.onSuccess(true);
                });
            } catch (AdminException e) {
                main.post(() -> {
                    if (e.getReason() == AdminException.Reason.NOT_SIGNED_IN) clearSession();
                    callback.onError(e);
                });
            }
        });
    }

    // ---------- requests open/closed: reading ----------

    private void refreshAvailability() {
        final int version = availabilityVersion;
        io.execute(() -> {
            try {
                boolean open = settingsSource.fetchRequestsEnabled();
                main.post(() -> {
                    if (version != availabilityVersion) return; // a newer realtime value won
                    setAvailability(open ? RequestsAvailability.OPEN : RequestsAvailability.CLOSED, true);
                });
            } catch (Exception e) {
                Log.w(TAG, "Could not read requests_enabled", e);
                // First read failing means closed; once a value is known, keep the last one.
                main.post(() -> {
                    if (!availabilityKnown) setAvailability(RequestsAvailability.CLOSED, false);
                });
            }
        });
    }

    private void setAvailability(RequestsAvailability value, boolean known) {
        if (known) availabilityKnown = true;
        if (value == availability) return;
        availability = value;
        for (RequestListener l : new ArrayList<>(listeners)) l.onAvailabilityChanged(value);
    }

    private void handleSettingChange(SupabaseRealtimeClient source, String type, String key,
                                     Boolean value) {
        if (source != socket) return;
        if (!AppConstants.SETTING_REQUESTS_ENABLED.equals(key)) return;
        availabilityVersion++;
        boolean open = !"DELETE".equals(type) && Boolean.TRUE.equals(value); // DELETE means closed
        setAvailability(open ? RequestsAvailability.OPEN : RequestsAvailability.CLOSED, true);
    }

    // ---------- connection management ----------

    private void reconcile() {
        if (foreground) {
            if (socket == null && !retryScheduled) connect();
        } else {
            teardown();
        }
    }

    private void connect() {
        if (!client.isConfigured()) {
            setConnection(RequestsConnection.OFFLINE, "Supabase URL/key are not set");
            return;
        }
        setConnection(RequestsConnection.CONNECTING, "");
        SupabaseRealtimeClient s = new SupabaseRealtimeClient(
                client.getBaseUrl(), client.getApiKey(), realtimeListener);
        socket = s;
        s.connect();
    }

    private void teardown() {
        main.removeCallbacks(retryRunnable);
        retryScheduled = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        fetching = false;
        buffered.clear();
    }

    private void scheduleRetry() {
        if (!foreground || retryScheduled) return;
        long delay = Math.min(MAX_RETRY_MS, 1000L << Math.min(attempt, 4));
        attempt++;
        retryScheduled = true;
        main.postDelayed(retryRunnable, delay);
    }

    /** While the socket is not live, poll the setting every ~20 s so it never goes stale. */
    private void updatePolling() {
        boolean need = foreground && connection != RequestsConnection.LIVE;
        if (need && !polling) {
            polling = true;
            main.postDelayed(pollRunnable, POLL_MS);
        } else if (!need && polling) {
            polling = false;
            main.removeCallbacks(pollRunnable);
        }
    }

    // ---------- realtime events (main thread) ----------

    private void handleJoined(SupabaseRealtimeClient source) {
        if (source != socket) return;
        attempt = 0;
        refreshAvailability(); // resync after (re)connecting
        fetching = true;
        buffered.clear();
        io.execute(() -> {
            try {
                List<SongRequest> snapshot = requestSource.fetchPending();
                main.post(() -> applySnapshot(source, snapshot));
            } catch (Exception e) {
                Log.w(TAG, "Could not load the request queue", e);
                main.post(() -> handleDisconnected(source, "Couldn't load the queue: " + e.getMessage()));
            }
        });
    }

    private void applySnapshot(SupabaseRealtimeClient source, List<SongRequest> snapshot) {
        if (source != socket) return;
        pending.clear();
        for (SongRequest request : snapshot) pending.put(request.getId(), request);
        fetching = false;

        List<SongRequest> arrived = new ArrayList<>();
        for (ChangeEvent event : buffered) { // changes that came in while the queue was loading
            SongRequest added = apply(event);
            if (added != null) arrived.add(added);
        }
        buffered.clear();

        setConnection(RequestsConnection.LIVE, "");
        notifyRequestsChanged();
        for (SongRequest request : arrived) notifyNewRequest(request);
    }

    private void handleRequestChange(SupabaseRealtimeClient source, ChangeEvent event) {
        if (source != socket) return;
        if (fetching) {
            buffered.add(event);
            return;
        }
        SongRequest added = apply(event);
        notifyRequestsChanged();
        if (added != null) notifyNewRequest(added);
    }

    /** Applies one change. Returns the request if it is a brand-new pending one. */
    private SongRequest apply(ChangeEvent event) {
        switch (event.type) {
            case "INSERT":
            case "UPDATE": {
                SongRequest r = event.record;
                if (r == null) return null;
                if (AppConstants.REQUESTS_STATUS_PENDING.equals(r.getStatus())) {
                    boolean isNew = !pending.containsKey(r.getId());
                    pending.put(r.getId(), r);
                    return isNew && "INSERT".equals(event.type) ? r : null;
                }
                pending.remove(r.getId()); // played or dismissed elsewhere
                return null;
            }
            case "DELETE":
                if (event.id != null) pending.remove(event.id);
                return null;
            default:
                return null;
        }
    }

    private void handleDisconnected(SupabaseRealtimeClient source, String reason) {
        if (source != socket) return;
        source.close();
        socket = null;
        fetching = false;
        buffered.clear();
        Log.w(TAG, "Realtime disconnected: " + reason);
        setConnection(RequestsConnection.OFFLINE, reason == null ? "" : reason);
        scheduleRetry();
    }

    // ---------- notifying listeners ----------

    private List<SongRequest> snapshot() {
        List<SongRequest> list = new ArrayList<>(pending.values());
        Collections.sort(list, (a, b) -> {
            int byTime = a.getCreatedAt().compareTo(b.getCreatedAt());
            return byTime != 0 ? byTime : a.getId().compareTo(b.getId());
        });
        return Collections.unmodifiableList(list);
    }

    private void setConnection(RequestsConnection c, String d) {
        connection = c;
        detail = d;
        for (RequestListener l : new ArrayList<>(listeners)) l.onConnectionChanged(c, d);
        updatePolling();
    }

    private void notifyRequestsChanged() {
        List<SongRequest> list = snapshot();
        for (RequestListener l : new ArrayList<>(listeners)) l.onRequestsChanged(list);
    }

    private void notifyNewRequest(SongRequest request) {
        for (RequestListener l : new ArrayList<>(listeners)) l.onNewRequest(request);
    }
}
