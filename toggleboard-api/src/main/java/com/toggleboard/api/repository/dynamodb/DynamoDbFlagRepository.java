package com.toggleboard.api.repository.dynamodb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.domain.FlagType;
import com.toggleboard.api.domain.TargetingRule;
import com.toggleboard.api.domain.Variant;
import com.toggleboard.api.repository.FlagRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB-backed {@link FlagRepository}. Drop-in replacement for
 * {@code InMemoryFlagRepository} behind the same interface. Nested
 * structures ({@code variants}, {@code targetingRules}, {@code segmentKeys})
 * are stored as JSON attributes rather than deeply nested DynamoDB maps,
 * which keeps the item shape simple since they're never queried on
 * directly — only read back whole with the rest of the flag.
 */
@Repository
@Profile("dynamodb")
public class DynamoDbFlagRepository implements FlagRepository {

    private final DynamoDbClient client;
    private final ObjectMapper objectMapper;

    public DynamoDbFlagRepository(DynamoDbClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flag save(Flag flag) {
        client.putItem(b -> b.tableName(TableNames.FLAGS).item(toItem(flag)));
        return flag;
    }

    @Override
    public Optional<Flag> findById(String id) {
        Map<String, AttributeValue> item = client.getItem(b -> b
                .tableName(TableNames.FLAGS)
                .key(Map.of("id", AttributeValue.fromS(id)))).item();
        return (item == null || item.isEmpty()) ? Optional.empty() : Optional.of(fromItem(item));
    }

    @Override
    public Optional<Flag> findByEnvironmentAndKey(String environmentKey, String flagKey) {
        return queryByEnvironment(environmentKey, flagKey, 1).stream().findFirst();
    }

    @Override
    public List<Flag> findAllByEnvironment(String environmentKey) {
        return queryByEnvironment(environmentKey, null, null);
    }

    @Override
    public boolean existsByEnvironmentAndKey(String environmentKey, String flagKey) {
        return findByEnvironmentAndKey(environmentKey, flagKey).isPresent();
    }

    @Override
    public void deleteById(String id) {
        client.deleteItem(b -> b.tableName(TableNames.FLAGS).key(Map.of("id", AttributeValue.fromS(id))));
    }

    private List<Flag> queryByEnvironment(String environmentKey, String exactFlagKey, Integer limit) {
        QueryRequest.Builder request = QueryRequest.builder()
                .tableName(TableNames.FLAGS)
                .indexName(TableNames.ENVIRONMENT_KEY_INDEX);

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":environmentKey", AttributeValue.fromS(environmentKey));
        String condition = "environmentKey = :environmentKey";
        if (exactFlagKey != null) {
            condition += " AND #key = :key";
            values.put(":key", AttributeValue.fromS(exactFlagKey));
            request.expressionAttributeNames(Map.of("#key", "key"));
        }
        request.keyConditionExpression(condition).expressionAttributeValues(values);
        if (limit != null) {
            request.limit(limit);
        }

        return client.query(request.build()).items().stream().map(this::fromItem).toList();
    }

    private Map<String, AttributeValue> toItem(Flag flag) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.fromS(flag.id()));
        item.put("key", AttributeValue.fromS(flag.key()));
        item.put("environmentKey", AttributeValue.fromS(flag.environmentKey()));
        item.put("name", AttributeValue.fromS(flag.name()));
        item.put("description", AttributeValue.fromS(flag.description() == null ? "" : flag.description()));
        item.put("type", AttributeValue.fromS(flag.type().name()));
        item.put("enabled", AttributeValue.fromBool(flag.enabled()));
        item.put("rolloutPercentage", AttributeValue.fromN(String.valueOf(flag.rolloutPercentage())));
        item.put("variantsJson", AttributeValue.fromS(toJson(flag.variants())));
        item.put("defaultVariantKey", AttributeValue.fromS(flag.defaultVariantKey() == null ? "" : flag.defaultVariantKey()));
        item.put("targetingRulesJson", AttributeValue.fromS(toJson(flag.targetingRules())));
        item.put("segmentKeysJson", AttributeValue.fromS(toJson(flag.segmentKeys())));
        item.put("createdAt", AttributeValue.fromS(flag.createdAt().toString()));
        item.put("updatedAt", AttributeValue.fromS(flag.updatedAt().toString()));
        return item;
    }

    private Flag fromItem(Map<String, AttributeValue> item) {
        String defaultVariantKey = item.get("defaultVariantKey").s();
        String description = item.get("description").s();
        return new Flag(
                item.get("id").s(),
                item.get("key").s(),
                item.get("environmentKey").s(),
                item.get("name").s(),
                description.isEmpty() ? null : description,
                FlagType.valueOf(item.get("type").s()),
                item.get("enabled").bool(),
                Integer.parseInt(item.get("rolloutPercentage").n()),
                fromJson(item.get("variantsJson").s(), Variant[].class),
                defaultVariantKey.isEmpty() ? null : defaultVariantKey,
                fromJson(item.get("targetingRulesJson").s(), TargetingRule[].class),
                fromJson(item.get("segmentKeysJson").s(), String[].class),
                Instant.parse(item.get("createdAt").s()),
                Instant.parse(item.get("updatedAt").s()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize flag field to JSON", e);
        }
    }

    private <T> List<T> fromJson(String json, Class<T[]> arrayType) {
        try {
            return List.of(objectMapper.readValue(json, arrayType));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize flag field from JSON", e);
        }
    }
}
