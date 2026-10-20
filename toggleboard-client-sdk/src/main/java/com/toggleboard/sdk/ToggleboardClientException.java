package com.toggleboard.sdk;

/**
 * Thrown by {@link ToggleboardClient#evaluate} when the evaluation request
 * fails and there is no previously cached result to fall back to.
 */
public class ToggleboardClientException extends RuntimeException {

    public ToggleboardClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
