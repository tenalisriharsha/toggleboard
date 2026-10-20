package com.toggleboard.api.repository.dynamodb;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.List;

/**
 * Creates the DynamoDB tables this service needs on startup, if they don't
 * already exist. Idempotent: safe to run against a table that's already
 * there (real AWS) or a fresh in-memory DynamoDB Local instance (tests).
 */
@Component
@Profile("dynamodb")
public class DynamoDbTableInitializer implements ApplicationRunner {

    private final DynamoDbClient client;

    public DynamoDbTableInitializer(DynamoDbClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        createTablesIfMissing();
    }

    public void createTablesIfMissing() {
        createIfMissing(CreateTableRequest.builder()
                .tableName(TableNames.ENVIRONMENTS)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        attr("key"),
                        attr("apiKey"))
                .keySchema(hashKey("key"))
                .globalSecondaryIndexes(gsi(TableNames.API_KEY_INDEX, "apiKey", null))
                .build());

        createIfMissing(CreateTableRequest.builder()
                .tableName(TableNames.FLAGS)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        attr("id"),
                        attr("environmentKey"),
                        attr("key"))
                .keySchema(hashKey("id"))
                .globalSecondaryIndexes(gsi(TableNames.ENVIRONMENT_KEY_INDEX, "environmentKey", "key"))
                .build());

        createIfMissing(CreateTableRequest.builder()
                .tableName(TableNames.SEGMENTS)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        attr("id"),
                        attr("environmentKey"),
                        attr("key"))
                .keySchema(hashKey("id"))
                .globalSecondaryIndexes(gsi(TableNames.ENVIRONMENT_KEY_INDEX, "environmentKey", "key"))
                .build());

        createIfMissing(CreateTableRequest.builder()
                .tableName(TableNames.AUDIT_ENTRIES)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        attr("environmentKey"),
                        attr("sortKey"))
                .keySchema(hashKey("environmentKey"), rangeKey("sortKey"))
                .build());
    }

    private void createIfMissing(CreateTableRequest request) {
        try {
            client.describeTable(b -> b.tableName(request.tableName()));
        } catch (ResourceNotFoundException notFound) {
            try {
                client.createTable(request);
            } catch (ResourceInUseException alreadyBeingCreated) {
                // another instance raced us to create it; nothing to do
            }
        }
    }

    private static AttributeDefinition attr(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build();
    }

    private static KeySchemaElement hashKey(String name) {
        return KeySchemaElement.builder().attributeName(name).keyType(KeyType.HASH).build();
    }

    private static KeySchemaElement rangeKey(String name) {
        return KeySchemaElement.builder().attributeName(name).keyType(KeyType.RANGE).build();
    }

    private static GlobalSecondaryIndex gsi(String indexName, String hashAttr, String rangeAttr) {
        List<KeySchemaElement> keySchema = rangeAttr == null
                ? List.of(hashKey(hashAttr))
                : List.of(hashKey(hashAttr), rangeKey(rangeAttr));
        return GlobalSecondaryIndex.builder()
                .indexName(indexName)
                .keySchema(keySchema)
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }
}
