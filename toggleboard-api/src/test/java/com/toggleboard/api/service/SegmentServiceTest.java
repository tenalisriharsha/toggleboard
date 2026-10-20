package com.toggleboard.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.api.domain.AuditAction;
import com.toggleboard.api.domain.Segment;
import com.toggleboard.api.domain.TargetingOperator;
import com.toggleboard.api.domain.TargetingRule;
import com.toggleboard.api.exception.ConflictException;
import com.toggleboard.api.exception.InvalidSegmentException;
import com.toggleboard.api.exception.NotFoundException;
import com.toggleboard.api.repository.memory.InMemoryAuditRepository;
import com.toggleboard.api.repository.memory.InMemoryEnvironmentRepository;
import com.toggleboard.api.repository.memory.InMemorySegmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SegmentServiceTest {

    private SegmentService segmentService;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(new InMemoryAuditRepository(), new ObjectMapper());
        EnvironmentService environmentService = new EnvironmentService(new InMemoryEnvironmentRepository(), auditService);
        environmentService.create("production", "Production", "alice");
        segmentService = new SegmentService(new InMemorySegmentRepository(), environmentService, auditService);
    }

    @Test
    void createsASegment() {
        List<TargetingRule> rules = List.of(new TargetingRule("plan", TargetingOperator.EQUALS, List.of("enterprise"), null));

        Segment segment = segmentService.create("production", "enterprise-users", "Enterprise Users", "desc", rules, "alice");

        assertThat(segment.id()).isNotBlank();
        assertThat(segment.rules()).isEqualTo(rules);
    }

    @Test
    void rejectsCreationInUnknownEnvironment() {
        List<TargetingRule> rules = List.of(new TargetingRule("plan", TargetingOperator.EQUALS, List.of("x"), null));

        assertThatThrownBy(() -> segmentService.create("unknown", "seg", "Seg", "desc", rules, "alice"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsDuplicateSegmentKey() {
        List<TargetingRule> rules = List.of(new TargetingRule("plan", TargetingOperator.EQUALS, List.of("x"), null));
        segmentService.create("production", "seg", "Seg", "desc", rules, "alice");

        assertThatThrownBy(() -> segmentService.create("production", "seg", "Again", "desc", rules, "alice"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsASegmentWithNoRules() {
        assertThatThrownBy(() -> segmentService.create("production", "seg", "Seg", "desc", List.of(), "alice"))
                .isInstanceOf(InvalidSegmentException.class);
    }

    @Test
    void rejectsAnEqualsRuleWithMultipleValues() {
        List<TargetingRule> rules = List.of(new TargetingRule("plan", TargetingOperator.EQUALS, List.of("a", "b"), null));

        assertThatThrownBy(() -> segmentService.create("production", "seg", "Seg", "desc", rules, "alice"))
                .isInstanceOf(InvalidSegmentException.class);
    }

    @Test
    void updateReplacesRulesAndRecordsAudit() {
        List<TargetingRule> initial = List.of(new TargetingRule("plan", TargetingOperator.EQUALS, List.of("enterprise"), null));
        segmentService.create("production", "seg", "Seg", "desc", initial, "alice");

        List<TargetingRule> updatedRules = List.of(new TargetingRule("plan", TargetingOperator.IN, List.of("enterprise", "pro"), null));
        Segment updated = segmentService.update("production", "seg", "Seg v2", "updated desc", updatedRules, "bob");

        assertThat(updated.rules()).isEqualTo(updatedRules);
        assertThat(auditService.listByEnvironment("production", null, null))
                .extracting(entry -> entry.action())
                .contains(AuditAction.UPDATE);
    }

    @Test
    void deleteRemovesTheSegment() {
        List<TargetingRule> rules = List.of(new TargetingRule("plan", TargetingOperator.EQUALS, List.of("x"), null));
        segmentService.create("production", "seg", "Seg", "desc", rules, "alice");

        segmentService.delete("production", "seg", "alice");

        assertThatThrownBy(() -> segmentService.getByKey("production", "seg")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listByEnvironmentReturnsOnlyThatEnvironmentsSegments() {
        List<TargetingRule> rules = List.of(new TargetingRule("plan", TargetingOperator.EQUALS, List.of("x"), null));
        segmentService.create("production", "seg-a", "A", "desc", rules, "alice");

        assertThat(segmentService.listByEnvironment("production")).extracting(Segment::key).containsExactly("seg-a");
    }
}
