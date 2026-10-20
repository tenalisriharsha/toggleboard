package com.toggleboard.api.web.dto;

import com.toggleboard.api.domain.AuditAction;
import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.domain.AuditEntry;

import java.time.Instant;

public record AuditEntryResponse(
        String id,
        String environmentKey,
        AuditEntityType entityType,
        String entityKey,
        AuditAction action,
        String actor,
        Instant timestamp,
        String before,
        String after
) {
    public static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(entry.id(), entry.environmentKey(), entry.entityType(), entry.entityKey(),
                entry.action(), entry.actor(), entry.timestamp(), entry.before(), entry.after());
    }
}
