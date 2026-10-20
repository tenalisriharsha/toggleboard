package com.toggleboard.api.streaming;

import com.toggleboard.api.domain.Flag;

import java.time.Instant;

/**
 * Pushed over SSE to subscribers of an environment's stream whenever a flag
 * is created, updated or deleted. {@code flag} is the resulting snapshot for
 * {@code CREATED}/{@code UPDATED}, or the last known snapshot before removal
 * for {@code DELETED}.
 */
public record FlagChangeEvent(
        String environmentKey,
        String flagKey,
        FlagChangeAction action,
        Flag flag,
        Instant timestamp
) {
}
