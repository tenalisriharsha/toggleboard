package com.toggleboard.api.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlagEvaluatorTest {

    private FlagEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new FlagEvaluator(new StableHashRolloutEngine());
    }

    @Test
    void killSwitchForcesDisabledRegardlessOfRollout() {
        Flag flag = booleanFlag(false, 100);
        EvaluationResult result = evaluator.evaluate(flag, "any-user");

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.KILL_SWITCH);
    }

    @Test
    void booleanFlagAtZeroPercentResolvesToFalseForEveryone() {
        Flag flag = booleanFlag(true, 0);
        for (int i = 0; i < 100; i++) {
            EvaluationResult result = evaluator.evaluate(flag, "user-" + i);
            assertThat(result.enabled()).isFalse();
            assertThat(result.reason()).isEqualTo(EvaluationReason.DEFAULT);
        }
    }

    @Test
    void booleanFlagAtHundredPercentResolvesToTrueForEveryone() {
        Flag flag = booleanFlag(true, 100);
        for (int i = 0; i < 100; i++) {
            EvaluationResult result = evaluator.evaluate(flag, "user-" + i);
            assertThat(result.enabled()).isTrue();
            assertThat(result.reason()).isEqualTo(EvaluationReason.ROLLOUT);
        }
    }

    @Test
    void multivariateFlagResolvesToOneOfItsVariants() {
        List<Variant> variants = List.of(
                new Variant("control", "off", 50),
                new Variant("treatment", "on", 50)
        );
        Flag flag = new Flag("id", "experiment", "production", "Experiment", "desc", FlagType.MULTIVARIATE,
                true, 0, variants, "control", List.of(), List.of(), Instant.now(), Instant.now());

        EvaluationResult result = evaluator.evaluate(flag, "user-1");

        assertThat(result.enabled()).isTrue();
        assertThat(result.variantKey()).isIn("control", "treatment");
        assertThat(result.reason()).isEqualTo(EvaluationReason.ROLLOUT);
    }

    @Test
    void multivariateFlagIsDeterministicForTheSameSubject() {
        List<Variant> variants = List.of(
                new Variant("control", "off", 30),
                new Variant("treatment", "on", 70)
        );
        Flag flag = new Flag("id", "experiment", "production", "Experiment", "desc", FlagType.MULTIVARIATE,
                true, 0, variants, "control", List.of(), List.of(), Instant.now(), Instant.now());

        EvaluationResult first = evaluator.evaluate(flag, "user-77");
        EvaluationResult second = evaluator.evaluate(flag, "user-77");

        assertThat(first.variantKey()).isEqualTo(second.variantKey());
    }

    @Test
    void directTargetingRuleForcesABooleanFlagOnAheadOfRollout() {
        TargetingRule rule = new TargetingRule("country", TargetingOperator.IN, List.of("US", "CA"), null);
        Flag flag = new Flag("id", "feature", "production", "Feature", "desc", FlagType.BOOLEAN,
                true, 0, List.of(), null, List.of(rule), List.of(), Instant.now(), Instant.now());

        EvaluationResult matched = evaluator.evaluate(flag, "user-1", Map.of("country", "US"), List.of());
        EvaluationResult unmatched = evaluator.evaluate(flag, "user-1", Map.of("country", "FR"), List.of());

        assertThat(matched.enabled()).isTrue();
        assertThat(matched.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
        assertThat(unmatched.enabled()).isFalse();
        assertThat(unmatched.reason()).isEqualTo(EvaluationReason.DEFAULT);
    }

    @Test
    void targetingRuleOverridesTheVariantOnAMultivariateFlag() {
        List<Variant> variants = List.of(new Variant("control", "off", 50), new Variant("treatment", "on", 50));
        TargetingRule rule = new TargetingRule("plan", TargetingOperator.EQUALS, List.of("enterprise"), "treatment");
        Flag flag = new Flag("id", "experiment", "production", "Experiment", "desc", FlagType.MULTIVARIATE,
                true, 0, variants, "control", List.of(rule), List.of(), Instant.now(), Instant.now());

        EvaluationResult result = evaluator.evaluate(flag, "user-1", Map.of("plan", "enterprise"), List.of());

        assertThat(result.variantKey()).isEqualTo("treatment");
        assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
    }

    @Test
    void matchingSegmentForcesABooleanFlagOn() {
        TargetingRule segmentRule = new TargetingRule("plan", TargetingOperator.EQUALS, List.of("enterprise"), null);
        Segment segment = new Segment("seg-1", "production", "enterprise-users", "Enterprise Users", "desc",
                List.of(segmentRule), Instant.now(), Instant.now());
        Flag flag = new Flag("id", "feature", "production", "Feature", "desc", FlagType.BOOLEAN,
                true, 0, List.of(), null, List.of(), List.of("enterprise-users"), Instant.now(), Instant.now());

        EvaluationResult matched = evaluator.evaluate(flag, "user-1", Map.of("plan", "enterprise"), List.of(segment));
        EvaluationResult unmatched = evaluator.evaluate(flag, "user-1", Map.of("plan", "free"), List.of(segment));

        assertThat(matched.enabled()).isTrue();
        assertThat(matched.reason()).isEqualTo(EvaluationReason.SEGMENT_MATCH);
        assertThat(unmatched.enabled()).isFalse();
    }

    @Test
    void segmentRequiresAllOfItsRulesToMatch() {
        Segment segment = new Segment("seg-1", "production", "us-enterprise", "US Enterprise", "desc",
                List.of(
                        new TargetingRule("plan", TargetingOperator.EQUALS, List.of("enterprise"), null),
                        new TargetingRule("country", TargetingOperator.EQUALS, List.of("US"), null)),
                Instant.now(), Instant.now());

        assertThat(segment.matches(Map.of("plan", "enterprise", "country", "US"))).isTrue();
        assertThat(segment.matches(Map.of("plan", "enterprise", "country", "FR"))).isFalse();
        assertThat(segment.matches(Map.of("plan", "enterprise"))).isFalse();
    }

    @Test
    void killSwitchStillWinsOverAMatchingTargetingRule() {
        TargetingRule rule = new TargetingRule("country", TargetingOperator.EQUALS, List.of("US"), null);
        Flag flag = new Flag("id", "feature", "production", "Feature", "desc", FlagType.BOOLEAN,
                false, 100, List.of(), null, List.of(rule), List.of(), Instant.now(), Instant.now());

        EvaluationResult result = evaluator.evaluate(flag, "user-1", Map.of("country", "US"), List.of());

        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.KILL_SWITCH);
    }

    private Flag booleanFlag(boolean enabled, int rolloutPercentage) {
        return new Flag("id", "feature", "production", "Feature", "desc", FlagType.BOOLEAN,
                enabled, rolloutPercentage, List.of(), null, List.of(), List.of(), Instant.now(), Instant.now());
    }
}
