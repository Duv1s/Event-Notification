package com.cobre.eventnotifications.infrastructure.web;

/** A request could not be parsed or violated an input constraint. Mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
