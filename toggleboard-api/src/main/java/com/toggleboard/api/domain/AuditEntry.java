package com.toggleboard.api.domain;

import java.time.Instant;

/**
 * A single recorded change: who ({@code actor}) did what ({@code action}) to
 * which entity, with JSON snapshots of its state before and after. Written
 * once per mutating service call and never updated.
 */
public record AuditEntry(
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
}
