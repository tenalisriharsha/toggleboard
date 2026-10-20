package com.toggleboard.api.service;

import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.domain.Segment;
import com.toggleboard.api.domain.TargetingRule;
import com.toggleboard.api.domain.TargetingRuleValidation;
import com.toggleboard.api.exception.ConflictException;
import com.toggleboard.api.exception.InvalidSegmentException;
import com.toggleboard.api.exception.NotFoundException;
import com.toggleboard.api.repository.SegmentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SegmentService {

    private final SegmentRepository segmentRepository;
    private final EnvironmentService environmentService;
    private final AuditService auditService;

    public SegmentService(SegmentRepository segmentRepository, EnvironmentService environmentService, AuditService auditService) {
        this.segmentRepository = segmentRepository;
        this.environmentService = environmentService;
        this.auditService = auditService;
    }

    public Segment create(String environmentKey, String key, String name, String description,
                           List<TargetingRule> rules, String actor) {
        environmentService.getByKey(environmentKey);
        if (segmentRepository.existsByEnvironmentAndKey(environmentKey, key)) {
            throw new ConflictException("Segment '" + key + "' already exists in environment '" + environmentKey + "'");
        }
        validate(rules);

        Instant now = Instant.now();
        Segment segment = new Segment(UUID.randomUUID().toString(), environmentKey, key, name, description,
                rules, now, now);
        segmentRepository.save(segment);
        auditService.recordCreate(environmentKey, AuditEntityType.SEGMENT, key, actor, segment);
        return segment;
    }

    public Segment update(String environmentKey, String key, String name, String description,
                           List<TargetingRule> rules, String actor) {
        Segment existing = getByKey(environmentKey, key);
        validate(rules);

        Segment updated = new Segment(existing.id(), existing.environmentKey(), existing.key(), name, description,
                rules, existing.createdAt(), Instant.now());
        segmentRepository.save(updated);
        auditService.recordUpdate(environmentKey, AuditEntityType.SEGMENT, key, actor, existing, updated);
        return updated;
    }

    public Segment getByKey(String environmentKey, String key) {
        return segmentRepository.findByEnvironmentAndKey(environmentKey, key)
                .orElseThrow(() -> new NotFoundException("Segment '" + key + "' not found in environment '" + environmentKey + "'"));
    }

    public List<Segment> listByEnvironment(String environmentKey) {
        environmentService.getByKey(environmentKey);
        return segmentRepository.findAllByEnvironment(environmentKey);
    }

    public boolean existsByKey(String environmentKey, String key) {
        return segmentRepository.existsByEnvironmentAndKey(environmentKey, key);
    }

    /** Used when resolving a flag's referenced segments: a missing segment is simply skipped. */
    public Optional<Segment> findIfPresent(String environmentKey, String key) {
        return segmentRepository.findByEnvironmentAndKey(environmentKey, key);
    }

    public void delete(String environmentKey, String key, String actor) {
        Segment existing = getByKey(environmentKey, key);
        segmentRepository.deleteById(existing.id());
        auditService.recordDelete(environmentKey, AuditEntityType.SEGMENT, key, actor, existing);
    }

    private void validate(List<TargetingRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new InvalidSegmentException("A segment requires at least one targeting rule");
        }
        String problem = TargetingRuleValidation.firstProblem(rules);
        if (problem != null) {
            throw new InvalidSegmentException(problem);
        }
    }
}
