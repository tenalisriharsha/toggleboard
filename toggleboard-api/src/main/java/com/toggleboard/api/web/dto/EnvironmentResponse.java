package com.toggleboard.api.web.dto;

import com.toggleboard.api.domain.Environment;

import java.time.Instant;

public record EnvironmentResponse(
        String key,
        String name,
        String apiKey,
        Instant createdAt
) {
    public static EnvironmentResponse from(Environment environment) {
        return new EnvironmentResponse(environment.key(), environment.name(), environment.apiKey(), environment.createdAt());
    }
}
