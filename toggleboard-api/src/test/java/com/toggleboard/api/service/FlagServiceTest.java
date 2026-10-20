package com.toggleboard.api.service;

import com.toggleboard.api.domain.EvaluationResult;
import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.domain.FlagEvaluator;
import com.toggleboard.api.domain.FlagType;
import com.toggleboard.api.domain.StableHashRolloutEngine;
import com.toggleboard.api.domain.Variant;
import com.toggleboard.api.exception.ConflictException;
import com.toggleboard.api.exception.InvalidFlagException;
import com.toggleboard.api.exception.NotFoundException;
import com.toggleboard.api.repository.memory.InMemoryEnvironmentRepository;
import com.toggleboard.api.repository.memory.InMemoryFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagServiceTest {

    private FlagService flagService;
    private EnvironmentService environmentService;

    @BeforeEach
    void setUp() {
        environmentService = new EnvironmentService(new InMemoryEnvironmentRepository());
        environmentService.create("production", "Production");
        flagService = new FlagService(new InMemoryFlagRepository(), environmentService,
                new FlagEvaluator(new StableHashRolloutEngine()));
    }

    @Test
    void createsABooleanFlag() {
        Flag flag = flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN,
                true, 25, List.of(), null);

        assertThat(flag.id()).isNotBlank();
        assertThat(flag.key()).isEqualTo("new-nav");
        assertThat(flag.rolloutPercentage()).isEqualTo(25);
    }

    @Test
    void rejectsCreationInUnknownEnvironment() {
        assertThatThrownBy(() -> flagService.create("unknown", "flag", "Flag", "desc", FlagType.BOOLEAN,
                true, 0, List.of(), null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsDuplicateFlagKeyWithinSameEnvironment() {
        flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN, true, 0, List.of(), null);

        assertThatThrownBy(() -> flagService.create("production", "new-nav", "Again", "desc", FlagType.BOOLEAN,
                true, 0, List.of(), null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsRolloutPercentageOutOfRange() {
        assertThatThrownBy(() -> flagService.create("production", "flag", "Flag", "desc", FlagType.BOOLEAN,
                true, 150, List.of(), null))
                .isInstanceOf(InvalidFlagException.class);
    }

    @Test
    void rejectsMultivariateFlagWhoseWeightsDoNotSumToHundred() {
        List<Variant> variants = List.of(new Variant("a", "A", 40), new Variant("b", "B", 40));

        assertThatThrownBy(() -> flagService.create("production", "experiment", "Experiment", "desc",
                FlagType.MULTIVARIATE, true, 0, variants, "a"))
                .isInstanceOf(InvalidFlagException.class)
                .hasMessageContaining("sum to 100");
    }

    @Test
    void rejectsMultivariateFlagWithUnknownDefaultVariant() {
        List<Variant> variants = List.of(new Variant("a", "A", 50), new Variant("b", "B", 50));

        assertThatThrownBy(() -> flagService.create("production", "experiment", "Experiment", "desc",
                FlagType.MULTIVARIATE, true, 0, variants, "c"))
                .isInstanceOf(InvalidFlagException.class);
    }

    @Test
    void updateChangesMutableFieldsButKeepsIdentityAndType() {
        Flag created = flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN,
                true, 25, List.of(), null);

        Flag updated = flagService.update("production", "new-nav", "New Nav v2", "updated desc", false, 75,
                List.of(), null);

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.name()).isEqualTo("New Nav v2");
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.rolloutPercentage()).isEqualTo(75);
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
    }

    @Test
    void deleteRemovesTheFlag() {
        flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN, true, 0, List.of(), null);

        flagService.delete("production", "new-nav");

        assertThatThrownBy(() -> flagService.getByKey("production", "new-nav")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void evaluateDelegatesToFlagEvaluator() {
        flagService.create("production", "new-nav", "New Nav", "desc", FlagType.BOOLEAN, true, 100, List.of(), null);

        EvaluationResult result = flagService.evaluate("production", "new-nav", "user-1");

        assertThat(result.enabled()).isTrue();
    }

    @Test
    void listByEnvironmentReturnsOnlyThatEnvironmentsFlags() {
        environmentService.create("staging", "Staging");
        flagService.create("production", "flag-a", "A", "desc", FlagType.BOOLEAN, true, 0, List.of(), null);
        flagService.create("staging", "flag-b", "B", "desc", FlagType.BOOLEAN, true, 0, List.of(), null);

        assertThat(flagService.listByEnvironment("production")).extracting(Flag::key).containsExactly("flag-a");
    }
}
