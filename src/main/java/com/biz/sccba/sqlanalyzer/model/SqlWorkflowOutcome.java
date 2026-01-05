package com.biz.sccba.sqlanalyzer.model;

/**
 * 描述单条 SQL 工作流执行的结果。
 */
public class SqlWorkflowOutcome {

    public enum Status {
        SUCCESS,
        FAILED
    }

    private String mapperId;
    private String sql;
    private Status status;
    private AgentErrorCode errorCode;
    private String errorMessage;

    public String getMapperId() {
        return mapperId;
    }

    public void setMapperId(String mapperId) {
        this.mapperId = mapperId;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public AgentErrorCode getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(AgentErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

