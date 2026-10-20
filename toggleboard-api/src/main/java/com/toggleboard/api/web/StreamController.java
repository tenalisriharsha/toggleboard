package com.toggleboard.api.web;

import com.toggleboard.api.service.EnvironmentService;
import com.toggleboard.api.streaming.FlagChangePublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The client SDK data-plane's push channel: subscribers receive a
 * {@code flag-change} SSE event every time a flag in this environment is
 * created, updated or deleted. Guarded by {@link com.toggleboard.api.security.ApiKeyAuthFilter},
 * same as the evaluation endpoint.
 */
@RestController
@Tag(name = "Streaming", description = "Client SDK data-plane: live push notifications when a flag changes")
public class StreamController {

    private final FlagChangePublisher flagChangePublisher;
    private final EnvironmentService environmentService;

    public StreamController(FlagChangePublisher flagChangePublisher, EnvironmentService environmentService) {
        this.flagChangePublisher = flagChangePublisher;
        this.environmentService = environmentService;
    }

    @GetMapping(value = "/api/v1/environments/{environmentKey}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to live flag-change events for an environment",
            description = "Requires the environment's API key in the X-API-Key header. Streams a flag-change "
                    + "SSE event every time a flag is created, updated or deleted.")
    public SseEmitter stream(@PathVariable String environmentKey) {
        environmentService.getByKey(environmentKey);
        return flagChangePublisher.subscribe(environmentKey);
    }
}
