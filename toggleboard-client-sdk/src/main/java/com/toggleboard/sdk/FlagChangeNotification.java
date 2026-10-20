package com.toggleboard.sdk;

import java.time.Instant;

/**
 * Delivered to listeners registered via {@link ToggleboardClient#onFlagChange}
 * whenever the server pushes a flag-change event over SSE. {@code action} is
 * one of {@code "CREATED"}, {@code "UPDATED"} or {@code "DELETED"}, mirroring
 * the server's {@code FlagChangeAction}.
 */
public record FlagChangeNotification(
        String environmentKey,
        String flagKey,
        String action,
        Instant timestamp
) {
}
