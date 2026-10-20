package com.toggleboard.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.api.domain.AuditAction;
import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.domain.AuditEntry;
import com.toggleboard.api.repository.AuditRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records one {@link AuditEntry} per mutating call, with JSON snapshots of
 * the entity before and after the change. A kill-switch flip is recorded as
 * its own {@link AuditAction} rather than a plain update, so the audit trail
 * makes it obvious when a flag was turned on or off.
 */
@Service
public class AuditService {

    private static final int DEFAULT_LIMIT = 50;

    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    public void recordCreate(String environmentKey, AuditEntityType entityType, String entityKey, String actor, Object after) {
        record(environmentKey, entityType, entityKey, AuditAction.CREATE, actor, null, after);
    }

    public void recordUpdate(String environmentKey, AuditEntityType entityType, String entityKey, String actor, Object before, Object after) {
        record(environmentKey, entityType, entityKey, AuditAction.UPDATE, actor, before, after);
    }

    public void recordKillSwitch(String environmentKey, String flagKey, String actor, Object before, Object after, boolean enabledAfter) {
        AuditAction action = enabledAfter ? AuditAction.KILL_SWITCH_ENABLED : AuditAction.KILL_SWITCH_DISABLED;
        record(environmentKey, AuditEntityType.FLAG, flagKey, action, actor, before, after);
    }

    public void recordDelete(String environmentKey, AuditEntityType entityType, String entityKey, String actor, Object before) {
        record(environmentKey, entityType, entityKey, AuditAction.DELETE, actor, before, null);
    }

    public List<AuditEntry> listByEnvironment(String environmentKey, AuditEntityType entityTypeFilter, Integer limit) {
        return auditRepository.findByEnvironment(environmentKey, entityTypeFilter, limit == null ? DEFAULT_LIMIT : limit);
    }

    private void record(String environmentKey, AuditEntityType entityType, String entityKey, AuditAction action,
                         String actor, Object before, Object after) {
        AuditEntry entry = new AuditEntry(UUID.randomUUID().toString(), environmentKey, entityType, entityKey,
                action, actor == null || actor.isBlank() ? "system" : actor, Instant.now(),
                toJson(before), toJson(after));
        auditRepository.save(entry);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "<unserializable>";
        }
    }
}
