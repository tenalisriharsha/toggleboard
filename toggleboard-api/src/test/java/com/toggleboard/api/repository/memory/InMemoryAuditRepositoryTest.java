package com.toggleboard.api.repository.memory;

import com.toggleboard.api.domain.AuditAction;
import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.domain.AuditEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAuditRepositoryTest {

    private InMemoryAuditRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAuditRepository();
    }

    @Test
    void findByEnvironmentReturnsNewestFirst() {
        AuditEntry older = entry("prod", "a", Instant.now().minusSeconds(10));
        AuditEntry newer = entry("prod", "b", Instant.now());
        repository.save(older);
        repository.save(newer);

        assertThat(repository.findByEnvironment("prod", null, 10)).extracting(AuditEntry::entityKey)
                .containsExactly("b", "a");
    }

    @Test
    void findByEnvironmentIsScopedToThatEnvironment() {
        repository.save(entry("prod", "a", Instant.now()));
        repository.save(entry("staging", "b", Instant.now()));

        assertThat(repository.findByEnvironment("prod", null, 10)).extracting(AuditEntry::entityKey).containsExactly("a");
    }

    @Test
    void findByEnvironmentCanFilterByEntityType() {
        repository.save(new AuditEntry("1", "prod", AuditEntityType.FLAG, "flag-a", AuditAction.CREATE, "alice", Instant.now(), null, "{}"));
        repository.save(new AuditEntry("2", "prod", AuditEntityType.ENVIRONMENT, "prod", AuditAction.CREATE, "alice", Instant.now(), null, "{}"));

        List<AuditEntry> flagOnly = repository.findByEnvironment("prod", AuditEntityType.FLAG, 10);

        assertThat(flagOnly).extracting(AuditEntry::entityKey).containsExactly("flag-a");
    }

    @Test
    void findByEnvironmentRespectsLimit() {
        for (int i = 0; i < 5; i++) {
            repository.save(entry("prod", "flag-" + i, Instant.now().plusMillis(i)));
        }

        assertThat(repository.findByEnvironment("prod", null, 2)).hasSize(2);
    }

    private AuditEntry entry(String environmentKey, String entityKey, Instant timestamp) {
        return new AuditEntry("id-" + entityKey, environmentKey, AuditEntityType.FLAG, entityKey,
                AuditAction.CREATE, "alice", timestamp, null, "{}");
    }
}
