package com.toggleboard.api.repository.dynamodb;

import com.toggleboard.api.domain.AuditAction;
import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.domain.AuditEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DynamoDbAuditRepositoryTest {

    private final DynamoDbAuditRepository repository = new DynamoDbAuditRepository(DynamoDbLocalTestSupport.CLIENT);

    @Test
    void savesAndListsNewestFirst() {
        String env = "prod-" + UUID.randomUUID();
        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now();

        AuditEntry older = new AuditEntry(UUID.randomUUID().toString(), env, AuditEntityType.FLAG, "flag-a",
                AuditAction.CREATE, "alice", t1, null, "{\"enabled\":true}");
        AuditEntry newer = new AuditEntry(UUID.randomUUID().toString(), env, AuditEntityType.FLAG, "flag-a",
                AuditAction.UPDATE, "bob", t2, "{\"enabled\":true}", "{\"enabled\":false}");

        repository.save(older);
        repository.save(newer);

        List<AuditEntry> entries = repository.findByEnvironment(env, null, 10);

        assertThat(entries).extracting(AuditEntry::id).containsExactly(newer.id(), older.id());
    }

    @Test
    void filtersByEntityType() {
        String env = "prod-" + UUID.randomUUID();
        AuditEntry flagEntry = new AuditEntry(UUID.randomUUID().toString(), env, AuditEntityType.FLAG, "flag-a",
                AuditAction.CREATE, "alice", Instant.now(), null, "{}");
        AuditEntry envEntry = new AuditEntry(UUID.randomUUID().toString(), env, AuditEntityType.ENVIRONMENT, env,
                AuditAction.CREATE, "alice", Instant.now(), null, "{}");

        repository.save(flagEntry);
        repository.save(envEntry);

        List<AuditEntry> flagOnly = repository.findByEnvironment(env, AuditEntityType.FLAG, 10);

        assertThat(flagOnly).extracting(AuditEntry::id).containsExactly(flagEntry.id());
    }

    @Test
    void respectsTheLimit() {
        String env = "prod-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            repository.save(new AuditEntry(UUID.randomUUID().toString(), env, AuditEntityType.FLAG, "flag-a",
                    AuditAction.UPDATE, "alice", Instant.now().plusMillis(i), null, "{}"));
        }

        assertThat(repository.findByEnvironment(env, null, 2)).hasSize(2);
    }
}
