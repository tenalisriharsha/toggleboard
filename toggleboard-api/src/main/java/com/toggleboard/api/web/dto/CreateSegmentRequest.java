package com.toggleboard.api.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CreateSegmentRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-_]*", message = "must be lowercase, alphanumeric, '-' or '_'") String key,
        @NotBlank String name,
        String description,
        @NotEmpty @Valid List<TargetingRuleDto> rules
) {
}
