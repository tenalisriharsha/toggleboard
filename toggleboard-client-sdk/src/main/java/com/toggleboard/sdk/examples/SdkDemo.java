package com.toggleboard.sdk.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.sdk.EvaluationResult;
import com.toggleboard.sdk.ToggleboardClient;
import com.toggleboard.sdk.ToggleboardClientConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A runnable walkthrough of {@link ToggleboardClient} against a real,
 * already-running toggleboard-api server: create an environment and a flag
 * through the admin API, evaluate it through the SDK, then watch a live
 * SSE push notification arrive the moment an admin flips the flag's kill
 * switch, and see the cache refresh itself in response — no explicit poll
 * needed. Run with:
 *
 * <pre>
 * mvn -pl toggleboard-client-sdk exec:java -Dexec.mainClass=com.toggleboard.sdk.examples.SdkDemo \
 *     -Dexec.args="http://localhost:8099"
 * </pre>
 */
public final class SdkDemo {

    private SdkDemo() {
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        String environmentKey = "sdk-demo-" + UUID.randomUUID().toString().substring(0, 8);
        String flagKey = "welcome-banner";

        HttpClient admin = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();

        System.out.println("# Setting up a demo environment and flag through the admin API");
        String apiKey = createEnvironment(admin, objectMapper, baseUrl, environmentKey);
        createFlag(admin, baseUrl, environmentKey, flagKey);
        System.out.println();

        System.out.println("# Creating a ToggleboardClient (polling every 30s, SSE push enabled)");
        ToggleboardClient client = ToggleboardClient.create(ToggleboardClientConfig.builder()
                .baseUrl(baseUrl)
                .environmentKey(environmentKey)
                .apiKey(apiKey)
                .streamingEnabled(true)
                .pollInterval(Duration.ofSeconds(30))
                .build());

        EvaluationResult initial = client.evaluate(flagKey, "user-482");
        System.out.println("client.evaluate(\"" + flagKey + "\", \"user-482\") -> "
                + "enabled=" + initial.enabled() + ", reason=" + initial.reason());
        System.out.println();

        CountDownLatch pushReceived = new CountDownLatch(1);
        client.onFlagChange(notification -> {
            System.out.println("[push] received flag-change event: flagKey=" + notification.flagKey()
                    + ", action=" + notification.action() + ", at=" + notification.timestamp());
            pushReceived.countDown();
        });

        Thread.sleep(500); // let the SSE subscription connect before the server-side change happens
        System.out.println("# An admin flips the kill switch off via the admin API");
        disableFlag(admin, baseUrl, environmentKey, flagKey);

        boolean arrived = pushReceived.await(8, TimeUnit.SECONDS);
        System.out.println();
        if (!arrived) {
            System.out.println("No push notification arrived within the timeout.");
        } else {
            EvaluationResult afterPush = client.evaluate(flagKey, "user-482");
            System.out.println("client.evaluate(\"" + flagKey + "\", \"user-482\") -> "
                    + "enabled=" + afterPush.enabled() + ", reason=" + afterPush.reason()
                    + "  (refreshed automatically by the push notification, no explicit poll needed)");
        }

        client.close();
        System.exit(arrived ? 0 : 1);
    }

    private static String createEnvironment(HttpClient http, ObjectMapper objectMapper, String baseUrl,
                                              String environmentKey) throws Exception {
        String body = "{\"key\":\"" + environmentKey + "\",\"name\":\"SDK Demo\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/environments"))
                .header("Content-Type", "application/json").header("X-Role", "admin")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());
        System.out.println("Created environment '" + environmentKey + "' with API key " + json.get("apiKey").asText());
        return json.get("apiKey").asText();
    }

    private static void createFlag(HttpClient http, String baseUrl, String environmentKey, String flagKey) throws Exception {
        String body = "{\"key\":\"" + flagKey + "\",\"name\":\"Welcome Banner\",\"type\":\"BOOLEAN\","
                + "\"enabled\":true,\"rolloutPercentage\":100}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/environments/" + environmentKey + "/flags"))
                .header("Content-Type", "application/json").header("X-Role", "admin")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        http.send(request, HttpResponse.BodyHandlers.discarding());
        System.out.println("Created flag '" + flagKey + "' (enabled, 100% rollout)");
    }

    private static void disableFlag(HttpClient http, String baseUrl, String environmentKey, String flagKey) throws Exception {
        String body = "{\"name\":\"Welcome Banner\",\"enabled\":false,\"rolloutPercentage\":100}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/environments/" + environmentKey + "/flags/" + flagKey))
                .header("Content-Type", "application/json").header("X-Role", "admin")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        http.send(request, HttpResponse.BodyHandlers.discarding());
    }
}
