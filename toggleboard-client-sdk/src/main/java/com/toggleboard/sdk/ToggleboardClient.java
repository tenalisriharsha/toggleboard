package com.toggleboard.sdk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A standalone Java client for toggleboard's evaluation data-plane. Every
 * {@link #evaluate} call hits the REST endpoint directly and caches the
 * result; if that network call fails, the last cached value for the same
 * flag/subject pair is served instead (and only if there is none does
 * evaluation throw). A background poller periodically refreshes every
 * cached entry, and — unless disabled — an SSE subscription (with automatic
 * reconnect/backoff) proactively refreshes a flag's cached entries the
 * moment the server reports it changed, so polling is a fallback rather
 * than the primary way results stay fresh.
 *
 * <p>Create with {@link #create(ToggleboardClientConfig)} and {@link #close()}
 * when done (or use it in a try-with-resources block).
 */
public final class ToggleboardClient implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(ToggleboardClient.class.getName());
    private static final long INITIAL_BACKOFF_MILLIS = 1_000L;
    private static final long MAX_BACKOFF_MILLIS = 30_000L;

    private final ToggleboardClientConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<FlagChangeNotification>> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService pollerExecutor;
    private final ExecutorService sseExecutor;
    private volatile boolean closed;

    private ToggleboardClient(ToggleboardClientConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder().connectTimeout(config.httpTimeout()).build();
        this.pollerExecutor = Executors.newSingleThreadScheduledExecutor(r -> daemonThread(r, "toggleboard-sdk-poller"));
        this.sseExecutor = Executors.newSingleThreadExecutor(r -> daemonThread(r, "toggleboard-sdk-sse"));
    }

    /** Creates a client and starts its background poller (and SSE subscriber, unless disabled). */
    public static ToggleboardClient create(ToggleboardClientConfig config) {
        ToggleboardClient client = new ToggleboardClient(config);
        long pollMillis = config.pollInterval().toMillis();
        client.pollerExecutor.scheduleAtFixedRate(client::pollOnce, pollMillis, pollMillis, TimeUnit.MILLISECONDS);
        if (config.streamingEnabled()) {
            client.sseExecutor.submit(client::runSseLoop);
        }
        return client;
    }

    /** Evaluates a flag for a subject with no extra targeting attributes. */
    public EvaluationResult evaluate(String flagKey, String subjectKey) {
        return evaluate(flagKey, subjectKey, Map.of());
    }

    /**
     * Evaluates a flag for a subject, passing {@code attributes} through for
     * targeting. On success the result is cached under this flag/subject pair
     * for the poller and any later push-triggered refresh to keep warm.
     *
     * @throws ToggleboardClientException if the request fails and there is no
     *                                     cached result for this flag/subject pair to fall back to
     */
    public EvaluationResult evaluate(String flagKey, String subjectKey, Map<String, String> attributes) {
        CacheKey key = new CacheKey(flagKey, subjectKey);
        try {
            EvaluationResult result = fetch(flagKey, subjectKey, attributes);
            cache.put(key, new CacheEntry(result, Map.copyOf(attributes)));
            return result;
        } catch (IOException e) {
            return fallbackOrThrow(key, subjectKey, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallbackOrThrow(key, subjectKey, e);
        }
    }

    /** Registers a listener invoked (on the SSE thread) whenever a push notification arrives. */
    public void onFlagChange(Consumer<FlagChangeNotification> listener) {
        listeners.add(listener);
    }

    /** Stops the poller and SSE subscriber and releases the underlying HTTP client. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pollerExecutor.shutdownNow();
        sseExecutor.shutdownNow();
        httpClient.shutdownNow();
    }

    private EvaluationResult fallbackOrThrow(CacheKey key, String subjectKey, Exception cause) {
        CacheEntry cached = cache.get(key);
        if (cached != null) {
            LOG.log(Level.WARNING, () -> "Evaluation of '" + key.flagKey() + "' failed; serving cached value: " + cause.getMessage());
            return cached.result();
        }
        throw new ToggleboardClientException(
                "Failed to evaluate flag '" + key.flagKey() + "' for subject '" + subjectKey + "' and no cached value is available", cause);
    }

    private void pollOnce() {
        for (Map.Entry<CacheKey, CacheEntry> entry : cache.entrySet()) {
            CacheKey key = entry.getKey();
            try {
                EvaluationResult result = fetch(key.flagKey(), key.subjectKey(), entry.getValue().attributes());
                cache.put(key, new CacheEntry(result, entry.getValue().attributes()));
            } catch (IOException e) {
                LOG.log(Level.WARNING, () -> "Background poll of '" + key.flagKey() + "' failed; keeping previous value: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private EvaluationResult fetch(String flagKey, String subjectKey, Map<String, String> attributes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(evaluateUri(flagKey, subjectKey, attributes))
                .header("X-API-Key", config.apiKey())
                .timeout(config.httpTimeout())
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Evaluation request for '" + flagKey + "' failed with status " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), EvaluationResult.class);
    }

    private void runSseLoop() {
        long backoffMillis = INITIAL_BACKOFF_MILLIS;
        while (!closed) {
            try {
                HttpRequest request = HttpRequest.newBuilder(streamUri())
                        .header("X-API-Key", config.apiKey())
                        .header("Accept", "text/event-stream")
                        .GET()
                        .build();
                HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
                if (response.statusCode() != 200) {
                    throw new IOException("Stream subscription failed with status " + response.statusCode());
                }
                backoffMillis = INITIAL_BACKOFF_MILLIS;
                consumeLines(response.body());
                if (closed) {
                    return;
                }
            } catch (IOException e) {
                if (closed) {
                    return;
                }
                long nextRetryMillis = backoffMillis;
                LOG.log(Level.INFO, () -> "SSE stream disconnected, reconnecting in " + nextRetryMillis + "ms: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (closed) {
                return;
            }
            sleepQuietly(backoffMillis);
            backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
        }
    }

    private void consumeLines(Stream<String> lines) {
        StringBuilder data = new StringBuilder();
        Iterator<String> iterator = lines.iterator();
        while (!closed && iterator.hasNext()) {
            String line = iterator.next();
            if (line.isEmpty()) {
                if (data.length() > 0) {
                    dispatchEvent(data.toString());
                    data.setLength(0);
                }
                continue;
            }
            if (line.startsWith(":") || !line.startsWith("data:")) {
                continue;
            }
            String value = line.substring("data:".length());
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            if (data.length() > 0) {
                data.append('\n');
            }
            data.append(value);
        }
    }

    private void dispatchEvent(String json) {
        RawFlagChangeEvent raw;
        try {
            raw = objectMapper.readValue(json, RawFlagChangeEvent.class);
        } catch (IOException e) {
            LOG.log(Level.WARNING, () -> "Failed to parse SSE event: " + e.getMessage());
            return;
        }
        refreshCachedSubjectsFor(raw.flagKey());
        FlagChangeNotification notification = new FlagChangeNotification(
                raw.environmentKey(), raw.flagKey(), raw.action(), Instant.parse(raw.timestamp()));
        for (Consumer<FlagChangeNotification> listener : listeners) {
            try {
                listener.accept(notification);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, () -> "Flag-change listener threw: " + e.getMessage());
            }
        }
    }

    private void refreshCachedSubjectsFor(String flagKey) {
        for (Map.Entry<CacheKey, CacheEntry> entry : cache.entrySet()) {
            if (!entry.getKey().flagKey().equals(flagKey)) {
                continue;
            }
            try {
                EvaluationResult result = fetch(flagKey, entry.getKey().subjectKey(), entry.getValue().attributes());
                cache.put(entry.getKey(), new CacheEntry(result, entry.getValue().attributes()));
            } catch (IOException e) {
                LOG.log(Level.WARNING, () -> "Failed to refresh '" + flagKey + "' after a push notification: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private URI evaluateUri(String flagKey, String subjectKey, Map<String, String> attributes) {
        StringBuilder query = new StringBuilder("subjectKey=").append(encode(subjectKey));
        attributes.forEach((name, value) -> query.append('&').append(encode(name)).append('=').append(encode(value)));
        return URI.create(config.baseUrl() + "/api/v1/environments/" + encode(config.environmentKey())
                + "/flags/" + encode(flagKey) + "/evaluate?" + query);
    }

    private URI streamUri() {
        return URI.create(config.baseUrl() + "/api/v1/environments/" + encode(config.environmentKey()) + "/stream");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record CacheKey(String flagKey, String subjectKey) {
    }

    private record CacheEntry(EvaluationResult result, Map<String, String> attributes) {
    }

    private record RawFlagChangeEvent(String environmentKey, String flagKey, String action, String timestamp) {
    }
}
