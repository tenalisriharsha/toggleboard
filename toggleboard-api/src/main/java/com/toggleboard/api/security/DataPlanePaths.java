package com.toggleboard.api.security;

import java.util.List;

/**
 * Path patterns for the client SDK data-plane (flag evaluation and the SSE
 * change stream), shared by {@link ApiKeyAuthFilter} (which guards them) and
 * {@link RoleAuthFilter} (which excludes them, since the client SDK
 * authenticates with an API key, not an {@code X-Role} header).
 */
final class DataPlanePaths {

    static final String EVALUATE_PATTERN = "/api/v1/environments/{environmentKey}/flags/{flagKey}/evaluate";
    static final String STREAM_PATTERN = "/api/v1/environments/{environmentKey}/stream";
    static final List<String> ALL = List.of(EVALUATE_PATTERN, STREAM_PATTERN);

    private DataPlanePaths() {
    }
}
