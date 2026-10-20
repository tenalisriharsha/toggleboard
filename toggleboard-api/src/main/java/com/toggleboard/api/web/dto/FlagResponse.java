package com.toggleboard.api.web.dto;

import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.domain.FlagType;

import java.time.Instant;
import java.util.List;

public record FlagResponse(
        String id,
        String key,
        String environmentKey,
        String name,
        String description,
        FlagType type,
        boolean enabled,
        int rolloutPercentage,
        List<VariantDto> variants,
        String defaultVariantKey,
        List<TargetingRuleDto> targetingRules,
        List<String> segmentKeys,
        Instant createdAt,
        Instant updatedAt
) {
    public static FlagResponse from(Flag flag) {
        List<VariantDto> variants = flag.variants() == null ? List.of() :
                flag.variants().stream().map(VariantDto::from).toList();
        List<TargetingRuleDto> targetingRules = flag.targetingRules() == null ? List.of() :
                flag.targetingRules().stream().map(TargetingRuleDto::from).toList();
        List<String> segmentKeys = flag.segmentKeys() == null ? List.of() : flag.segmentKeys();
        return new FlagResponse(flag.id(), flag.key(), flag.environmentKey(), flag.name(), flag.description(),
                flag.type(), flag.enabled(), flag.rolloutPercentage(), variants, flag.defaultVariantKey(),
                targetingRules, segmentKeys, flag.createdAt(), flag.updatedAt());
    }
}
