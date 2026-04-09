package com.biz.sccba.sqlanalyzer.tui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * TUI 命令执行结果
 */
public class TuiCommandResult {

    private final boolean success;
    private final String message;
    private final String sessionId;
    private final Map<String, Object> data;

    public TuiCommandResult(boolean success, String message, String sessionId, Map<String, Object> data) {
        this.success = success;
        this.message = message != null ? message : "";
        this.sessionId = sessionId;
        this.data = data != null ? data : new HashMap<>();
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public static TuiCommandResult success(String message) {
        return new TuiCommandResult(true, message, null, null);
    }

    public static TuiCommandResult success(String message, String sessionId) {
        return new TuiCommandResult(true, message, sessionId, null);
    }

    public static TuiCommandResult error(String message) {
        return new TuiCommandResult(false, message, null, null);
    }

    public static TuiCommandResult error(String message, String sessionId) {
        return new TuiCommandResult(false, message, sessionId, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean success = true;
        private String message;
        private String sessionId;
        private Map<String, Object> data;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public TuiCommandResult build() {
            return new TuiCommandResult(success, message, sessionId, data);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TuiCommandResult that = (TuiCommandResult) o;
        return success == that.success &&
               Objects.equals(message, that.message) &&
               Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, message, sessionId);
    }

    @Override
    public String toString() {
        return "TuiCommandResult{" +
               "success=" + success +
               ", message='" + message + '\'' +
               ", sessionId='" + sessionId + '\'' +
               '}';
    }
}
