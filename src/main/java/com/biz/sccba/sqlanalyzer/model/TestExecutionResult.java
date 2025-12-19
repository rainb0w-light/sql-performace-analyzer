package com.biz.sccba.sqlanalyzer.model;

import lombok.Data;
import java.util.List;

/**
 * SQL执行结果
 */
@Data
public class TestExecutionResult {
    /**
     * 步骤ID
     */
    private String stepId;

    /**
     * 线程ID
     */
    private String threadId;

    /**
     * SQL语句（单个SQL，向后兼容）
     */
    private String sql;

    /**
     * SQL执行详情列表（支持一个step执行多个SQL）
     */
    private List<SqlExecutionDetail> sqlDetails;

    /**
     * 执行开始时间戳（纳秒）
     */
    private Long startTimeNanos;

    /**
     * 执行结束时间戳（纳秒）
     */
    private Long endTimeNanos;

    /**
     * 执行耗时（毫秒）
     */
    private Long durationMillis;

    /**
     * 执行状态：SUCCESS, FAILED
     */
    private ExecutionStatus status;

    /**
     * 异常信息（如果执行失败）
     */
    private ExceptionInfo exception;

    /**
     * SQL执行详情
     */
    @Data
    public static class SqlExecutionDetail {
        /**
         * SQL语句
         */
        private String sql;

        /**
         * 执行开始时间戳（纳秒）
         */
        private Long startTimeNanos;

        /**
         * 执行结束时间戳（纳秒）
         */
        private Long endTimeNanos;

        /**
         * 执行耗时（毫秒）
         */
        private Long durationMillis;

        /**
         * 执行状态：SUCCESS, FAILED
         */
        private ExecutionStatus status;

        /**
         * 异常信息（如果执行失败）
         */
        private ExceptionInfo exception;
    }

    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        SUCCESS,
        FAILED
    }

    /**
     * 异常信息
     */
    @Data
    public static class ExceptionInfo {
        /**
         * 异常类型
         */
        private String exceptionType;

        /**
         * 异常消息
         */
        private String message;

        /**
         * SQL错误码（如果有）
         */
        private Integer sqlErrorCode;

        /**
         * SQL状态（如果有）
         */
        private String sqlState;

        /**
         * 堆栈跟踪（可选，用于详细调试）
         */
        private String stackTrace;
    }
}

