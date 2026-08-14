package com.bookillustrator.domain.exception;

/** Base type for business-rule violations. Caught centrally by GlobalExceptionHandler. */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
