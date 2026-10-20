package com.toggleboard.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.api.domain.AuditAction;
import com.toggleboard.api.domain.Environment;
import com.toggleboard.api.exception.ConflictException;
import com.toggleboard.api.exception.NotFoundException;
import com.toggleboard.api.repository.memory.InMemoryAuditRepository;
import com.toggleboard.api.repository.memory.InMemoryEnvironmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentServiceTest {

    private EnvironmentService environmentService;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(new InMemoryAuditRepository(), new ObjectMapper());
        environmentService = new EnvironmentService(new InMemoryEnvironmentRepository(), auditService);
    }

    @Test
    void createGeneratesAUniqueApiKey() {
        Environment production = environmentService.create("production", "Production", "alice");
        Environment staging = environmentService.create("staging", "Staging", "alice");

        assertThat(production.apiKey()).isNotBlank().startsWith("tgb_");
        assertThat(staging.apiKey()).isNotEqualTo(production.apiKey());
    }

    @Test
    void createRejectsDuplicateKeys() {
        environmentService.create("production", "Production", "alice");

        assertThatThrownBy(() -> environmentService.create("production", "Prod Again", "alice"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRecordsAnAuditEntry() {
        environmentService.create("production", "Production", "alice");

        assertThat(auditService.listByEnvironment("production", null, null))
                .extracting(entry -> entry.action())
                .containsExactly(AuditAction.CREATE);
    }

    @Test
    void getByKeyThrowsWhenMissing() {
        assertThatThrownBy(() -> environmentService.getByKey("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getByApiKeyFindsTheOwningEnvironment() {
        Environment environment = environmentService.create("production", "Production", "alice");

        assertThat(environmentService.getByApiKey(environment.apiKey())).isEqualTo(environment);
    }

    @Test
    void listAllReturnsEveryEnvironment() {
        environmentService.create("production", "Production", "alice");
        environmentService.create("staging", "Staging", "alice");

        assertThat(environmentService.listAll()).hasSize(2);
    }
}
