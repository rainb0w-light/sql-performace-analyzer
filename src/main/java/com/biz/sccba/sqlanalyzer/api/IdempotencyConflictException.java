package com.biz.sccba.sqlanalyzer.api;

/** Same tenant/key was already committed with a different request payload. */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
