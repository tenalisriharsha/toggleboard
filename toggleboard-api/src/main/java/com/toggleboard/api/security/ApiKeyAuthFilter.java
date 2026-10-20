package com.toggleboard.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.api.domain.Environment;
import com.toggleboard.api.repository.EnvironmentRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Guards the evaluation data-plane: a request for
 * {@code /api/v1/environments/{environmentKey}/flags/{flagKey}/evaluate} must
 * carry an {@code X-API-Key} header matching that environment's key. Admin
 * CRUD endpoints are untouched here; role-based access for those arrives in
 * a later phase.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String PROTECTED_PATTERN = EvaluationPaths.PATTERN;
    private static final String API_KEY_HEADER = "X-API-Key";

    private final EnvironmentRepository environmentRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiKeyAuthFilter(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = requestPath(request);
        if (!pathMatcher.match(PROTECTED_PATTERN, path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Map<String, String> pathVariables = pathMatcher.extractUriTemplateVariables(PROTECTED_PATTERN, path);
        String environmentKey = pathVariables.get("environmentKey");
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing " + API_KEY_HEADER + " header");
            return;
        }

        Optional<Environment> environment = environmentRepository.findByKey(environmentKey);
        if (environment.isEmpty() || !environment.get().apiKey().equals(apiKey)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "API key does not match environment '" + environmentKey + "'");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String requestPath(HttpServletRequest request) {
        return RequestPaths.resolve(request);
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
