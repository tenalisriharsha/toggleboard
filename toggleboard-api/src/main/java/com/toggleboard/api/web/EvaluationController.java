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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The client SDK data-plane: evaluates a single flag for a subject. Unlike
 * the admin CRUD endpoints, these require a valid environment API key (see
 * {@link com.toggleboard.api.security.ApiKeyAuthFilter}).
 */
@RestController
@RequestMapping("/api/v1/environments/{environmentKey}/flags/{flagKey}/evaluate")
@Tag(name = "Evaluation", description = "Client SDK data-plane: resolve a flag's value for a subject")
public class EvaluationController {

    private static final String SUBJECT_KEY_PARAM = "subjectKey";

    private final FlagService flagService;

    public EvaluationController(FlagService flagService) {
        this.flagService = flagService;
    }

    @GetMapping
    @Operation(summary = "Evaluate a flag for a subject",
            description = "Requires the environment's API key in the X-API-Key header. Any extra query "
                    + "parameters besides subjectKey are passed through as subject attributes for targeting rules.")
    public EvaluationResponse evaluate(@PathVariable String environmentKey, @PathVariable String flagKey,
                                        @Parameter(description = "Stable identifier for the user/device being evaluated")
                                        @RequestParam @NotBlank String subjectKey,
                                        @RequestParam Map<String, String> allParams) {
        Map<String, String> attributes = new LinkedHashMap<>(allParams);
        attributes.remove(SUBJECT_KEY_PARAM);
        EvaluationResult result = flagService.evaluate(environmentKey, flagKey, subjectKey, attributes);
        return EvaluationResponse.from(result);
    }
}
