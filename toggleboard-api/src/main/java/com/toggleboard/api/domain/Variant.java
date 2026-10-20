package com.toggleboard.api.domain;

/**
 * One possible outcome of a multivariate flag, with its own rollout weight
 * (0-100, weights across all variants of a flag must sum to 100).
 */
public record Variant(String key, String value, int weight) {
}
