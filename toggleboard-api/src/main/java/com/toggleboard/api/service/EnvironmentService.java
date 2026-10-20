package com.toggleboard.api.service;

import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.domain.Environment;
import com.toggleboard.api.exception.ConflictException;
import com.toggleboard.api.exception.NotFoundException;
import com.toggleboard.api.repository.EnvironmentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final AuditService auditService;

    public EnvironmentService(EnvironmentRepository environmentRepository, AuditService auditService) {
        this.environmentRepository = environmentRepository;
        this.auditService = auditService;
    }

    public Environment create(String key, String name, String actor) {
        if (environmentRepository.existsByKey(key)) {
            throw new ConflictException("Environment with key '" + key + "' already exists");
        }
        Environment environment = new Environment(key, name, generateApiKey(), Instant.now());
        environmentRepository.save(environment);
        auditService.recordCreate(key, AuditEntityType.ENVIRONMENT, key, actor, environment);
        return environment;
    }

    public Environment getByKey(String key) {
        return environmentRepository.findByKey(key)
                .orElseThrow(() -> new NotFoundException("Environment '" + key + "' not found"));
    }

    public Environment getByApiKey(String apiKey) {
        return environmentRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new NotFoundException("No environment matches the given API key"));
    }

    public List<Environment> listAll() {
        return environmentRepository.findAll();
    }

    private String generateApiKey() {
        return "tgb_" + UUID.randomUUID().toString().replace("-", "");
    }
}
