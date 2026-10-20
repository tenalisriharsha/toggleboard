package com.toggleboard.api.web;

import com.toggleboard.api.domain.Environment;
import com.toggleboard.api.service.EnvironmentService;
import com.toggleboard.api.web.dto.CreateEnvironmentRequest;
import com.toggleboard.api.web.dto.EnvironmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/environments")
@Tag(name = "Environments", description = "Isolated deployment targets, each with its own API key")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @PostMapping
    @Operation(summary = "Create an environment and generate its API key")
    public ResponseEntity<EnvironmentResponse> create(@Valid @RequestBody CreateEnvironmentRequest request) {
        Environment environment = environmentService.create(request.key(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(EnvironmentResponse.from(environment));
    }

    @GetMapping
    @Operation(summary = "List all environments")
    public List<EnvironmentResponse> listAll() {
        return environmentService.listAll().stream().map(EnvironmentResponse::from).toList();
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get a single environment by key")
    public EnvironmentResponse getByKey(@PathVariable String key) {
        return EnvironmentResponse.from(environmentService.getByKey(key));
    }
}
