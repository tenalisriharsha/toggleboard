package com.toggleboard.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Minimal role-based access control for the admin CRUD endpoints under
 * {@code /api/v1/environments/**}: reads (GET) are open to any caller, but
 * a mutation (POST/PUT/DELETE/PATCH) requires an {@code X-Role: admin}
 * header. The evaluation data-plane is excluded here, since it is already
 * guarded by {@link ApiKeyAuthFilter} and client SDKs don't carry a role.
 *
 * <p>This is a placeholder ahead of real identity/authorization in a later
 * phase: the role is self-declared by the caller, not verified against any
 * user store.
 */
@Component
public class RoleAuthFilter extends OncePerRequestFilter {

    private static final String PROTECTED_PATTERN = "/api/v1/environments/**";
    private static final String ROLE_HEADER = "X-Role";
    private static final Set<String> SAFE_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = RequestPaths.resolve(request);
        boolean isDataPlanePath = DataPlanePaths.ALL.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
        boolean isAdminPath = pathMatcher.match(PROTECTED_PATTERN, path) && !isDataPlanePath;

        if (!isAdminPath || SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String roleHeader = request.getHeader(ROLE_HEADER);
        if (roleHeader == null || roleHeader.isBlank()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing " + ROLE_HEADER + " header for this action");
            return;
        }

        Role role;
        try {
            role = Role.valueOf(roleHeader.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, ROLE_HEADER + " must be 'admin' or 'viewer'");
            return;
        }

        if (role != Role.ADMIN) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "viewer role cannot perform this action");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", status);
        payload.put("message", message);
        objectMapper.writeValue(response.getWriter(), payload);
    }
}
