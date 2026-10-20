package com.toggleboard.api.web.dto;

import com.toggleboard.api.domain.FlagType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CreateFlagRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-_]*", message = "must be lowercase, alphanumeric, '-' or '_'") String key,
        @NotBlank String name,
        String description,
        @NotNull FlagType type,
        boolean enabled,
        @Min(0) @Max(100) int rolloutPercentage,
        @Valid List<VariantDto> variants,
        String defaultVariantKey
) {
}
