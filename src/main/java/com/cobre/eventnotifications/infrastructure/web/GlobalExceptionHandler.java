package com.cobre.eventnotifications.infrastructure.web;

import com.cobre.eventnotifications.application.exception.NotificationNotFoundException;
import com.cobre.eventnotifications.application.exception.ReplayNotAllowedException;
import com.cobre.eventnotifications.application.exception.SubscriptionNotEligibleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Central error handling. Renders RFC 7807 {@link ProblemDetail} responses with an app-specific code. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ProblemDetail handleNotFound(NotificationNotFoundException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND, "Notification not found", ex.getMessage(), "NOTIFICATION_NOT_FOUND", request);
    }

    @ExceptionHandler(ReplayNotAllowedException.class)
    public ProblemDetail handleReplayNotAllowed(ReplayNotAllowedException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Replay not allowed", ex.getMessage(), "REPLAY_NOT_ALLOWED", request);
    }

    @ExceptionHandler(SubscriptionNotEligibleException.class)
    public ProblemDetail handleSubscriptionNotEligible(
            SubscriptionNotEligibleException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Subscription not eligible",
                ex.getMessage(),
                "SUBSCRIPTION_NOT_ELIGIBLE",
                request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), "INVALID_REQUEST", request);
    }

    /**
     * Catches domain value-object / query validation ({@link IllegalArgumentException}) that surfaces
     * while building domain types from untrusted input (e.g. occurred_from not before occurred_to).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), "INVALID_REQUEST", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request parameters are invalid",
                "VALIDATION_ERROR",
                request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request parameters are invalid",
                "VALIDATION_ERROR",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "Parameter '" + ex.getName() + "' has an invalid value",
                "INVALID_REQUEST",
                request);
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, String detail, String code, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.cobre.example/problems/"
                + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
