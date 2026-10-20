package com.toggleboard.api.sdk;

import com.toggleboard.api.domain.FlagType;
import com.toggleboard.api.service.EnvironmentService;
import com.toggleboard.api.service.FlagService;
import com.toggleboard.sdk.EvaluationResult;
import com.toggleboard.sdk.FlagChangeNotification;
import com.toggleboard.sdk.ToggleboardClient;
import com.toggleboard.sdk.ToggleboardClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the standalone {@code toggleboard-client-sdk} module actually
 * interoperates with the real, running {@code toggleboard-api} server —
 * a real HTTP evaluation round trip and a real SSE push notification, not
 * MockMvc. The SDK module itself stays dependency-free at runtime; this
 * test lives here (where spring-boot-starter-test already is) rather than
 * pulling Spring into the SDK module just to prove this.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientSdkIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private FlagService flagService;

    private String environmentKey;
    private String apiKey;
    private ToggleboardClient client;

    @BeforeEach
    void setUp() {
        environmentKey = "env-" + UUID.randomUUID().toString().substring(0, 8);
        apiKey = environmentService.create(environmentKey, "SDK Test Env", "tester").apiKey();
        flagService.create(environmentKey, "welcome-banner", "Welcome Banner", "desc", FlagType.BOOLEAN,
                true, 100, List.of(), null, List.of(), List.of(), "tester");
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void evaluatesAFlagOverTheRealHttpServer() {
        client = ToggleboardClient.create(ToggleboardClientConfig.builder()
                .baseUrl("http://localhost:" + port)
                .environmentKey(environmentKey)
                .apiKey(apiKey)
                .streamingEnabled(false)
                .pollInterval(Duration.ofMinutes(10))
                .build());

        EvaluationResult result = client.evaluate("welcome-banner", "user-1");

        assertThat(result.flagKey()).isEqualTo("welcome-banner");
        assertThat(result.enabled()).isTrue();
        assertThat(result.reason()).isEqualTo("ROLLOUT");
    }

    @Test
    void receivesAPushNotificationWhenAnAdminUpdatesTheFlag() throws Exception {
        client = ToggleboardClient.create(ToggleboardClientConfig.builder()
                .baseUrl("http://localhost:" + port)
                .environmentKey(environmentKey)
                .apiKey(apiKey)
                .streamingEnabled(true)
                .pollInterval(Duration.ofMinutes(10))
                .build());
        client.evaluate("welcome-banner", "user-1");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FlagChangeNotification> received = new AtomicReference<>();
        client.onFlagChange(notification -> {
            received.set(notification);
            latch.countDown();
        });
        Thread.sleep(300); // let the SSE subscription connect before the change happens

        flagService.update(environmentKey, "welcome-banner", "Welcome Banner", "desc", false, 100,
                List.of(), null, List.of(), List.of(), "tester");

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().flagKey()).isEqualTo("welcome-banner");
        assertThat(received.get().action()).isEqualTo("UPDATED");
    }
}
