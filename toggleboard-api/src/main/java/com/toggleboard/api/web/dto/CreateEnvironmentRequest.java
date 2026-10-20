package com.toggleboard.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateEnvironmentRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-_]*", message = "must be lowercase, alphanumeric, '-' or '_'") String key,
        @NotBlank String name
) {
}
