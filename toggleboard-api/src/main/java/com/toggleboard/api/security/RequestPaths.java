package com.toggleboard.api.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code getServletPath()} returns "" when the DispatcherServlet owns the
 * default ("/") mapping, so the full path has to come from the URI instead.
 * Shared by every security filter that needs to pattern-match the request path.
 */
final class RequestPaths {

    private RequestPaths() {
    }

    static String resolve(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
