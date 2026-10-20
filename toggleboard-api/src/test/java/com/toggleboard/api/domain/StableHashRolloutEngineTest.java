package com.toggleboard.api.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StableHashRolloutEngineTest {

    private final StableHashRolloutEngine engine = new StableHashRolloutEngine();

    @Test
    void sameFlagAndSubjectAlwaysBucketsTheSame() {
        int first = engine.bucketOf("checkout-redesign", "user-42");
        int second = engine.bucketOf("checkout-redesign", "user-42");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void bucketIsWithinZeroToNinetyNine() {
        for (int i = 0; i < 5000; i++) {
            int bucket = engine.bucketOf("flag", "subject-" + i);
            assertThat(bucket).isBetween(0, 99);
        }
    }

    @Test
    void differentFlagsCanBucketTheSameSubjectDifferently() {
        boolean sawDifference = false;
        for (int i = 0; i < 200; i++) {
            String subject = "subject-" + i;
            if (engine.bucketOf("flag-a", subject) != engine.bucketOf("flag-b", subject)) {
                sawDifference = true;
                break;
            }
        }
        assertThat(sawDifference).isTrue();
    }

    @Test
    void zeroPercentRolloutExcludesEveryone() {
        for (int i = 0; i < 500; i++) {
            assertThat(engine.isInRollout("flag", "subject-" + i, 0)).isFalse();
        }
    }

    @Test
    void hundredPercentRolloutIncludesEveryone() {
        for (int i = 0; i < 500; i++) {
            assertThat(engine.isInRollout("flag", "subject-" + i, 100)).isTrue();
        }
    }

    @Test
    void rolloutPercentageApproximatesTargetAcrossManySubjects() {
        int sampleSize = 20_000;
        int target = 30;
        long inRollout = 0;
        for (int i = 0; i < sampleSize; i++) {
            if (engine.isInRollout("pricing-v2", "user-" + i, target)) {
                inRollout++;
            }
        }
        double observedPercentage = (inRollout * 100.0) / sampleSize;
        assertThat(observedPercentage).isCloseTo(target, org.assertj.core.data.Offset.offset(2.0));
    }

    @Test
    void increasingRolloutNeverRemovesAnExistingSubject() {
        for (int i = 0; i < 1000; i++) {
            String subject = "user-" + i;
            if (engine.isInRollout("flag", subject, 25)) {
                assertThat(engine.isInRollout("flag", subject, 50)).isTrue();
            }
        }
    }

    @Test
    void pickVariantRespectsWeightedBoundaries() {
        List<Variant> variants = List.of(
                new Variant("control", "off", 50),
                new Variant("treatment", "on", 50)
        );
        for (int i = 0; i < 500; i++) {
            String subject = "user-" + i;
            int bucket = engine.bucketOf("experiment", subject);
            Variant picked = engine.pickVariant("experiment", subject, variants);
            if (bucket < 50) {
                assertThat(picked.key()).isEqualTo("control");
            } else {
                assertThat(picked.key()).isEqualTo("treatment");
            }
        }
    }

    @Test
    void pickVariantDistributionApproximatesWeights() {
        List<Variant> variants = List.of(
                new Variant("a", "A", 10),
                new Variant("b", "B", 30),
                new Variant("c", "C", 60)
        );
        int sampleSize = 20_000;
        int countA = 0, countB = 0, countC = 0;
        for (int i = 0; i < sampleSize; i++) {
            Variant picked = engine.pickVariant("flag", "user-" + i, variants);
            switch (picked.key()) {
                case "a" -> countA++;
                case "b" -> countB++;
                case "c" -> countC++;
                default -> throw new AssertionError("Unexpected variant " + picked.key());
            }
        }
        assertThat((countA * 100.0) / sampleSize).isCloseTo(10, org.assertj.core.data.Offset.offset(2.0));
        assertThat((countB * 100.0) / sampleSize).isCloseTo(30, org.assertj.core.data.Offset.offset(2.0));
        assertThat((countC * 100.0) / sampleSize).isCloseTo(60, org.assertj.core.data.Offset.offset(2.0));
    }

    @Test
    void pickVariantRejectsEmptyList() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> engine.pickVariant("flag", "subject", List.of()));
    }
}
