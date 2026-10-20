package com.toggleboard.api.repository.dynamodb;

import com.toggleboard.api.domain.AuditAction;
import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.domain.AuditEntry;
import com.toggleboard.api.repository.AuditRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DynamoDB-backed {@link AuditRepository}. The sort key is
 * {@code <ISO-8601 timestamp>#<id>} so a descending query on
 * {@code environmentKey} naturally returns the newest entries first without
 * a separate index.
 */
@Repository
@Profile("dynamodb")
public class DynamoDbAuditRepository implements AuditRepository {

    private final DynamoDbClient client;

    public DynamoDbAuditRepository(DynamoDbClient client) {
        this.client = client;
    }

    @Override
    public AuditEntry save(AuditEntry entry) {
        client.putItem(b -> b.tableName(TableNames.AUDIT_ENTRIES).item(toItem(entry)));
        return entry;
    }

    @Override
    public List<AuditEntry> findByEnvironment(String environmentKey, AuditEntityType entityTypeFilter, int limit) {
        List<AuditEntry> matched = new ArrayList<>();
        Map<String, AttributeValue> exclusiveStartKey = null;

        do {
            QueryRequest.Builder request = QueryRequest.builder()
                    .tableName(TableNames.AUDIT_ENTRIES)
                    .keyConditionExpression("environmentKey = :environmentKey")
                    .expressionAttributeValues(Map.of(":environmentKey", AttributeValue.fromS(environmentKey)))
                    .scanIndexForward(false);
            if (exclusiveStartKey != null) {
                request.exclusiveStartKey(exclusiveStartKey);
            }

            QueryResponse response = client.query(request.build());
            for (Map<String, AttributeValue> item : response.items()) {
                AuditEntry entry = fromItem(item);
                if (entityTypeFilter == null || entry.entityType() == entityTypeFilter) {
                    matched.add(entry);
                    if (matched.size() >= limit) {
                        return matched;
                    }
                }
            }
            exclusiveStartKey = response.lastEvaluatedKey();
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());

        return matched;
    }

    private Map<String, AttributeValue> toItem(AuditEntry entry) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("environmentKey", AttributeValue.fromS(entry.environmentKey()));
        item.put("sortKey", AttributeValue.fromS(entry.timestamp().toString() + "#" + entry.id()));
        item.put("id", AttributeValue.fromS(entry.id()));
        item.put("entityType", AttributeValue.fromS(entry.entityType().name()));
        item.put("entityKey", AttributeValue.fromS(entry.entityKey()));
        item.put("action", AttributeValue.fromS(entry.action().name()));
        item.put("actor", AttributeValue.fromS(entry.actor()));
        item.put("timestamp", AttributeValue.fromS(entry.timestamp().toString()));
        item.put("before", entry.before() == null ? AttributeValue.fromNul(true) : AttributeValue.fromS(entry.before()));
        item.put("after", entry.after() == null ? AttributeValue.fromNul(true) : AttributeValue.fromS(entry.after()));
        return item;
    }

    private AuditEntry fromItem(Map<String, AttributeValue> item) {
        return new AuditEntry(
                item.get("id").s(),
                item.get("environmentKey").s(),
                AuditEntityType.valueOf(item.get("entityType").s()),
                item.get("entityKey").s(),
                AuditAction.valueOf(item.get("action").s()),
                item.get("actor").s(),
                Instant.parse(item.get("timestamp").s()),
                isNull(item.get("before")) ? null : item.get("before").s(),
                isNull(item.get("after")) ? null : item.get("after").s());
    }

    private boolean isNull(AttributeValue value) {
        return value == null || Boolean.TRUE.equals(value.nul());
    }
}
