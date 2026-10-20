package com.toggleboard.sdk;

/**
 * The result of evaluating one flag for one subject, as returned by the
 * toggleboard evaluation endpoint. Field names match the server's JSON
 * response exactly so Jackson can deserialize straight into this record.
 */
public record EvaluationResult(
        String flagKey,
        boolean enabled,
        String variantKey,
        String value,
        String reason
) {
}
