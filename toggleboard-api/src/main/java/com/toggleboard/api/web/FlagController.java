package com.toggleboard.api.web;

import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.service.FlagService;
import com.toggleboard.api.web.dto.CreateFlagRequest;
import com.toggleboard.api.web.dto.FlagResponse;
import com.toggleboard.api.web.dto.UpdateFlagRequest;
import com.toggleboard.api.web.dto.VariantDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/environments/{environmentKey}/flags")
@Tag(name = "Flags", description = "Boolean and multivariate feature flags, scoped to an environment")
public class FlagController {

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    @PostMapping
    @Operation(summary = "Create a flag in an environment")
    public ResponseEntity<FlagResponse> create(@PathVariable String environmentKey,
                                                @Valid @RequestBody CreateFlagRequest request) {
        Flag flag = flagService.create(environmentKey, request.key(), request.name(), request.description(),
                request.type(), request.enabled(), request.rolloutPercentage(), toDomain(request.variants()),
                request.defaultVariantKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(FlagResponse.from(flag));
    }

    @GetMapping
    @Operation(summary = "List all flags in an environment")
    public List<FlagResponse> list(@PathVariable String environmentKey) {
        return flagService.listByEnvironment(environmentKey).stream().map(FlagResponse::from).toList();
    }

    @GetMapping("/{flagKey}")
    @Operation(summary = "Get a single flag")
    public FlagResponse get(@PathVariable String environmentKey, @PathVariable String flagKey) {
        return FlagResponse.from(flagService.getByKey(environmentKey, flagKey));
    }

    @PutMapping("/{flagKey}")
    @Operation(summary = "Update a flag's targeting, rollout or kill switch state")
    public FlagResponse update(@PathVariable String environmentKey, @PathVariable String flagKey,
                                @Valid @RequestBody UpdateFlagRequest request) {
        Flag flag = flagService.update(environmentKey, flagKey, request.name(), request.description(),
                request.enabled(), request.rolloutPercentage(), toDomain(request.variants()), request.defaultVariantKey());
        return FlagResponse.from(flag);
    }

    @DeleteMapping("/{flagKey}")
    @Operation(summary = "Delete a flag")
    public ResponseEntity<Void> delete(@PathVariable String environmentKey, @PathVariable String flagKey) {
        flagService.delete(environmentKey, flagKey);
        return ResponseEntity.noContent().build();
    }

    private List<com.toggleboard.api.domain.Variant> toDomain(List<VariantDto> variants) {
        if (variants == null) {
            return List.of();
        }
        return variants.stream().map(VariantDto::toDomain).toList();
    }
}
