package com.example.store.exception;

/** Thrown when a request is structurally valid but violates a business rule. Mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
