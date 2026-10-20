package com.toggleboard.api.repository.memory;

import com.toggleboard.api.domain.Segment;
import com.toggleboard.api.domain.TargetingOperator;
import com.toggleboard.api.domain.TargetingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySegmentRepositoryTest {

    private InMemorySegmentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemorySegmentRepository();
    }

    @Test
    void savesAndFindsById() {
        Segment segment = newSegment("id-1", "prod", "beta-users");
        repository.save(segment);

        assertThat(repository.findById("id-1")).contains(segment);
    }

    @Test
    void findsByEnvironmentAndKey() {
        repository.save(newSegment("id-1", "prod", "beta-users"));
        repository.save(newSegment("id-2", "staging", "beta-users"));

        assertThat(repository.findByEnvironmentAndKey("prod", "beta-users").map(Segment::id)).contains("id-1");
        assertThat(repository.findByEnvironmentAndKey("staging", "missing")).isEmpty();
    }

    @Test
    void listsAllSegmentsScopedToAnEnvironmentOnly() {
        repository.save(newSegment("id-1", "prod", "seg-a"));
        repository.save(newSegment("id-2", "prod", "seg-b"));
        repository.save(newSegment("id-3", "staging", "seg-c"));

        assertThat(repository.findAllByEnvironment("prod")).extracting(Segment::key).containsExactly("seg-a", "seg-b");
    }

    @Test
    void existsByEnvironmentAndKeyReflectsState() {
        assertThat(repository.existsByEnvironmentAndKey("prod", "beta-users")).isFalse();
        repository.save(newSegment("id-1", "prod", "beta-users"));
        assertThat(repository.existsByEnvironmentAndKey("prod", "beta-users")).isTrue();
    }

    @Test
    void deleteByIdRemovesTheSegment() {
        repository.save(newSegment("id-1", "prod", "beta-users"));
        repository.deleteById("id-1");

        assertThat(repository.findById("id-1")).isEmpty();
    }

    private Segment newSegment(String id, String environmentKey, String key) {
        Instant now = Instant.now();
        List<TargetingRule> rules = List.of(new TargetingRule("beta", TargetingOperator.EQUALS, List.of("true"), null));
        return new Segment(id, environmentKey, key, key, "desc", rules, now, now);
    }
}
