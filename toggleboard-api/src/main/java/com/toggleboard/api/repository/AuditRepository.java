package com.toggleboard.api.repository;

import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.domain.AuditEntry;

import java.util.List;

public interface AuditRepository {

    AuditEntry save(AuditEntry entry);

    /**
     * All entries for an environment, newest first, optionally filtered by
     * entity type and capped at {@code limit}.
     */
    List<AuditEntry> findByEnvironment(String environmentKey, AuditEntityType entityTypeFilter, int limit);
}
