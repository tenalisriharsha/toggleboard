package com.toggleboard.api.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateFlagRequest(
        @NotBlank String name,
        String description,
        boolean enabled,
        @Min(0) @Max(100) int rolloutPercentage,
        @Valid List<VariantDto> variants,
        String defaultVariantKey,
        @Valid List<TargetingRuleDto> targetingRules,
        List<String> segmentKeys
) {
}
