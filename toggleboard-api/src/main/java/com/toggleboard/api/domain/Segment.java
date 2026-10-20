package com.toggleboard.api.domain;

import java.time.Instant;
import java.util.List;

/**
 * A reusable, named group of {@link TargetingRule}s, scoped to an
 * environment. A segment matches a subject only when ALL of its rules match
 * (AND semantics); flags reference segments by key.
 */
public record Segment(
        String id,
        String environmentKey,
        String key,
        String name,
        String description,
        List<TargetingRule> rules,
        Instant createdAt,
        Instant updatedAt
) {

    public boolean matches(java.util.Map<String, String> attributes) {
        return !rules.isEmpty() && rules.stream().allMatch(rule -> rule.matches(attributes));
    }

    public Segment withUpdatedAt(Instant timestamp) {
        return new Segment(id, environmentKey, key, name, description, rules, createdAt, timestamp);
    }
}
