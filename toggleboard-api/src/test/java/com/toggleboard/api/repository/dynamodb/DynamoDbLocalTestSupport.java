package com.toggleboard.api.repository.dynamodb;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;

/**
 * Starts a single in-memory, embedded DynamoDB Local instance (no Docker)
 * for the whole test JVM and creates every table this service needs against
 * it, reusing the same {@link DynamoDbTableInitializer} that production
 * deployments run on startup. Shared across all {@code DynamoDb*Test}
 * classes to avoid paying the startup cost repeatedly; each test uses
 * randomized keys so leftover rows from other tests never collide.
 */
final class DynamoDbLocalTestSupport {

    static final DynamoDbClient CLIENT;

    static {
        try {
            int port = freePort();
            DynamoDBProxyServer server = ServerRunner.createServerFromCommandLineArgs(
                    new String[]{"-inMemory", "-port", String.valueOf(port)});
            server.start();

            CLIENT = DynamoDbClient.builder()
                    .endpointOverride(URI.create("http://localhost:" + port))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                    .build();

            new DynamoDbTableInitializer(CLIENT).createTablesIfMissing();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start embedded DynamoDB Local for tests", e);
        }
    }

    private DynamoDbLocalTestSupport() {
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
