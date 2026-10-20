package com.toggleboard.api.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a {@link Flag} to a concrete value for a given subject (typically
 * a user ID). Precedence: kill switch, then the flag's own targeting rules,
 * then its referenced segments, then the percentage rollout.
 */
public class FlagEvaluator {

    private final StableHashRolloutEngine rolloutEngine;

    public FlagEvaluator(StableHashRolloutEngine rolloutEngine) {
        this.rolloutEngine = rolloutEngine;
    }

    public EvaluationResult evaluate(Flag flag, String subjectKey) {
        return evaluate(flag, subjectKey, Map.of(), List.of());
    }

    public EvaluationResult evaluate(Flag flag, String subjectKey, Map<String, String> attributes, List<Segment> segments) {
        if (!flag.enabled()) {
            return new EvaluationResult(flag.key(), false, flag.defaultVariantKey(), "false", EvaluationReason.KILL_SWITCH);
        }

        Optional<TargetingRule> matchedRule = flag.targetingRules().stream()
                .filter(rule -> rule.matches(attributes))
                .findFirst();
        if (matchedRule.isPresent()) {
            return resolve(flag, subjectKey, EvaluationReason.TARGETING_MATCH, matchedRule.get().overrideVariantKey());
        }

        boolean segmentMatch = segments.stream().anyMatch(segment -> segment.matches(attributes));
        if (segmentMatch) {
            return resolve(flag, subjectKey, EvaluationReason.SEGMENT_MATCH, null);
        }

        return resolve(flag, subjectKey, null, null);
    }

    private EvaluationResult resolve(Flag flag, String subjectKey, EvaluationReason matchReason, String overrideVariantKey) {
        if (flag.type() == FlagType.BOOLEAN) {
            if (matchReason != null) {
                return new EvaluationResult(flag.key(), true, null, "true", matchReason);
            }
            boolean inRollout = rolloutEngine.isInRollout(flag.key(), subjectKey, flag.rolloutPercentage());
            EvaluationReason reason = inRollout ? EvaluationReason.ROLLOUT : EvaluationReason.DEFAULT;
            return new EvaluationResult(flag.key(), inRollout, null, String.valueOf(inRollout), reason);
        }

        EvaluationReason reason = matchReason != null ? matchReason : EvaluationReason.ROLLOUT;
        Variant variant = resolveVariant(flag, subjectKey, overrideVariantKey);
        return new EvaluationResult(flag.key(), true, variant.key(), variant.value(), reason);
    }

    private Variant resolveVariant(Flag flag, String subjectKey, String overrideVariantKey) {
        if (overrideVariantKey != null) {
            return flag.variants().stream()
                    .filter(v -> v.key().equals(overrideVariantKey))
                    .findFirst()
                    .orElseGet(() -> rolloutEngine.pickVariant(flag.key(), subjectKey, flag.variants()));
        }
        return rolloutEngine.pickVariant(flag.key(), subjectKey, flag.variants());
    }
}
