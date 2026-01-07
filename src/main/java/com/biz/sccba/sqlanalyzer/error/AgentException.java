package com.biz.sccba.sqlanalyzer.error;

/**
 * Agent 层统一异常，携带错误码与上下文。
 */
public class AgentException extends Exception {

    private final AgentErrorCode errorCode;

    public AgentException(AgentErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AgentException(AgentErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public AgentErrorCode getErrorCode() {
        return errorCode;
    }
}

