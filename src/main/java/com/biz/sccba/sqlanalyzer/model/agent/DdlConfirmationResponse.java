package com.biz.sccba.sqlanalyzer.model.agent;

/**
 * DDL 确认响应
 */
public class DdlConfirmationResponse {
    /**
     * 是否确认
     */
    private boolean confirmed;

    /**
     * 用户反馈
     */
    private String userFeedback;

    /**
     * 执行结果
     */
    private String executionResult;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;

    /**
     * 评论/原因
     */
    private String comment;

    // Default constructor
    public DdlConfirmationResponse() {}

    // Constructor for confirmation
    public DdlConfirmationResponse(boolean confirmed, String executionResult, String errorMessage) {
        this.confirmed = confirmed;
        this.executionResult = executionResult;
        this.errorMessage = errorMessage;
    }

    // Getters and Setters
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public String getUserFeedback() { return userFeedback; }
    public void setUserFeedback(String userFeedback) { this.userFeedback = userFeedback; }

    public String getExecutionResult() { return executionResult; }
    public void setExecutionResult(String executionResult) { this.executionResult = executionResult; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    /**
     * 创建确认响应
     */
    public static DdlConfirmationResponse confirmed(String executionResult) {
        DdlConfirmationResponse response = new DdlConfirmationResponse();
        response.setConfirmed(true);
        response.setExecutionResult(executionResult);
        return response;
    }

    /**
     * 创建拒绝响应
     */
    public static DdlConfirmationResponse rejected(String comment) {
        DdlConfirmationResponse response = new DdlConfirmationResponse();
        response.setConfirmed(false);
        response.setComment(comment);
        return response;
    }
}
