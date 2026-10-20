package com.toggleboard.api.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TargetingRuleTest {

    @Test
    void equalsMatchesOnlyTheExactValue() {
        TargetingRule rule = new TargetingRule("plan", TargetingOperator.EQUALS, List.of("enterprise"), null);

        assertThat(rule.matches(Map.of("plan", "enterprise"))).isTrue();
        assertThat(rule.matches(Map.of("plan", "free"))).isFalse();
    }

    @Test
    void inMatchesAnyOfTheListedValues() {
        TargetingRule rule = new TargetingRule("country", TargetingOperator.IN, List.of("US", "CA", "GB"), null);

        assertThat(rule.matches(Map.of("country", "CA"))).isTrue();
        assertThat(rule.matches(Map.of("country", "FR"))).isFalse();
    }

    @Test
    void containsMatchesASubstringOfTheAttribute() {
        TargetingRule rule = new TargetingRule("email", TargetingOperator.CONTAINS, List.of("@acme.com"), null);

        assertThat(rule.matches(Map.of("email", "alice@acme.com"))).isTrue();
        assertThat(rule.matches(Map.of("email", "alice@example.com"))).isFalse();
    }

    @Test
    void missingAttributeNeverMatches() {
        TargetingRule rule = new TargetingRule("plan", TargetingOperator.EQUALS, List.of("enterprise"), null);

        assertThat(rule.matches(Map.of())).isFalse();
    }
}
