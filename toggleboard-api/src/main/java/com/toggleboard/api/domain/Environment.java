package com.toggleboard.api.domain;

import java.time.Instant;

/**
 * An isolated deployment target (e.g. "production", "staging"). Each
 * environment has its own API key used by client SDKs to evaluate flags.
 */
public record Environment(
        String key,
        String name,
        String apiKey,
        Instant createdAt
) {
}
