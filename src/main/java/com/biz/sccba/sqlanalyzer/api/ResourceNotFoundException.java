package com.biz.sccba.sqlanalyzer.api;

/** Tenant-scoped resource absence; foreign ownership is deliberately indistinguishable from missing. */
public final class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
