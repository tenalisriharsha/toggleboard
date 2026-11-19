package com.toggleboard.api.web.dto;

import com.toggleboard.api.domain.Environment;

import java.time.Instant;

public record EnvironmentSummaryResponse(
        String key,
        String name,
        Instant createdAt
) {
    public static EnvironmentSummaryResponse from(Environment environment) {
        return new EnvironmentSummaryResponse(environment.key(), environment.name(), environment.createdAt());
    }
}
