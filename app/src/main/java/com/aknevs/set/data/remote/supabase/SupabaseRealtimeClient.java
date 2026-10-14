package com.aknevs.set.data.remote.supabase;

import com.aknevs.set.domain.model.SongRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * One Supabase Realtime connection listening to public.song_requests and public.app_settings
 * (the same "postgres_changes" subscriptions the web app uses). Single use: after it
 * disconnects, create a new instance. Callbacks arrive on background threads.
 */
public class SupabaseRealtimeClient {

    public interface Listener {
        /** The server confirmed the subscription. */
        void onJoined(SupabaseRealtimeClient source);

        /** song_requests change. type is INSERT, UPDATE or DELETE; record is null for DELETE. */
        void onRequestChange(SupabaseRealtimeClient source, String type, SongRequest record, String oldId);

        /** app_settings change. key may be null; value is null for DELETE or non-boolean values. */
        void onSettingChange(SupabaseRealtimeClient source, String type, String key, Boolean value);

        /** The connection ended or was refused. Reported once. */
        void onDisconnected(SupabaseRealtimeClient source, String reason);
    }

    private static final String TOPIC = "realtime:song-requests-android";
    private static final String JOIN_REF = "1";
    private static final long HEARTBEAT_SECONDS = 25;

    // Shared: no read timeout (the socket idles between requests); OkHttp pings detect dead links.
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build();

    private final String baseUrl;
    private final String apiKey;
    private final Listener listener;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger refs = new AtomicInteger(1);
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private volatile WebSocket socket;

    public SupabaseRealtimeClient(String baseUrl, String apiKey, Listener listener) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.listener = listener;
    }

    public void connect() {
        HttpUrl base = HttpUrl.parse(baseUrl + "/realtime/v1/websocket");
        if (base == null) {
            fail("Invalid Supabase URL");
            return;
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("apikey", apiKey)
                .addQueryParameter("vsn", "1.0.0")
                .build();
        socket = HTTP.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                sendJoin(ws);
                scheduler.scheduleAtFixedRate(() -> sendHeartbeat(ws),
                        HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
                fail("Connection closed" + (reason == null || reason.isEmpty() ? "" : ": " + reason));
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                fail("Connection closed");
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                String why = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                if (response != null && (response.code() == 401 || response.code() == 403)) {
                    why = "Realtime rejected the key (HTTP " + response.code() + ")";
                }
                fail(why);
            }
        });
    }

    /** Intentional close: no callback is delivered afterwards. */
    public void close() {
        finished.set(true);
        shutdown();
    }

    // ---------- protocol ----------

    private void sendJoin(WebSocket ws) {
        try {
            JSONObject change = new JSONObject()
                    .put("event", "*")
                    .put("schema", "public")
                    .put("table", "song_requests");
            // No filter on app_settings: Realtime cannot filter DELETE events, and a deleted
            // 'requests_enabled' row must close requests. The key is checked on our side;
            // RLS already limits which settings rows anyone can read.
            JSONObject settings = new JSONObject()
                    .put("event", "*")
                    .put("schema", "public")
                    .put("table", "app_settings");
            JSONObject config = new JSONObject()
                    .put("broadcast", new JSONObject().put("ack", false).put("self", false))
                    .put("presence", new JSONObject().put("key", ""))
                    .put("postgres_changes", new JSONArray().put(change).put(settings))
                    .put("private", false);
            JSONObject payload = new JSONObject()
                    .put("config", config)
                    .put("access_token", apiKey);
            JSONObject message = new JSONObject()
                    .put("topic", TOPIC)
                    .put("event", "phx_join")
                    .put("payload", payload)
                    .put("ref", JOIN_REF)
                    .put("join_ref", JOIN_REF);
            ws.send(message.toString());
        } catch (JSONException e) {
            fail("Could not build join message");
        }
    }

    private void sendHeartbeat(WebSocket ws) {
        try {
            JSONObject message = new JSONObject()
                    .put("topic", "phoenix")
                    .put("event", "heartbeat")
                    .put("payload", new JSONObject())
                    .put("ref", String.valueOf(refs.incrementAndGet()));
            ws.send(message.toString());
        } catch (JSONException ignored) {
            // cannot happen with constant keys
        }
    }

    private void handleMessage(String text) {
        if (finished.get()) return;
        try {
            JSONObject message = new JSONObject(text);
            String event = message.optString("event");
            JSONObject payload = message.optJSONObject("payload");
            if (payload == null) payload = new JSONObject();

            switch (event) {
                case "phx_reply":
                    if (JOIN_REF.equals(message.optString("ref"))) {
                        if ("ok".equals(payload.optString("status"))) {
                            listener.onJoined(this);
                        } else {
                            JSONObject response = payload.optJSONObject("response");
                            String reason = response != null
                                    ? response.optString("reason", payload.toString())
                                    : payload.toString();
                            fail("Realtime refused the subscription: " + reason);
                        }
                    }
                    break;

                case "postgres_changes": {
                    JSONObject data = payload.getJSONObject("data");
                    String type = data.getString("type");
                    String table = data.optString("table");
                    JSONObject record = data.optJSONObject("record");
                    JSONObject old = data.optJSONObject("old_record");

                    if ("app_settings".equals(table)) {
                        JSONObject source = record != null ? record : old;
                        String key = source != null && source.has("key") ? source.optString("key") : null;
                        Boolean value = null;
                        if (record != null && !"DELETE".equals(type)) {
                            Object v = record.opt("value");
                            if (v instanceof Boolean) value = (Boolean) v;
                            else if (v instanceof String) value = Boolean.valueOf((String) v);
                        }
                        listener.onSettingChange(this, type, key, value);
                    } else if ("DELETE".equals(type)) {
                        String id = old == null ? null : old.optString("id", null);
                        if (id != null) listener.onRequestChange(this, type, null, id);
                    } else if (record != null) {
                        listener.onRequestChange(this, type,
                                SupabaseRequestDataSource.parse(record), null);
                    }
                    break;
                }

                case "system":
                    // e.g. the table is not in the realtime publication, or RLS blocks the role
                    if ("error".equals(payload.optString("status"))) {
                        fail(payload.optString("message", "Realtime reported an error"));
                    }
                    break;

                case "phx_error":
                case "phx_close":
                    fail("Realtime channel closed");
                    break;

                default:
                    break; // heartbeat replies, presence, etc.
            }
        } catch (JSONException e) {
            // one malformed frame must not drop the connection
        }
    }

    // ---------- lifecycle ----------

    private void fail(String reason) {
        if (finished.compareAndSet(false, true)) {
            shutdown();
            listener.onDisconnected(this, reason);
        }
    }

    private void shutdown() {
        scheduler.shutdownNow();
        WebSocket s = socket;
        if (s != null) s.close(1000, "bye");
    }
}
