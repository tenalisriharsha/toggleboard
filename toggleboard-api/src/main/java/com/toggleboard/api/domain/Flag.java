package com.toggleboard.api.domain;

import java.time.Instant;
import java.util.List;

/**
 * A feature flag scoped to a single environment. Boolean flags are governed by
 * {@code rolloutPercentage}; multivariate flags are governed by the weights on
 * {@code variants}. {@code enabled == false} is the kill switch: it forces the
 * flag off regardless of rollout or targeting.
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
        Instant createdAt,
        Instant updatedAt
) {

    public Flag withUpdatedAt(Instant timestamp) {
        return new Flag(id, key, environmentKey, name, description, type, enabled,
                rolloutPercentage, variants, defaultVariantKey, createdAt, timestamp);
    }
}
