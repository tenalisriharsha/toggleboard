package com.toggleboard.api.domain;

/**
 * How a {@link TargetingRule} compares a subject attribute against its
 * configured values.
 */
public enum TargetingOperator {
    /** The attribute must equal the rule's single value. */
    EQUALS,
    /** The attribute must equal one of the rule's values. */
    IN,
    /** The attribute must contain one of the rule's values as a substring. */
    CONTAINS
}
