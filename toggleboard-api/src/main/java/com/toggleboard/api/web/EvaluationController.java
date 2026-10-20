package com.toggleboard.api.web;

import com.toggleboard.api.domain.EvaluationResult;
import com.toggleboard.api.service.FlagService;
import com.toggleboard.api.web.dto.EvaluationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The client SDK data-plane: evaluates a single flag for a subject. Unlike
 * the admin CRUD endpoints, these require a valid environment API key (see
 * {@link com.toggleboard.api.security.ApiKeyAuthFilter}).
 */
@RestController
@RequestMapping("/api/v1/environments/{environmentKey}/flags/{flagKey}/evaluate")
@Tag(name = "Evaluation", description = "Client SDK data-plane: resolve a flag's value for a subject")
public class EvaluationController {

    private final FlagService flagService;

    public EvaluationController(FlagService flagService) {
        this.flagService = flagService;
    }

    @GetMapping
    @Operation(summary = "Evaluate a flag for a subject", description = "Requires the environment's API key in the X-API-Key header")
    public EvaluationResponse evaluate(@PathVariable String environmentKey, @PathVariable String flagKey,
                                        @Parameter(description = "Stable identifier for the user/device being evaluated")
                                        @RequestParam @NotBlank String subjectKey) {
        EvaluationResult result = flagService.evaluate(environmentKey, flagKey, subjectKey);
        return EvaluationResponse.from(result);
    }
}
