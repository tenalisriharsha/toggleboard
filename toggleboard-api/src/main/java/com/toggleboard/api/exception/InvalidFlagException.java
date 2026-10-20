package com.toggleboard.api.exception;

public class InvalidFlagException extends RuntimeException {
    public InvalidFlagException(String message) {
        super(message);
    }
}
