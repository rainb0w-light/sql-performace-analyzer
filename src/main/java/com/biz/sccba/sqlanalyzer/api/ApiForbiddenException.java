package com.biz.sccba.sqlanalyzer.api;

public class ApiForbiddenException extends RuntimeException {
    public ApiForbiddenException(String message) {
        super(message);
    }
}
