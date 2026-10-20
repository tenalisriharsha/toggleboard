package com.toggleboard.api.domain;

/**
 * Resolves a {@link Flag} to a concrete value for a given subject (typically
 * a user ID). Kill switch takes precedence over rollout; targeting rules and
 * segments are evaluated here in a later phase.
 */
public class FlagEvaluator {

    private final StableHashRolloutEngine rolloutEngine;

    public FlagEvaluator(StableHashRolloutEngine rolloutEngine) {
        this.rolloutEngine = rolloutEngine;
    }

    public EvaluationResult evaluate(Flag flag, String subjectKey) {
        if (!flag.enabled()) {
            return new EvaluationResult(flag.key(), false, flag.defaultVariantKey(), "false", EvaluationReason.KILL_SWITCH);
        }

        if (flag.type() == FlagType.BOOLEAN) {
            boolean inRollout = rolloutEngine.isInRollout(flag.key(), subjectKey, flag.rolloutPercentage());
            EvaluationReason reason = inRollout ? EvaluationReason.ROLLOUT : EvaluationReason.DEFAULT;
            return new EvaluationResult(flag.key(), inRollout, null, String.valueOf(inRollout), reason);
        }

        Variant variant = rolloutEngine.pickVariant(flag.key(), subjectKey, flag.variants());
        return new EvaluationResult(flag.key(), true, variant.key(), variant.value(), EvaluationReason.ROLLOUT);
    }
}
