package com.toggleboard.api.web.dto;

import com.toggleboard.api.domain.Variant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record VariantDto(
        @NotBlank String key,
        @NotBlank String value,
        @Min(0) @Max(100) int weight
) {
    public static VariantDto from(Variant variant) {
        return new VariantDto(variant.key(), variant.value(), variant.weight());
    }

    public Variant toDomain() {
        return new Variant(key, value, weight);
    }
}
