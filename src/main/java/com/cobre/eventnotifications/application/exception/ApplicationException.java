package com.cobre.eventnotifications.application.exception;

/** Base type for application use-case failures. */
public abstract class ApplicationException extends RuntimeException {

    protected ApplicationException(String message) {
        super(message);
    }
}
