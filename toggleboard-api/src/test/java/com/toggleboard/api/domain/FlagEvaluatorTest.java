package com.toggleboard.api.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

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
                true, 0, variants, "control", Instant.now(), Instant.now());

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
                true, 0, variants, "control", Instant.now(), Instant.now());

        EvaluationResult first = evaluator.evaluate(flag, "user-77");
        EvaluationResult second = evaluator.evaluate(flag, "user-77");

        assertThat(first.variantKey()).isEqualTo(second.variantKey());
    }

    private Flag booleanFlag(boolean enabled, int rolloutPercentage) {
        return new Flag("id", "feature", "production", "Feature", "desc", FlagType.BOOLEAN,
                enabled, rolloutPercentage, List.of(), null, Instant.now(), Instant.now());
    }
}
