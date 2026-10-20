package com.toggleboard.sdk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link ToggleboardClient} against a real (but in-process,
 * JDK-only) HTTP server, so these tests need no Spring context and no
 * running toggleboard-api — the module stays standalone for its own tests
 * the same way it does at runtime.
 */
class ToggleboardClientTest {

    private HttpServer server;
    private ToggleboardClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void evaluateReturnsTheServersResult() throws Exception {
        server = startServer();
        server.createContext("/api/v1/environments/test-env/flags/flag-a/evaluate", exchange ->
                writeJson(exchange, 200, "{\"flagKey\":\"flag-a\",\"enabled\":true,\"variantKey\":null,\"value\":\"true\",\"reason\":\"ROLLOUT\"}"));
        client = newClient(noStreaming().pollInterval(Duration.ofMinutes(10)));

        EvaluationResult result = client.evaluate("flag-a", "user-1");

        assertThat(result.flagKey()).isEqualTo("flag-a");
        assertThat(result.enabled()).isTrue();
        assertThat(result.reason()).isEqualTo("ROLLOUT");
    }

    @Test
    void evaluateSendsTheApiKeyHeaderAndSubjectAndAttributesAsQueryParams() throws Exception {
        server = startServer();
        AtomicReference<String> seenApiKey = new AtomicReference<>();
        AtomicReference<String> seenQuery = new AtomicReference<>();
        server.createContext("/api/v1/environments/test-env/flags/flag-a/evaluate", exchange -> {
            seenApiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            seenQuery.set(exchange.getRequestURI().getQuery());
            writeJson(exchange, 200, "{\"flagKey\":\"flag-a\",\"enabled\":true,\"variantKey\":null,\"value\":\"true\",\"reason\":\"TARGETING_MATCH\"}");
        });
        client = newClient(noStreaming().apiKey("secret-key").pollInterval(Duration.ofMinutes(10)));

        client.evaluate("flag-a", "user-1", Map.of("country", "US"));

        assertThat(seenApiKey.get()).isEqualTo("secret-key");
        assertThat(seenQuery.get()).contains("subjectKey=user-1").contains("country=US");
    }

    @Test
    void fallsBackToTheCachedValueWhenARequestFails() throws Exception {
        server = startServer();
        AtomicInteger requestCount = new AtomicInteger();
        server.createContext("/api/v1/environments/test-env/flags/flag-a/evaluate", exchange -> {
            if (requestCount.incrementAndGet() == 1) {
                writeJson(exchange, 200, "{\"flagKey\":\"flag-a\",\"enabled\":true,\"variantKey\":null,\"value\":\"true\",\"reason\":\"ROLLOUT\"}");
            } else {
                writeJson(exchange, 500, "{\"message\":\"boom\"}");
            }
        });
        client = newClient(noStreaming().pollInterval(Duration.ofMinutes(10)));

        EvaluationResult first = client.evaluate("flag-a", "user-1");
        EvaluationResult second = client.evaluate("flag-a", "user-1");

        assertThat(first.enabled()).isTrue();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void throwsWhenARequestFailsAndNoCachedValueExists() throws Exception {
        server = startServer();
        server.createContext("/api/v1/environments/test-env/flags/flag-a/evaluate", exchange -> writeJson(exchange, 500, "{\"message\":\"boom\"}"));
        client = newClient(noStreaming().pollInterval(Duration.ofMinutes(10)));

        assertThatThrownBy(() -> client.evaluate("flag-a", "user-1"))
                .isInstanceOf(ToggleboardClientException.class);
    }

    @Test
    void backgroundPollerRefetchesCachedFlagsOnAnInterval() throws Exception {
        server = startServer();
        AtomicInteger requestCount = new AtomicInteger();
        server.createContext("/api/v1/environments/test-env/flags/flag-a/evaluate", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 200, "{\"flagKey\":\"flag-a\",\"enabled\":true,\"variantKey\":null,\"value\":\"true\",\"reason\":\"ROLLOUT\"}");
        });
        client = newClient(noStreaming().pollInterval(Duration.ofMillis(150)));

        client.evaluate("flag-a", "user-1");
        Thread.sleep(600);

        assertThat(requestCount.get()).isGreaterThan(1);
    }

    @Test
    void ssePushNotificationRefreshesTheCacheAndNotifiesListeners() throws Exception {
        server = startServer();
        AtomicInteger evaluateRequests = new AtomicInteger();
        server.createContext("/api/v1/environments/test-env/flags/flag-a/evaluate", exchange -> {
            evaluateRequests.incrementAndGet();
            writeJson(exchange, 200, "{\"flagKey\":\"flag-a\",\"enabled\":true,\"variantKey\":null,\"value\":\"true\",\"reason\":\"ROLLOUT\"}");
        });
        AtomicInteger streamAttempts = new AtomicInteger();
        server.createContext("/api/v1/environments/test-env/stream", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                if (streamAttempts.incrementAndGet() == 1) {
                    // Close with no data, forcing the client's reconnect/backoff path.
                    return;
                }
                String event = "data: {\"environmentKey\":\"test-env\",\"flagKey\":\"flag-a\",\"action\":\"UPDATED\","
                        + "\"timestamp\":\"" + Instant.now() + "\"}\n\n";
                body.write(event.getBytes(StandardCharsets.UTF_8));
                body.flush();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        CountDownLatch notified = new CountDownLatch(1);
        AtomicReference<FlagChangeNotification> received = new AtomicReference<>();
        client = newClient(ToggleboardClientConfig.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .environmentKey("test-env").apiKey("key-1")
                .streamingEnabled(true).pollInterval(Duration.ofMinutes(10)));
        client.evaluate("flag-a", "user-1");
        client.onFlagChange(notification -> {
            received.set(notification);
            notified.countDown();
        });

        boolean arrived = notified.await(8, TimeUnit.SECONDS);

        assertThat(arrived).isTrue();
        assertThat(received.get().flagKey()).isEqualTo("flag-a");
        assertThat(received.get().action()).isEqualTo("UPDATED");
        assertThat(streamAttempts.get()).isGreaterThanOrEqualTo(2);
        assertThat(evaluateRequests.get()).isGreaterThanOrEqualTo(2);
    }

    private HttpServer startServer() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
        return httpServer;
    }

    private ToggleboardClientConfig.Builder noStreaming() {
        return ToggleboardClientConfig.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .environmentKey("test-env").apiKey("key-1").streamingEnabled(false);
    }

    private ToggleboardClient newClient(ToggleboardClientConfig.Builder builder) {
        return ToggleboardClient.create(builder.build());
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}
