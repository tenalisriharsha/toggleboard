package com.toggleboard.api.domain;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Deterministically buckets a (flag, subject) pair into [0, 100) so that
 * rollout decisions are stable across evaluations: the same subject always
 * lands in the same bucket for a given flag, and changing a flag's rollout
 * percentage only ever moves subjects into or out of the rollout, never
 * reshuffles who is already in it.
 *
 * <p>Uses 64-bit FNV-1a, which has no external dependency and good avalanche
 * behavior for short ASCII keys like these.
 */
public class StableHashRolloutEngine {

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    public int bucketOf(String flagKey, String subjectKey) {
        long hash = fnv1a64(flagKey + ":" + subjectKey);
        return (int) Math.floorMod(hash, 100L);
    }

    public boolean isInRollout(String flagKey, String subjectKey, int rolloutPercentage) {
        if (rolloutPercentage <= 0) {
            return false;
        }
        if (rolloutPercentage >= 100) {
            return true;
        }
        return bucketOf(flagKey, subjectKey) < rolloutPercentage;
    }

    /**
     * Picks a variant whose cumulative weight range contains the subject's
     * bucket. Variant weights are expected to sum to 100; if they sum to
     * less, the remaining buckets fall through to the last variant.
     */
    public Variant pickVariant(String flagKey, String subjectKey, List<Variant> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("Cannot pick a variant from an empty list");
        }
        int bucket = bucketOf(flagKey, subjectKey);
        int cumulative = 0;
        for (Variant variant : variants) {
            cumulative += variant.weight();
            if (bucket < cumulative) {
                return variant;
            }
        }
        return variants.get(variants.size() - 1);
    }

    private long fnv1a64(String input) {
        long hash = FNV_OFFSET_BASIS;
        for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
