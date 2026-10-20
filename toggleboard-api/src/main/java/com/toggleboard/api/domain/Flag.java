package com.toggleboard.api.domain;

import java.time.Instant;
import java.util.List;

/**
 * A feature flag scoped to a single environment. Boolean flags are governed by
 * {@code rolloutPercentage}; multivariate flags are governed by the weights on
 * {@code variants}. {@code enabled == false} is the kill switch: it forces the
 * flag off regardless of rollout or targeting. {@code targetingRules} are
 * matched directly against subject attributes (ORed with each other and with
 * every segment named in {@code segmentKeys}) before falling back to the
 * percentage rollout.
 */
public record Flag(
        String id,
        String key,
        String environmentKey,
        String name,
        String description,
        FlagType type,
        boolean enabled,
        int rolloutPercentage,
        List<Variant> variants,
        String defaultVariantKey,
        List<TargetingRule> targetingRules,
        List<String> segmentKeys,
        Instant createdAt,
        Instant updatedAt
) {

    public Flag withUpdatedAt(Instant timestamp) {
        return new Flag(id, key, environmentKey, name, description, type, enabled, rolloutPercentage,
                variants, defaultVariantKey, targetingRules, segmentKeys, createdAt, timestamp);
    }
}
