package com.toggleboard.api.repository.dynamodb;

import com.toggleboard.api.domain.Environment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the same shape of assertions as {@code InMemoryEnvironmentRepositoryTest}
 * against a real {@link software.amazon.awssdk.services.dynamodb.DynamoDbClient}
 * backed by embedded DynamoDB Local, proving the DynamoDB implementation is a
 * genuine drop-in for the {@code EnvironmentRepository} interface.
 */
class DynamoDbEnvironmentRepositoryTest {

    private final DynamoDbEnvironmentRepository repository = new DynamoDbEnvironmentRepository(DynamoDbLocalTestSupport.CLIENT);

    @Test
    void savesAndFindsByKey() {
        String key = uniqueKey();
        Environment environment = new Environment(key, "Production", "tgb_" + key, Instant.now());
        repository.save(environment);

        assertThat(repository.findByKey(key)).contains(environment);
    }

    @Test
    void findsByApiKey() {
        String key = uniqueKey();
        String apiKey = "tgb_" + key;
        Environment environment = new Environment(key, "Production", apiKey, Instant.now());
        repository.save(environment);

        assertThat(repository.findByApiKey(apiKey)).contains(environment);
        assertThat(repository.findByApiKey("tgb_unknown-" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void existsByKeyReflectsState() {
        String key = uniqueKey();
        assertThat(repository.existsByKey(key)).isFalse();

        repository.save(new Environment(key, "Production", "tgb_" + key, Instant.now()));

        assertThat(repository.existsByKey(key)).isTrue();
    }

    @Test
    void findAllIncludesEverySavedEnvironment() {
        String keyA = uniqueKey();
        String keyB = uniqueKey();
        repository.save(new Environment(keyA, "A", "tgb_" + keyA, Instant.now()));
        repository.save(new Environment(keyB, "B", "tgb_" + keyB, Instant.now()));

        assertThat(repository.findAll()).extracting(Environment::key).contains(keyA, keyB);
    }

    private String uniqueKey() {
        return "env-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
