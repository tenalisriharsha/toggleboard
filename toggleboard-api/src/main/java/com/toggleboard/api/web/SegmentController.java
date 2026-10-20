package com.toggleboard.api.web;

import com.toggleboard.api.domain.Segment;
import com.toggleboard.api.domain.TargetingRule;
import com.toggleboard.api.service.SegmentService;
import com.toggleboard.api.web.dto.CreateSegmentRequest;
import com.toggleboard.api.web.dto.SegmentResponse;
import com.toggleboard.api.web.dto.TargetingRuleDto;
import com.toggleboard.api.web.dto.UpdateSegmentRequest;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/environments/{environmentKey}/segments")
@Tag(name = "Segments", description = "Reusable named groups of targeting rules, referenced by flags")
public class SegmentController {

    private final SegmentService segmentService;

    public SegmentController(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @PostMapping
    @Operation(summary = "Create a segment in an environment", description = "Requires X-Role: admin")
    public ResponseEntity<SegmentResponse> create(@PathVariable String environmentKey,
                                                   @Valid @RequestBody CreateSegmentRequest request,
                                                   @RequestHeader(value = "X-Actor", required = false) String actor) {
        Segment segment = segmentService.create(environmentKey, request.key(), request.name(),
                request.description(), toRules(request.rules()), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(SegmentResponse.from(segment));
    }

    @GetMapping
    @Operation(summary = "List all segments in an environment")
    public List<SegmentResponse> list(@PathVariable String environmentKey) {
        return segmentService.listByEnvironment(environmentKey).stream().map(SegmentResponse::from).toList();
    }

    @GetMapping("/{segmentKey}")
    @Operation(summary = "Get a single segment")
    public SegmentResponse get(@PathVariable String environmentKey, @PathVariable String segmentKey) {
        return SegmentResponse.from(segmentService.getByKey(environmentKey, segmentKey));
    }

    @PutMapping("/{segmentKey}")
    @Operation(summary = "Update a segment's rules", description = "Requires X-Role: admin")
    public SegmentResponse update(@PathVariable String environmentKey, @PathVariable String segmentKey,
                                   @Valid @RequestBody UpdateSegmentRequest request,
                                   @RequestHeader(value = "X-Actor", required = false) String actor) {
        Segment segment = segmentService.update(environmentKey, segmentKey, request.name(), request.description(),
                toRules(request.rules()), actor);
        return SegmentResponse.from(segment);
    }

    @DeleteMapping("/{segmentKey}")
    @Operation(summary = "Delete a segment", description = "Requires X-Role: admin")
    public ResponseEntity<Void> delete(@PathVariable String environmentKey, @PathVariable String segmentKey,
                                        @RequestHeader(value = "X-Actor", required = false) String actor) {
        segmentService.delete(environmentKey, segmentKey, actor);
        return ResponseEntity.noContent().build();
    }

    private List<TargetingRule> toRules(List<TargetingRuleDto> rules) {
        if (rules == null) {
            return List.of();
        }
        return rules.stream().map(TargetingRuleDto::toDomain).toList();
    }
}
