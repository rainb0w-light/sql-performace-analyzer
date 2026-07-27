package com.biz.sccba.sqlanalyzer.api;

/** Authentication failure that must be rendered as RFC 9457 HTTP 401. */
public final class ApiAuthenticationException extends RuntimeException {
    public ApiAuthenticationException(String message) {
        super(message);
    }

    public ApiAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
