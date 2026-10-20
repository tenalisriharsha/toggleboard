package com.toggleboard.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toggleboard.api.domain.AuditAction;
import com.toggleboard.api.domain.EvaluationReason;
import com.toggleboard.api.domain.EvaluationResult;
import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.domain.FlagEvaluator;
import com.toggleboard.api.domain.FlagType;
import com.toggleboard.api.domain.StableHashRolloutEngine;
import com.toggleboard.api.domain.TargetingOperator;
import com.toggleboard.api.domain.TargetingRule;
import com.toggleboard.api.domain.Variant;
import com.toggleboard.api.exception.ConflictException;
import com.toggleboard.api.exception.InvalidFlagException;
import com.toggleboard.api.exception.NotFoundException;
import com.toggleboard.api.repository.memory.InMemoryAuditRepository;
import com.toggleboard.api.repository.memory.InMemoryEnvironmentRepository;
import com.toggleboard.api.repository.memory.InMemoryFlagRepository;
import com.toggleboard.api.repository.memory.InMemorySegmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagServiceTest {

    private FlagService flagService;
    private EnvironmentService environmentService;
    private SegmentService segmentService;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(new InMemoryAuditRepository(), new ObjectMapper());
        environmentService = new EnvironmentService(new InMemoryEnvironmentRepository(), auditService);
        environmentService.create("production", "Production", "alice");
        segmentService = new SegmentService(new InMemorySegmentRepository(), environmentService, auditService);
        flagService = new FlagService(new InMemoryFlagRepository(), environmentService, segmentService,
                new FlagEvaluator(new StableHashRolloutEngine()), auditService);
    }

    @Test
    void createsABooleanFlag() {
        Flag flag = flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN,
                true, 25, List.of(), null, List.of(), List.of(), "alice");

        assertThat(flag.id()).isNotBlank();
        assertThat(flag.key()).isEqualTo("new-nav");
        assertThat(flag.rolloutPercentage()).isEqualTo(25);
    }

    @Test
    void rejectsCreationInUnknownEnvironment() {
        assertThatThrownBy(() -> flagService.create("unknown", "flag", "Flag", "desc", FlagType.BOOLEAN,
                true, 0, List.of(), null, List.of(), List.of(), "alice"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsDuplicateFlagKeyWithinSameEnvironment() {
        flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN, true, 0,
                List.of(), null, List.of(), List.of(), "alice");

        assertThatThrownBy(() -> flagService.create("production", "new-nav", "Again", "desc", FlagType.BOOLEAN,
                true, 0, List.of(), null, List.of(), List.of(), "alice"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsRolloutPercentageOutOfRange() {
        assertThatThrownBy(() -> flagService.create("production", "flag", "Flag", "desc", FlagType.BOOLEAN,
                true, 150, List.of(), null, List.of(), List.of(), "alice"))
                .isInstanceOf(InvalidFlagException.class);
    }

    @Test
    void rejectsMultivariateFlagWhoseWeightsDoNotSumToHundred() {
        List<Variant> variants = List.of(new Variant("a", "A", 40), new Variant("b", "B", 40));

        assertThatThrownBy(() -> flagService.create("production", "experiment", "Experiment", "desc",
                FlagType.MULTIVARIATE, true, 0, variants, "a", List.of(), List.of(), "alice"))
                .isInstanceOf(InvalidFlagException.class)
                .hasMessageContaining("sum to 100");
    }

    @Test
    void rejectsMultivariateFlagWithUnknownDefaultVariant() {
        List<Variant> variants = List.of(new Variant("a", "A", 50), new Variant("b", "B", 50));

        assertThatThrownBy(() -> flagService.create("production", "experiment", "Experiment", "desc",
                FlagType.MULTIVARIATE, true, 0, variants, "c", List.of(), List.of(), "alice"))
                .isInstanceOf(InvalidFlagException.class);
    }

    @Test
    void rejectsOverrideVariantKeyOnABooleanFlag() {
        TargetingRule rule = new TargetingRule("country", TargetingOperator.EQUALS, List.of("US"), "on");

        assertThatThrownBy(() -> flagService.create("production", "flag", "Flag", "desc", FlagType.BOOLEAN,
                true, 0, List.of(), null, List.of(rule), List.of(), "alice"))
                .isInstanceOf(InvalidFlagException.class);
    }

    @Test
    void rejectsAReferenceToAnUnknownSegment() {
        assertThatThrownBy(() -> flagService.create("production", "flag", "Flag", "desc", FlagType.BOOLEAN,
                true, 0, List.of(), null, List.of(), List.of("no-such-segment"), "alice"))
                .isInstanceOf(InvalidFlagException.class)
                .hasMessageContaining("no-such-segment");
    }

    @Test
    void acceptsAReferenceToAnExistingSegment() {
        segmentService.create("production", "beta-users", "Beta Users", "desc",
                List.of(new TargetingRule("beta", TargetingOperator.EQUALS, List.of("true"), null)), "alice");

        Flag flag = flagService.create("production", "flag", "Flag", "desc", FlagType.BOOLEAN,
                true, 0, List.of(), null, List.of(), List.of("beta-users"), "alice");

        assertThat(flag.segmentKeys()).containsExactly("beta-users");
    }

    @Test
    void updateChangesMutableFieldsButKeepsIdentityAndType() {
        Flag created = flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN,
                true, 25, List.of(), null, List.of(), List.of(), "alice");

        Flag updated = flagService.update("production", "new-nav", "New Nav v2", "updated desc", false, 75,
                List.of(), null, List.of(), List.of(), "alice");

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.name()).isEqualTo("New Nav v2");
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.rolloutPercentage()).isEqualTo(75);
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
    }

    @Test
    void updateThatFlipsEnabledRecordsAKillSwitchAuditAction() {
        flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN,
                true, 25, List.of(), null, List.of(), List.of(), "alice");

        flagService.update("production", "new-nav", "New Nav", "desc", false, 25,
                List.of(), null, List.of(), List.of(), "bob");

        assertThat(auditService.listByEnvironment("production", null, null))
                .extracting(entry -> entry.action())
                .contains(AuditAction.KILL_SWITCH_DISABLED);
    }

    @Test
    void updateThatDoesNotTouchEnabledRecordsAPlainUpdate() {
        flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN,
                true, 25, List.of(), null, List.of(), List.of(), "alice");

        flagService.update("production", "new-nav", "New Nav v2", "desc", true, 50,
                List.of(), null, List.of(), List.of(), "bob");

        assertThat(auditService.listByEnvironment("production", null, null))
                .extracting(entry -> entry.action())
                .contains(AuditAction.UPDATE);
    }

    @Test
    void deleteRemovesTheFlag() {
        flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN, true, 0,
                List.of(), null, List.of(), List.of(), "alice");

        flagService.delete("production", "new-nav", "alice");

        assertThatThrownBy(() -> flagService.getByKey("production", "new-nav")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void evaluateDelegatesToFlagEvaluator() {
        flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN, true, 100,
                List.of(), null, List.of(), List.of(), "alice");

        EvaluationResult result = flagService.evaluate("production", "new-nav", "user-1", Map.of());

        assertThat(result.enabled()).isTrue();
    }

    @Test
    void evaluateResolvesReferencedSegments() {
        segmentService.create("production", "beta-users", "Beta Users", "desc",
                List.of(new TargetingRule("beta", TargetingOperator.EQUALS, List.of("true"), null)), "alice");
        flagService.create("production", "gated", "Gated", "desc", FlagType.BOOLEAN, true, 0,
                List.of(), null, List.of(), List.of("beta-users"), "alice");

        EvaluationResult matched = flagService.evaluate("production", "gated", "user-1", Map.of("beta", "true"));
        EvaluationResult unmatched = flagService.evaluate("production", "gated", "user-1", Map.of("beta", "false"));

        assertThat(matched.enabled()).isTrue();
        assertThat(matched.reason()).isEqualTo(EvaluationReason.SEGMENT_MATCH);
        assertThat(unmatched.enabled()).isFalse();
    }

    @Test
    void listByEnvironmentReturnsOnlyThatEnvironmentsFlags() {
        environmentService.create("staging", "Staging", "alice");
        flagService.create("production", "flag-a", "A", "desc", FlagType.BOOLEAN, true, 0,
                List.of(), null, List.of(), List.of(), "alice");
        flagService.create("staging", "flag-b", "B", "desc", FlagType.BOOLEAN, true, 0,
                List.of(), null, List.of(), List.of(), "alice");

        assertThat(flagService.listByEnvironment("production")).extracting(Flag::key).containsExactly("flag-a");
    }
}
