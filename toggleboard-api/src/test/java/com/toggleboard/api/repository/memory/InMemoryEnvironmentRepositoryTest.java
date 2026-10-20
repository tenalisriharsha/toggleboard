package com.toggleboard.api.repository.memory;

import com.toggleboard.api.domain.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryEnvironmentRepositoryTest {

    private InMemoryEnvironmentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryEnvironmentRepository();
    }

    @Test
    void savesAndFindsByKey() {
        Environment environment = new Environment("production", "Production", "tgb_abc", Instant.now());
        repository.save(environment);

        assertThat(repository.findByKey("production")).contains(environment);
    }

    @Test
    void findsByApiKey() {
        Environment environment = new Environment("production", "Production", "tgb_abc", Instant.now());
        repository.save(environment);

        assertThat(repository.findByApiKey("tgb_abc")).contains(environment);
        assertThat(repository.findByApiKey("tgb_unknown")).isEmpty();
    }

    @Test
    void existsByKeyReflectsState() {
        assertThat(repository.existsByKey("production")).isFalse();
        repository.save(new Environment("production", "Production", "tgb_abc", Instant.now()));
        assertThat(repository.existsByKey("production")).isTrue();
    }

    @Test
    void findAllReturnsEverySavedEnvironment() {
        repository.save(new Environment("production", "Production", "tgb_a", Instant.now()));
        repository.save(new Environment("staging", "Staging", "tgb_b", Instant.now()));

        assertThat(repository.findAll()).hasSize(2);
    }
}
