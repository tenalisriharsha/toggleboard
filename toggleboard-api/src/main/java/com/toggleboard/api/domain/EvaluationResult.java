package com.toggleboard.api.domain;

public record EvaluationResult(
        String flagKey,
        boolean enabled,
        String variantKey,
        String value,
        EvaluationReason reason
) {
}
