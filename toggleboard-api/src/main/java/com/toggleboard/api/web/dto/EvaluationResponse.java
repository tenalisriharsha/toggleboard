package com.toggleboard.api.web.dto;

import com.toggleboard.api.domain.EvaluationReason;
import com.toggleboard.api.domain.EvaluationResult;

public record EvaluationResponse(
        String flagKey,
        boolean enabled,
        String variantKey,
        String value,
        EvaluationReason reason
) {
    public static EvaluationResponse from(EvaluationResult result) {
        return new EvaluationResponse(result.flagKey(), result.enabled(), result.variantKey(), result.value(), result.reason());
    }
}
