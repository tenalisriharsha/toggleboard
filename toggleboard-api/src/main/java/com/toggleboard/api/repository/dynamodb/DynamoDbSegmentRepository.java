package com.toggleboard.api.repository.dynamodb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.api.domain.Segment;
import com.toggleboard.api.domain.TargetingRule;
import com.toggleboard.api.repository.SegmentRepository;
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

/** DynamoDB-backed {@link SegmentRepository}, mirroring {@link DynamoDbFlagRepository}'s shape. */
@Repository
@Profile("dynamodb")
public class DynamoDbSegmentRepository implements SegmentRepository {

    private final DynamoDbClient client;
    private final ObjectMapper objectMapper;

    public DynamoDbSegmentRepository(DynamoDbClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public Segment save(Segment segment) {
        client.putItem(b -> b.tableName(TableNames.SEGMENTS).item(toItem(segment)));
        return segment;
    }

    @Override
    public Optional<Segment> findById(String id) {
        Map<String, AttributeValue> item = client.getItem(b -> b
                .tableName(TableNames.SEGMENTS)
                .key(Map.of("id", AttributeValue.fromS(id)))).item();
        return (item == null || item.isEmpty()) ? Optional.empty() : Optional.of(fromItem(item));
    }

    @Override
    public Optional<Segment> findByEnvironmentAndKey(String environmentKey, String key) {
        return queryByEnvironment(environmentKey, key, 1).stream().findFirst();
    }

    @Override
    public List<Segment> findAllByEnvironment(String environmentKey) {
        return queryByEnvironment(environmentKey, null, null);
    }

    @Override
    public boolean existsByEnvironmentAndKey(String environmentKey, String key) {
        return findByEnvironmentAndKey(environmentKey, key).isPresent();
    }

    @Override
    public void deleteById(String id) {
        client.deleteItem(b -> b.tableName(TableNames.SEGMENTS).key(Map.of("id", AttributeValue.fromS(id))));
    }

    private List<Segment> queryByEnvironment(String environmentKey, String exactKey, Integer limit) {
        QueryRequest.Builder request = QueryRequest.builder()
                .tableName(TableNames.SEGMENTS)
                .indexName(TableNames.ENVIRONMENT_KEY_INDEX);

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":environmentKey", AttributeValue.fromS(environmentKey));
        String condition = "environmentKey = :environmentKey";
        if (exactKey != null) {
            condition += " AND #key = :key";
            values.put(":key", AttributeValue.fromS(exactKey));
            request.expressionAttributeNames(Map.of("#key", "key"));
        }
        request.keyConditionExpression(condition).expressionAttributeValues(values);
        if (limit != null) {
            request.limit(limit);
        }

        return client.query(request.build()).items().stream().map(this::fromItem).toList();
    }

    private Map<String, AttributeValue> toItem(Segment segment) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.fromS(segment.id()));
        item.put("environmentKey", AttributeValue.fromS(segment.environmentKey()));
        item.put("key", AttributeValue.fromS(segment.key()));
        item.put("name", AttributeValue.fromS(segment.name()));
        item.put("description", AttributeValue.fromS(segment.description() == null ? "" : segment.description()));
        item.put("rulesJson", AttributeValue.fromS(toJson(segment.rules())));
        item.put("createdAt", AttributeValue.fromS(segment.createdAt().toString()));
        item.put("updatedAt", AttributeValue.fromS(segment.updatedAt().toString()));
        return item;
    }

    private Segment fromItem(Map<String, AttributeValue> item) {
        String description = item.get("description").s();
        return new Segment(
                item.get("id").s(),
                item.get("environmentKey").s(),
                item.get("key").s(),
                item.get("name").s(),
                description.isEmpty() ? null : description,
                fromJson(item.get("rulesJson").s()),
                Instant.parse(item.get("createdAt").s()),
                Instant.parse(item.get("updatedAt").s()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize segment rules to JSON", e);
        }
    }

    private List<TargetingRule> fromJson(String json) {
        try {
            return List.of(objectMapper.readValue(json, TargetingRule[].class));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize segment rules from JSON", e);
        }
    }
}
