package com.aknevs.set.data.remote.supabase;

import com.aknevs.set.domain.model.SyncException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Thin OkHttp wrapper for the Supabase REST API. Blocking: call from a background thread. */
public class SupabaseClient {

    /** Response body plus the total row count from Content-Range (-1 if unknown). */
    public static final class Page {
        public final String body;
        public final int totalCount;

        Page(String body, int totalCount) {
            this.body = body;
            this.totalCount = totalCount;
        }
    }

    private final OkHttpClient http;
    private final String baseUrl;
    private final String apiKey;

    public SupabaseClient(String url, String key) {
        this.baseUrl = url == null ? "" : url.trim().replaceAll("/+$", "");
        this.apiKey = key == null ? "" : key.trim();
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public String getBaseUrl() { return baseUrl; }

    public String getApiKey() { return apiKey; }

    public boolean isConfigured() {
        return !baseUrl.isEmpty() && !apiKey.isEmpty()
                && !baseUrl.contains("YOUR-PROJECT")
                && !apiKey.contains("YOUR-PUBLISHABLE");
    }

    public Page get(String path, Map<String, String> query) throws SyncException {
        if (!isConfigured()) {
            throw new SyncException(SyncException.Reason.NOT_CONFIGURED,
                    "SUPABASE_URL / SUPABASE_KEY are not set");
        }
        HttpUrl base = HttpUrl.parse(baseUrl + path);
        if (base == null) {
            throw new SyncException(SyncException.Reason.NOT_CONFIGURED,
                    "SUPABASE_URL is not a valid URL");
        }
        HttpUrl.Builder url = base.newBuilder();
        for (Map.Entry<String, String> e : query.entrySet()) {
            url.addQueryParameter(e.getKey(), e.getValue());
        }

        Request.Builder request = new Request.Builder()
                .url(url.build())
                .header("apikey", apiKey)
                .header("Accept", "application/json")
                .header("Prefer", "count=exact")
                .get();
        // New-style publishable keys are not JWTs and go in "apikey" only.
        // Legacy anon keys are JWTs and are also sent as the bearer token.
        if (apiKey.startsWith("eyJ")) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        try (Response response = http.newCall(request.build()).execute()) {
            int code = response.code();
            if (code == 401 || code == 403) {
                throw new SyncException(SyncException.Reason.AUTH, "HTTP " + code);
            }
            if (!response.isSuccessful()) {
                throw new SyncException(SyncException.Reason.SERVER, "HTTP " + code);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new SyncException(SyncException.Reason.INVALID_DATA, "Empty body");
            }
            return new Page(body.string(), parseTotal(response.header("Content-Range")));
        } catch (IOException e) {
            throw new SyncException(SyncException.Reason.NETWORK, String.valueOf(e.getMessage()), e);
        }
    }

    /** A raw HTTP outcome. Unlike get(), non-2xx codes are returned, not thrown. */
    public static final class Result {
        public final int code;
        public final String body;

        Result(int code, String body) {
            this.code = code;
            this.body = body;
        }

        public boolean isSuccessful() { return code >= 200 && code < 300; }
    }

    /**
     * Any method, optionally with a JSON body and the signed-in user's token. Blocking.
     * Only configuration problems and network failures throw.
     */
    public Result send(String method, String path, Map<String, String> query, String jsonBody,
                       String bearerToken, Map<String, String> headers) throws SyncException {
        if (!isConfigured()) {
            throw new SyncException(SyncException.Reason.NOT_CONFIGURED,
                    "SUPABASE_URL / SUPABASE_KEY are not set");
        }
        HttpUrl base = HttpUrl.parse(baseUrl + path);
        if (base == null) {
            throw new SyncException(SyncException.Reason.NOT_CONFIGURED, "SUPABASE_URL is not valid");
        }
        HttpUrl.Builder url = base.newBuilder();
        if (query != null) {
            for (Map.Entry<String, String> e : query.entrySet()) {
                url.addQueryParameter(e.getKey(), e.getValue());
            }
        }
        Request.Builder request = new Request.Builder()
                .url(url.build())
                .header("apikey", apiKey)
                .header("Accept", "application/json");
        String bearer = bearerToken != null ? bearerToken : (apiKey.startsWith("eyJ") ? apiKey : null);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                request.header(e.getKey(), e.getValue());
            }
        }
        if (jsonBody != null) {
            request.method(method, RequestBody.create(jsonBody,
                    MediaType.parse("application/json; charset=utf-8")));
        } else if ("GET".equals(method)) {
            request.get();
        } else {
            request.method(method, new FormBody.Builder().build());
        }
        try (Response response = http.newCall(request.build()).execute()) {
            ResponseBody body = response.body();
            return new Result(response.code(), body == null ? "" : body.string());
        } catch (IOException e) {
            throw new SyncException(SyncException.Reason.NETWORK, String.valueOf(e.getMessage()), e);
        }
    }

    ///** "0-999/2345" -> 2345, "*/0" -> 0, anything else -> -1. */
    private static int parseTotal(String contentRange) {
        if (contentRange == null) return -1;
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0) return -1;
        try {
            return Integer.parseInt(contentRange.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
