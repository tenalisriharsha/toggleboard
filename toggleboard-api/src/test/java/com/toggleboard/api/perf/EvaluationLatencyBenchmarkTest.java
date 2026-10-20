package com.toggleboard.api.perf;

import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.domain.FlagEvaluator;
import com.toggleboard.api.domain.FlagType;
import com.toggleboard.api.domain.Segment;
import com.toggleboard.api.domain.StableHashRolloutEngine;
import com.toggleboard.api.domain.TargetingOperator;
import com.toggleboard.api.domain.TargetingRule;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A hand-rolled p50/p99 latency benchmark, not a correctness test: it measures
 * {@link FlagEvaluator#evaluate} directly (pure in-process computation, no
 * HTTP) and the full REST round trip for the same evaluation over a real,
 * randomly-ported embedded server. The asserted ceilings are generous
 * (meant to catch a real regression, e.g. an accidental O(n^2) or a blocking
 * call creeping into the hot path) rather than tight performance gates, so
 * this stays non-flaky on a shared/loaded machine.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EvaluationLatencyBenchmarkTest {

    @LocalServerPort
    private int port;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private FlagService flagService;

    @Test
    void directEvaluatorLatency() {
        FlagEvaluator evaluator = new FlagEvaluator(new StableHashRolloutEngine());
        Instant now = Instant.now();
        Flag flag = new Flag("bench-id", "bench-flag", "bench-env", "Bench Flag", "desc", FlagType.BOOLEAN,
                true, 50, List.of(), null,
                List.of(new TargetingRule("plan", TargetingOperator.EQUALS, List.of("enterprise"), null)),
                List.of(), now, now);
        List<Segment> segments = List.of();
        Map<String, String> attributes = Map.of("plan", "pro");

        int warmupIterations = 20_000;
        int measuredIterations = 50_000;
        for (int i = 0; i < warmupIterations; i++) {
            evaluator.evaluate(flag, "user-" + i, attributes, segments);
        }

        long[] samplesNanos = new long[measuredIterations];
        for (int i = 0; i < measuredIterations; i++) {
            long start = System.nanoTime();
            evaluator.evaluate(flag, "user-" + i, attributes, segments);
            samplesNanos[i] = System.nanoTime() - start;
        }

        printAndAssert("FlagEvaluator.evaluate() [in-process, no HTTP]", samplesNanos, 5_000_000L);
    }

    @Test
    void restRoundTripLatency() throws Exception {
        String environmentKey = "bench-" + UUID.randomUUID().toString().substring(0, 8);
        String apiKey = environmentService.create(environmentKey, "Benchmark Env", "bench").apiKey();
        flagService.create(environmentKey, "bench-flag", "Bench Flag", "desc", FlagType.BOOLEAN,
                true, 100, List.of(), null, List.of(), List.of(), "bench");

        HttpClient httpClient = HttpClient.newHttpClient();
        URI uri = URI.create("http://localhost:" + port + "/api/v1/environments/" + environmentKey
                + "/flags/bench-flag/evaluate?subjectKey=user-1");
        HttpRequest request = HttpRequest.newBuilder(uri).header("X-API-Key", apiKey).GET().build();

        int warmupIterations = 20;
        int measuredIterations = 300;
        for (int i = 0; i < warmupIterations; i++) {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        }

        long[] samplesNanos = new long[measuredIterations];
        for (int i = 0; i < measuredIterations; i++) {
            long start = System.nanoTime();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            samplesNanos[i] = System.nanoTime() - start;
        }

        printAndAssert("GET .../evaluate [full REST round trip, localhost]", samplesNanos, 500_000_000L);
    }

    private void printAndAssert(String label, long[] samplesNanos, long maxP99Nanos) {
        long[] sorted = samplesNanos.clone();
        Arrays.sort(sorted);
        long p50 = percentile(sorted, 50);
        long p95 = percentile(sorted, 95);
        long p99 = percentile(sorted, 99);
        long max = sorted[sorted.length - 1];

        System.out.println();
        System.out.println("=== Evaluation latency: " + label + " ===");
        System.out.printf("  samples: %d%n", sorted.length);
        System.out.printf("  p50: %.3f ms%n", p50 / 1_000_000.0);
        System.out.printf("  p95: %.3f ms%n", p95 / 1_000_000.0);
        System.out.printf("  p99: %.3f ms%n", p99 / 1_000_000.0);
        System.out.printf("  max: %.3f ms%n", max / 1_000_000.0);

        assertThat(p99).isLessThan(maxP99Nanos);
    }

    private long percentile(long[] sortedNanos, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sortedNanos.length) - 1;
        return sortedNanos[Math.max(0, Math.min(index, sortedNanos.length - 1))];
    }
}
