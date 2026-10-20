package com.toggleboard.api.service;

import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.domain.EvaluationResult;
import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.domain.FlagEvaluator;
import com.toggleboard.api.domain.FlagType;
import com.toggleboard.api.domain.Segment;
import com.toggleboard.api.domain.TargetingRule;
import com.toggleboard.api.domain.TargetingRuleValidation;
import com.toggleboard.api.domain.Variant;
import com.toggleboard.api.exception.ConflictException;
import com.toggleboard.api.exception.InvalidFlagException;
import com.toggleboard.api.exception.NotFoundException;
import com.toggleboard.api.repository.FlagRepository;
import com.toggleboard.api.streaming.FlagChangeAction;
import com.toggleboard.api.streaming.FlagChangeEvent;
import com.toggleboard.api.streaming.FlagChangePublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FlagService {

    private final FlagRepository flagRepository;
    private final EnvironmentService environmentService;
    private final SegmentService segmentService;
    private final FlagEvaluator flagEvaluator;
    private final AuditService auditService;
    private final FlagChangePublisher flagChangePublisher;

    public FlagService(FlagRepository flagRepository, EnvironmentService environmentService,
                        SegmentService segmentService, FlagEvaluator flagEvaluator, AuditService auditService,
                        FlagChangePublisher flagChangePublisher) {
        this.flagRepository = flagRepository;
        this.environmentService = environmentService;
        this.segmentService = segmentService;
        this.flagEvaluator = flagEvaluator;
        this.auditService = auditService;
        this.flagChangePublisher = flagChangePublisher;
    }

    public Flag create(String environmentKey, String key, String name, String description, FlagType type,
                        boolean enabled, int rolloutPercentage, List<Variant> variants, String defaultVariantKey,
                        List<TargetingRule> targetingRules, List<String> segmentKeys, String actor) {
        environmentService.getByKey(environmentKey);

        if (flagRepository.existsByEnvironmentAndKey(environmentKey, key)) {
            throw new ConflictException("Flag '" + key + "' already exists in environment '" + environmentKey + "'");
        }
        List<TargetingRule> rules = targetingRules == null ? List.of() : targetingRules;
        List<String> segments = segmentKeys == null ? List.of() : segmentKeys;
        validate(type, rolloutPercentage, variants, defaultVariantKey, rules, environmentKey, segments);

        Instant now = Instant.now();
        Flag flag = new Flag(UUID.randomUUID().toString(), key, environmentKey, name, description, type,
                enabled, rolloutPercentage, variants, defaultVariantKey, rules, segments, now, now);
        flagRepository.save(flag);
        auditService.recordCreate(environmentKey, AuditEntityType.FLAG, key, actor, flag);
        flagChangePublisher.publish(new FlagChangeEvent(environmentKey, key, FlagChangeAction.CREATED, flag, flag.updatedAt()));
        return flag;
    }

    public Flag update(String environmentKey, String key, String name, String description, boolean enabled,
                        int rolloutPercentage, List<Variant> variants, String defaultVariantKey,
                        List<TargetingRule> targetingRules, List<String> segmentKeys, String actor) {
        Flag existing = getByKey(environmentKey, key);
        List<TargetingRule> rules = targetingRules == null ? List.of() : targetingRules;
        List<String> segments = segmentKeys == null ? List.of() : segmentKeys;
        validate(existing.type(), rolloutPercentage, variants, defaultVariantKey, rules, environmentKey, segments);

        Flag updated = new Flag(existing.id(), existing.key(), existing.environmentKey(), name, description,
                existing.type(), enabled, rolloutPercentage, variants, defaultVariantKey, rules, segments,
                existing.createdAt(), Instant.now());
        flagRepository.save(updated);

        if (existing.enabled() != updated.enabled()) {
            auditService.recordKillSwitch(environmentKey, key, actor, existing, updated, updated.enabled());
        } else {
            auditService.recordUpdate(environmentKey, AuditEntityType.FLAG, key, actor, existing, updated);
        }
        flagChangePublisher.publish(new FlagChangeEvent(environmentKey, key, FlagChangeAction.UPDATED, updated, updated.updatedAt()));
        return updated;
    }

    public Flag getByKey(String environmentKey, String key) {
        return flagRepository.findByEnvironmentAndKey(environmentKey, key)
                .orElseThrow(() -> new NotFoundException("Flag '" + key + "' not found in environment '" + environmentKey + "'"));
    }

    public List<Flag> listByEnvironment(String environmentKey) {
        environmentService.getByKey(environmentKey);
        return flagRepository.findAllByEnvironment(environmentKey);
    }

    public void delete(String environmentKey, String key, String actor) {
        Flag existing = getByKey(environmentKey, key);
        flagRepository.deleteById(existing.id());
        auditService.recordDelete(environmentKey, AuditEntityType.FLAG, key, actor, existing);
        flagChangePublisher.publish(new FlagChangeEvent(environmentKey, key, FlagChangeAction.DELETED, existing, Instant.now()));
    }

    public EvaluationResult evaluate(String environmentKey, String key, String subjectKey, Map<String, String> attributes) {
        Flag flag = getByKey(environmentKey, key);
        List<Segment> segments = flag.segmentKeys().stream()
                .flatMap(segmentKey -> segmentService.findIfPresent(environmentKey, segmentKey).stream())
                .toList();
        return flagEvaluator.evaluate(flag, subjectKey, attributes, segments);
    }

    private void validate(FlagType type, int rolloutPercentage, List<Variant> variants, String defaultVariantKey,
                           List<TargetingRule> targetingRules, String environmentKey, List<String> segmentKeys) {
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new InvalidFlagException("rolloutPercentage must be between 0 and 100");
        }
        if (type == FlagType.MULTIVARIATE) {
            if (variants == null || variants.isEmpty()) {
                throw new InvalidFlagException("Multivariate flags require at least one variant");
            }
            int totalWeight = variants.stream().mapToInt(Variant::weight).sum();
            if (totalWeight != 100) {
                throw new InvalidFlagException("Variant weights must sum to 100, got " + totalWeight);
            }
            boolean hasDefault = variants.stream().anyMatch(v -> v.key().equals(defaultVariantKey));
            if (defaultVariantKey == null || !hasDefault) {
                throw new InvalidFlagException("defaultVariantKey must match one of the variant keys");
            }
        }
        String targetingProblem = TargetingRuleValidation.firstProblem(targetingRules);
        if (targetingProblem != null) {
            throw new InvalidFlagException(targetingProblem);
        }
        for (TargetingRule rule : targetingRules) {
            if (rule.overrideVariantKey() != null && type != FlagType.MULTIVARIATE) {
                throw new InvalidFlagException("overrideVariantKey is only valid on multivariate flags");
            }
        }
        for (String segmentKey : segmentKeys) {
            if (!segmentService.existsByKey(environmentKey, segmentKey)) {
                throw new InvalidFlagException("Unknown segment '" + segmentKey + "' in environment '" + environmentKey + "'");
            }
        }
    }
}
