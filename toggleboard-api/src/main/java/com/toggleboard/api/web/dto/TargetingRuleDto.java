package com.toggleboard.api.web.dto;

import com.toggleboard.api.domain.TargetingOperator;
import com.toggleboard.api.domain.TargetingRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TargetingRuleDto(
        @NotBlank String attribute,
        @NotNull TargetingOperator operator,
        @NotEmpty List<String> values,
        String overrideVariantKey
) {
    public static TargetingRuleDto from(TargetingRule rule) {
        return new TargetingRuleDto(rule.attribute(), rule.operator(), rule.values(), rule.overrideVariantKey());
    }

    public TargetingRule toDomain() {
        return new TargetingRule(attribute, operator, values, overrideVariantKey);
    }
}
