package com.toggleboard.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

/**
 * Wires the {@link DynamoDbClient} used by the {@code dynamodb} profile's
 * repositories. {@code toggleboard.dynamodb.endpoint} points it at a local
 * DynamoDB Local instance; leaving it unset falls back to the default AWS
 * endpoint and credential chain for a real deployment.
 */
@Configuration
@Profile("dynamodb")
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(
            @Value("${toggleboard.dynamodb.endpoint:}") String endpoint,
            @Value("${toggleboard.dynamodb.region:us-east-1}") String region) {

        var builder = DynamoDbClient.builder().region(Region.of(region));

        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(localCredentials());
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    private AwsCredentialsProvider localCredentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"));
    }
}
