package com.toggleboard.api.repository.memory;

import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.domain.AuditEntry;
import com.toggleboard.api.repository.AuditRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@Profile("!dynamodb")
public class InMemoryAuditRepository implements AuditRepository {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public AuditEntry save(AuditEntry entry) {
        entries.add(entry);
        return entry;
    }

    @Override
    public List<AuditEntry> findByEnvironment(String environmentKey, AuditEntityType entityTypeFilter, int limit) {
        return entries.stream()
                .filter(e -> e.environmentKey().equals(environmentKey))
                .filter(e -> entityTypeFilter == null || e.entityType() == entityTypeFilter)
                .sorted(Comparator.comparing(AuditEntry::timestamp).reversed())
                .limit(limit)
                .toList();
    }
}
