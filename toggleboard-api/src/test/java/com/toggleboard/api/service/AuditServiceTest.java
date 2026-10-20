package com.toggleboard.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.api.domain.AuditAction;
import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.domain.AuditEntry;
import com.toggleboard.api.repository.memory.InMemoryAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceTest {

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(new InMemoryAuditRepository(), new ObjectMapper());
    }

    @Test
    void recordCreateStoresAfterSnapshotOnly() {
        auditService.recordCreate("production", AuditEntityType.FLAG, "new-nav", "alice", Map.of("enabled", true));

        AuditEntry entry = auditService.listByEnvironment("production", null, null).get(0);

        assertThat(entry.action()).isEqualTo(AuditAction.CREATE);
        assertThat(entry.actor()).isEqualTo("alice");
        assertThat(entry.before()).isNull();
        assertThat(entry.after()).contains("\"enabled\":true");
    }

    @Test
    void recordKillSwitchPicksEnabledOrDisabledAction() {
        auditService.recordKillSwitch("production", "new-nav", "alice", Map.of("enabled", true), Map.of("enabled", false), false);
        auditService.recordKillSwitch("production", "new-nav", "alice", Map.of("enabled", false), Map.of("enabled", true), true);

        List<AuditAction> actions = auditService.listByEnvironment("production", null, null).stream()
                .map(AuditEntry::action).toList();

        assertThat(actions).contains(AuditAction.KILL_SWITCH_DISABLED, AuditAction.KILL_SWITCH_ENABLED);
    }

    @Test
    void blankActorDefaultsToSystem() {
        auditService.recordCreate("production", AuditEntityType.FLAG, "new-nav", "  ", Map.of());

        assertThat(auditService.listByEnvironment("production", null, null).get(0).actor()).isEqualTo("system");
    }

    @Test
    void listIsScopedToEnvironmentAndNewestFirst() {
        auditService.recordCreate("production", AuditEntityType.FLAG, "a", "alice", Map.of());
        auditService.recordCreate("staging", AuditEntityType.FLAG, "b", "alice", Map.of());
        auditService.recordCreate("production", AuditEntityType.FLAG, "c", "alice", Map.of());

        List<String> entityKeys = auditService.listByEnvironment("production", null, null).stream()
                .map(AuditEntry::entityKey).toList();

        assertThat(entityKeys).containsExactly("c", "a");
    }

    @Test
    void listCanBeFilteredByEntityType() {
        auditService.recordCreate("production", AuditEntityType.FLAG, "flag-a", "alice", Map.of());
        auditService.recordCreate("production", AuditEntityType.ENVIRONMENT, "production", "alice", Map.of());

        List<AuditEntry> flagEntries = auditService.listByEnvironment("production", AuditEntityType.FLAG, null);

        assertThat(flagEntries).extracting(AuditEntry::entityKey).containsExactly("flag-a");
    }
}
