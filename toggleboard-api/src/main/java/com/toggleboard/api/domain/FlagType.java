package com.toggleboard.api.domain;

/**
 * A boolean flag resolves to true/false via rollout percentage.
 * A multivariate flag resolves to one of several weighted variants.
 */
public enum FlagType {
    BOOLEAN,
    MULTIVARIATE
}
