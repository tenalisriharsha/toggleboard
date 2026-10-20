package com.toggleboard.api.domain;

import java.util.List;

/**
 * Shared structural validation for {@link TargetingRule} lists, used by both
 * flag-level rules and segment rules. Returns a human-readable problem
 * description, or {@code null} when the rules are well-formed; callers wrap
 * this into whichever typed exception fits their entity.
 */
public final class TargetingRuleValidation {

    private TargetingRuleValidation() {
    }

    public static String firstProblem(List<TargetingRule> rules) {
        if (rules == null) {
            return null;
        }
        for (TargetingRule rule : rules) {
            if (rule.attribute() == null || rule.attribute().isBlank()) {
                return "targeting rule attribute must not be blank";
            }
            if (rule.operator() == null) {
                return "targeting rule operator is required";
            }
            if (rule.values() == null || rule.values().isEmpty()) {
                return "targeting rule values must not be empty";
            }
            if (rule.operator() == TargetingOperator.EQUALS && rule.values().size() != 1) {
                return "EQUALS targeting rules take exactly one value";
            }
        }
        return null;
    }
}
