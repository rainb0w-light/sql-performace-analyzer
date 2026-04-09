package com.biz.sccba.sqlanalyzer.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Tool 执行结果
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 结果数据
     */
    private Object data;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行时间 (毫秒)
     */
    private long durationMs;

    /**
     * 执行时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;

    /**
     * 创建成功结果
     */
    public static ToolResult success(Object data) {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setData(data);
        result.setTimestamp(LocalDateTime.now());
        return result;
    }

    /**
     * 创建成功结果 (带执行时间)
     */
    public static ToolResult success(Object data, long durationMs) {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setData(data);
        result.setDurationMs(durationMs);
        result.setTimestamp(LocalDateTime.now());
        return result;
    }

    /**
     * 创建失败结果
     */
    public static ToolResult failure(String errorMessage) {
        ToolResult result = new ToolResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        result.setTimestamp(LocalDateTime.now());
        return result;
    }

    /**
     * 创建失败结果 (带异常)
     */
    public static ToolResult failure(String errorMessage, Throwable cause) {
        ToolResult result = new ToolResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage + ": " + cause.getMessage());
        result.setTimestamp(LocalDateTime.now());
        return result;
    }
}