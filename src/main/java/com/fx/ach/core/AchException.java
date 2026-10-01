package com.fx.ach.core;

/**
 * An ACH problem with a message fit to show a person.
 */
public class AchException extends RuntimeException {

    public AchException(String message) {
        super(message);
    }

    public AchException(String message, Throwable cause) {
        super(message, cause);
    }
}
