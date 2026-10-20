package com.toggleboard.api.perf;

import com.toggleboard.api.domain.FlagType;
import com.toggleboard.api.service.EnvironmentService;
import com.toggleboard.api.service.FlagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A simple concurrent load test against the evaluation endpoint: many
 * threads hammering {@code /evaluate} at once over a real, randomly-ported
 * embedded server. No Gatling here — a fixed thread pool firing real HTTP
 * requests at the real app is enough to prove the data-plane holds up under
 * concurrency without a heavier dependency.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EvaluationLoadTest {

    @LocalServerPort
    private int port;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private FlagService flagService;

    @Test
    void concurrentEvaluationRequestsAllSucceed() throws Exception {
        String environmentKey = "load-" + UUID.randomUUID().toString().substring(0, 8);
        String apiKey = environmentService.create(environmentKey, "Load Test Env", "load-test").apiKey();
        flagService.create(environmentKey, "load-flag", "Load Flag", "desc", FlagType.BOOLEAN,
                true, 100, List.of(), null, List.of(), List.of(), "load-test");

        int threadCount = 20;
        int requestsPerThread = 50;
        int totalRequests = threadCount * requestsPerThread;

        HttpClient httpClient = HttpClient.newHttpClient();
        URI uri = URI.create("http://localhost:" + port + "/api/v1/environments/" + environmentKey
                + "/flags/load-flag/evaluate?subjectKey=user-1");
        HttpRequest request = HttpRequest.newBuilder(uri).header("X-API-Key", apiKey).GET().build();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(totalRequests);

        long startNanos = System.nanoTime();
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                for (int i = 0; i < requestsPerThread; i++) {
                    try {
                        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                        if (response.statusCode() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean completedInTime = latch.await(60, TimeUnit.SECONDS);
        long elapsedNanos = System.nanoTime() - startNanos;
        executor.shutdown();

        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double throughput = totalRequests / elapsedSeconds;

        System.out.println();
        System.out.println("=== Load test: concurrent GET .../evaluate ===");
        System.out.printf("  threads: %d, requests per thread: %d, total requests: %d%n", threadCount, requestsPerThread, totalRequests);
        System.out.printf("  elapsed: %.3f s%n", elapsedSeconds);
        System.out.printf("  throughput: %.1f requests/sec%n", throughput);
        System.out.printf("  succeeded: %d, failed: %d%n", successCount.get(), failureCount.get());

        assertThat(completedInTime).isTrue();
        assertThat(failureCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(totalRequests);
    }
}
