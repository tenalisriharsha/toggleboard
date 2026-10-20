package com.toggleboard.api.service;

import com.toggleboard.api.domain.Environment;
import com.toggleboard.api.exception.ConflictException;
import com.toggleboard.api.exception.NotFoundException;
import com.toggleboard.api.repository.memory.InMemoryEnvironmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentServiceTest {

    private EnvironmentService environmentService;

    @BeforeEach
    void setUp() {
        environmentService = new EnvironmentService(new InMemoryEnvironmentRepository());
    }

    @Test
    void createGeneratesAUniqueApiKey() {
        Environment production = environmentService.create("production", "Production");
        Environment staging = environmentService.create("staging", "Staging");

        assertThat(production.apiKey()).isNotBlank().startsWith("tgb_");
        assertThat(staging.apiKey()).isNotEqualTo(production.apiKey());
    }

    @Test
    void createRejectsDuplicateKeys() {
        environmentService.create("production", "Production");

        assertThatThrownBy(() -> environmentService.create("production", "Prod Again"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getByKeyThrowsWhenMissing() {
        assertThatThrownBy(() -> environmentService.getByKey("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getByApiKeyFindsTheOwningEnvironment() {
        Environment environment = environmentService.create("production", "Production");

        assertThat(environmentService.getByApiKey(environment.apiKey())).isEqualTo(environment);
    }

    @Test
    void listAllReturnsEveryEnvironment() {
        environmentService.create("production", "Production");
        environmentService.create("staging", "Staging");

        assertThat(environmentService.listAll()).hasSize(2);
    }
}
