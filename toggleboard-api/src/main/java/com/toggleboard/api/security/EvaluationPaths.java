package com.toggleboard.api.security;

/**
 * The evaluation data-plane path pattern, shared by {@link ApiKeyAuthFilter}
 * (which guards it) and {@link RoleAuthFilter} (which excludes it, since the
 * client SDK authenticates with an API key, not an {@code X-Role} header).
 */
final class EvaluationPaths {

    static final String PATTERN = "/api/v1/environments/{environmentKey}/flags/{flagKey}/evaluate";

    private EvaluationPaths() {
    }
}
