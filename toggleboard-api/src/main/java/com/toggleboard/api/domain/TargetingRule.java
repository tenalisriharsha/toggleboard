package com.toggleboard.api.domain;

import java.util.List;

/**
 * Matches a subject's attributes (e.g. {@code country=US}) ahead of the
 * percentage rollout. A rule on a {@link Flag} is evaluated directly; rules
 * inside a {@link Segment} are ANDed together, while a flag's own rules and
 * its referenced segments are ORed against each other.
 *
 * <p>{@code overrideVariantKey} only applies to multivariate flags matched
 * directly on the flag (not via a segment): when set, a match resolves to
 * that variant instead of falling through to the weighted rollout pick.
 */
public record TargetingRule(
        String attribute,
        TargetingOperator operator,
        List<String> values,
        String overrideVariantKey
) {

    public boolean matches(java.util.Map<String, String> attributes) {
        String actual = attributes.get(attribute);
        if (actual == null) {
            return false;
        }
        return switch (operator) {
            case EQUALS -> !values.isEmpty() && actual.equals(values.get(0));
            case IN -> values.contains(actual);
            case CONTAINS -> values.stream().anyMatch(actual::contains);
        };
    }
}
