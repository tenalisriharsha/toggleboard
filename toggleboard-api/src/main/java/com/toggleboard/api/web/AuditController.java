package com.toggleboard.api.web;

import com.toggleboard.api.domain.AuditEntityType;
import com.toggleboard.api.service.AuditService;
import com.toggleboard.api.web.dto.AuditEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/environments/{environmentKey}/audit")
@Tag(name = "Audit", description = "History of every create/update/delete/kill-switch-toggle in an environment")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "List audit entries for an environment, newest first")
    public List<AuditEntryResponse> list(@PathVariable String environmentKey,
                                          @RequestParam(required = false) AuditEntityType entityType,
                                          @RequestParam(required = false) Integer limit) {
        return auditService.listByEnvironment(environmentKey, entityType, limit).stream()
                .map(AuditEntryResponse::from)
                .toList();
    }
}
