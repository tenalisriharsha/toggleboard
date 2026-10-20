package com.toggleboard.api.web.dto;

import com.toggleboard.api.domain.Segment;

import java.time.Instant;
import java.util.List;

public record SegmentResponse(
        String id,
        String environmentKey,
        String key,
        String name,
        String description,
        List<TargetingRuleDto> rules,
        Instant createdAt,
        Instant updatedAt
) {
    public static SegmentResponse from(Segment segment) {
        List<TargetingRuleDto> rules = segment.rules().stream().map(TargetingRuleDto::from).toList();
        return new SegmentResponse(segment.id(), segment.environmentKey(), segment.key(), segment.name(),
                segment.description(), rules, segment.createdAt(), segment.updatedAt());
    }
}
