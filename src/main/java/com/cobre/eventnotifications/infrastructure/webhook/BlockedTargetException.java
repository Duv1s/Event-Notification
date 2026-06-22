package com.cobre.eventnotifications.infrastructure.webhook;

/** Raised by {@link SsrfGuard} when a webhook target is not allowed (scheme, port or resolved IP). */
public class BlockedTargetException extends RuntimeException {

    public BlockedTargetException(String message) {
        super(message);
    }
}
