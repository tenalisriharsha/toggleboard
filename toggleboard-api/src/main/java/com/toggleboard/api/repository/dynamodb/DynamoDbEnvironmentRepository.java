package com.toggleboard.api.repository.dynamodb;

import com.toggleboard.api.domain.Environment;
import com.toggleboard.api.repository.EnvironmentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB-backed {@link EnvironmentRepository}. Drop-in replacement for
 * {@code InMemoryEnvironmentRepository}: the service layer that depends on
 * the {@link EnvironmentRepository} interface never changes.
 */
@Repository
@Profile("dynamodb")
public class DynamoDbEnvironmentRepository implements EnvironmentRepository {

    private final DynamoDbClient client;

    public DynamoDbEnvironmentRepository(DynamoDbClient client) {
        this.client = client;
    }

    @Override
    public Environment save(Environment environment) {
        client.putItem(b -> b.tableName(TableNames.ENVIRONMENTS).item(toItem(environment)));
        return environment;
    }

    @Override
    public Optional<Environment> findByKey(String key) {
        Map<String, AttributeValue> item = client.getItem(b -> b
                .tableName(TableNames.ENVIRONMENTS)
                .key(Map.of("key", AttributeValue.fromS(key)))).item();
        return (item == null || item.isEmpty()) ? Optional.empty() : Optional.of(fromItem(item));
    }

    @Override
    public Optional<Environment> findByApiKey(String apiKey) {
        QueryRequest query = QueryRequest.builder()
                .tableName(TableNames.ENVIRONMENTS)
                .indexName(TableNames.API_KEY_INDEX)
                .keyConditionExpression("apiKey = :apiKey")
                .expressionAttributeValues(Map.of(":apiKey", AttributeValue.fromS(apiKey)))
                .limit(1)
                .build();
        List<Map<String, AttributeValue>> items = client.query(query).items();
        return items.isEmpty() ? Optional.empty() : Optional.of(fromItem(items.get(0)));
    }

    @Override
    public List<Environment> findAll() {
        return client.scan(b -> b.tableName(TableNames.ENVIRONMENTS)).items().stream()
                .map(this::fromItem)
                .toList();
    }

    @Override
    public boolean existsByKey(String key) {
        return findByKey(key).isPresent();
    }

    private Map<String, AttributeValue> toItem(Environment environment) {
        return Map.of(
                "key", AttributeValue.fromS(environment.key()),
                "name", AttributeValue.fromS(environment.name()),
                "apiKey", AttributeValue.fromS(environment.apiKey()),
                "createdAt", AttributeValue.fromS(environment.createdAt().toString()));
    }

    private Environment fromItem(Map<String, AttributeValue> item) {
        return new Environment(
                item.get("key").s(),
                item.get("name").s(),
                item.get("apiKey").s(),
                Instant.parse(item.get("createdAt").s()));
    }
}
