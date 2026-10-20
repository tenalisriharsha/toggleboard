package com.toggleboard.api.repository.dynamodb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.domain.FlagType;
import com.toggleboard.api.domain.TargetingOperator;
import com.toggleboard.api.domain.TargetingRule;
import com.toggleboard.api.domain.Variant;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the same shape of assertions as {@code InMemoryFlagRepositoryTest}
 * against embedded DynamoDB Local, including round-tripping the JSON-encoded
 * variants, targeting rules and segment keys.
 */
class DynamoDbFlagRepositoryTest {

    private final DynamoDbFlagRepository repository =
            new DynamoDbFlagRepository(DynamoDbLocalTestSupport.CLIENT, new ObjectMapper());

    @Test
    void savesAndFindsById() {
        String id = UUID.randomUUID().toString();
        Flag flag = newFlag(id, "prod-" + id, "new-nav");
        repository.save(flag);

        assertThat(repository.findById(id)).contains(flag);
    }

    @Test
    void findsByEnvironmentAndKeyScopedPerEnvironment() {
        String env1 = "prod-" + UUID.randomUUID();
        String env2 = "staging-" + UUID.randomUUID();
        Flag flagA = newFlag(UUID.randomUUID().toString(), env1, "new-nav");
        Flag flagB = newFlag(UUID.randomUUID().toString(), env2, "new-nav");
        repository.save(flagA);
        repository.save(flagB);

        assertThat(repository.findByEnvironmentAndKey(env1, "new-nav").map(Flag::id)).contains(flagA.id());
        assertThat(repository.findByEnvironmentAndKey(env2, "new-nav").map(Flag::id)).contains(flagB.id());
        assertThat(repository.findByEnvironmentAndKey(env2, "missing")).isEmpty();
    }

    @Test
    void listsAllFlagsScopedToAnEnvironmentOnly() {
        String env = "prod-" + UUID.randomUUID();
        String otherEnv = "staging-" + UUID.randomUUID();
        repository.save(newFlag(UUID.randomUUID().toString(), env, "flag-a"));
        repository.save(newFlag(UUID.randomUUID().toString(), env, "flag-b"));
        repository.save(newFlag(UUID.randomUUID().toString(), otherEnv, "flag-c"));

        List<Flag> envFlags = repository.findAllByEnvironment(env);

        assertThat(envFlags).extracting(Flag::key).containsExactlyInAnyOrder("flag-a", "flag-b");
    }

    @Test
    void existsByEnvironmentAndKeyReflectsState() {
        String env = "prod-" + UUID.randomUUID();
        assertThat(repository.existsByEnvironmentAndKey(env, "new-nav")).isFalse();

        repository.save(newFlag(UUID.randomUUID().toString(), env, "new-nav"));

        assertThat(repository.existsByEnvironmentAndKey(env, "new-nav")).isTrue();
    }

    @Test
    void deleteByIdRemovesTheFlag() {
        String id = UUID.randomUUID().toString();
        repository.save(newFlag(id, "prod-" + id, "new-nav"));

        repository.deleteById(id);

        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void roundTripsVariantsTargetingRulesAndSegmentKeys() {
        String id = UUID.randomUUID().toString();
        String env = "prod-" + id;
        List<Variant> variants = List.of(new Variant("control", "off", 50), new Variant("treatment", "on", 50));
        List<TargetingRule> rules = List.of(
                new TargetingRule("country", TargetingOperator.IN, List.of("US", "CA"), "treatment"));
        List<String> segmentKeys = List.of("beta-users", "internal");
        Instant now = Instant.now();

        Flag flag = new Flag(id, "experiment", env, "Experiment", "desc", FlagType.MULTIVARIATE, true, 0,
                variants, "control", rules, segmentKeys, now, now);
        repository.save(flag);

        Flag loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.variants()).isEqualTo(variants);
        assertThat(loaded.targetingRules()).isEqualTo(rules);
        assertThat(loaded.segmentKeys()).isEqualTo(segmentKeys);
    }

    private Flag newFlag(String id, String environmentKey, String key) {
        Instant now = Instant.now();
        return new Flag(id, key, environmentKey, key, "desc", FlagType.BOOLEAN, true, 50,
                List.of(), null, List.of(), List.of(), now, now);
    }
}
