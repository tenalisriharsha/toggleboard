package com.toggleboard.api.repository.dynamodb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.api.domain.Segment;
import com.toggleboard.api.domain.TargetingOperator;
import com.toggleboard.api.domain.TargetingRule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DynamoDbSegmentRepositoryTest {

    private final DynamoDbSegmentRepository repository =
            new DynamoDbSegmentRepository(DynamoDbLocalTestSupport.CLIENT, new ObjectMapper());

    @Test
    void savesAndRoundTripsRules() {
        String id = UUID.randomUUID().toString();
        String env = "prod-" + id;
        List<TargetingRule> rules = List.of(
                new TargetingRule("plan", TargetingOperator.EQUALS, List.of("enterprise"), null));
        Instant now = Instant.now();
        Segment segment = new Segment(id, env, "enterprise-users", "Enterprise Users", "desc", rules, now, now);

        repository.save(segment);

        assertThat(repository.findById(id)).contains(segment);
    }

    @Test
    void findsByEnvironmentAndKeyScopedPerEnvironment() {
        String env1 = "prod-" + UUID.randomUUID();
        String env2 = "staging-" + UUID.randomUUID();
        Segment segmentA = newSegment(UUID.randomUUID().toString(), env1, "beta-users");
        Segment segmentB = newSegment(UUID.randomUUID().toString(), env2, "beta-users");
        repository.save(segmentA);
        repository.save(segmentB);

        assertThat(repository.findByEnvironmentAndKey(env1, "beta-users").map(Segment::id)).contains(segmentA.id());
        assertThat(repository.findByEnvironmentAndKey(env2, "missing")).isEmpty();
    }

    @Test
    void listsAllSegmentsScopedToAnEnvironmentOnly() {
        String env = "prod-" + UUID.randomUUID();
        String otherEnv = "staging-" + UUID.randomUUID();
        repository.save(newSegment(UUID.randomUUID().toString(), env, "segment-a"));
        repository.save(newSegment(UUID.randomUUID().toString(), env, "segment-b"));
        repository.save(newSegment(UUID.randomUUID().toString(), otherEnv, "segment-c"));

        assertThat(repository.findAllByEnvironment(env)).extracting(Segment::key)
                .containsExactlyInAnyOrder("segment-a", "segment-b");
    }

    @Test
    void existsByEnvironmentAndKeyReflectsState() {
        String env = "prod-" + UUID.randomUUID();
        assertThat(repository.existsByEnvironmentAndKey(env, "beta-users")).isFalse();

        repository.save(newSegment(UUID.randomUUID().toString(), env, "beta-users"));

        assertThat(repository.existsByEnvironmentAndKey(env, "beta-users")).isTrue();
    }

    @Test
    void deleteByIdRemovesTheSegment() {
        String id = UUID.randomUUID().toString();
        Segment segment = newSegment(id, "prod-" + id, "beta-users");
        repository.save(segment);

        repository.deleteById(id);

        assertThat(repository.findById(id)).isEmpty();
    }

    private Segment newSegment(String id, String environmentKey, String key) {
        Instant now = Instant.now();
        List<TargetingRule> rules = List.of(
                new TargetingRule("country", TargetingOperator.IN, List.of("US"), null));
        return new Segment(id, environmentKey, key, key, "desc", rules, now, now);
    }
}
