package com.toggleboard.api.repository.memory;

import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.domain.FlagType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryFlagRepositoryTest {

    private InMemoryFlagRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryFlagRepository();
    }

    @Test
    void savesAndFindsById() {
        Flag flag = newFlag("id-1", "prod", "new-nav");
        repository.save(flag);

        assertThat(repository.findById("id-1")).contains(flag);
    }

    @Test
    void findsByEnvironmentAndKey() {
        repository.save(newFlag("id-1", "prod", "new-nav"));
        repository.save(newFlag("id-2", "staging", "new-nav"));

        assertThat(repository.findByEnvironmentAndKey("prod", "new-nav").map(Flag::id)).contains("id-1");
        assertThat(repository.findByEnvironmentAndKey("staging", "new-nav").map(Flag::id)).contains("id-2");
        assertThat(repository.findByEnvironmentAndKey("staging", "missing")).isEmpty();
    }

    @Test
    void listsAllFlagsScopedToAnEnvironmentOnly() {
        repository.save(newFlag("id-1", "prod", "flag-a"));
        repository.save(newFlag("id-2", "prod", "flag-b"));
        repository.save(newFlag("id-3", "staging", "flag-c"));

        List<Flag> prodFlags = repository.findAllByEnvironment("prod");

        assertThat(prodFlags).extracting(Flag::key).containsExactly("flag-a", "flag-b");
    }

    @Test
    void existsByEnvironmentAndKeyReflectsState() {
        assertThat(repository.existsByEnvironmentAndKey("prod", "new-nav")).isFalse();
        repository.save(newFlag("id-1", "prod", "new-nav"));
        assertThat(repository.existsByEnvironmentAndKey("prod", "new-nav")).isTrue();
    }

    @Test
    void deleteByIdRemovesTheFlag() {
        repository.save(newFlag("id-1", "prod", "new-nav"));
        repository.deleteById("id-1");

        assertThat(repository.findById("id-1")).isEmpty();
    }

    private Flag newFlag(String id, String environmentKey, String key) {
        Instant now = Instant.now();
        return new Flag(id, key, environmentKey, key, "desc", FlagType.BOOLEAN, true, 50, List.of(), null, now, now);
    }
}
