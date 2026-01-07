package com.biz.sccba.sqlanalyzer.error;

/**
 * Agent 层统一错误码。
 */
public enum AgentErrorCode {
    DATA_FILL_FAILED,
    EXECUTION_PLAN_FAILED,
    PLAN_ANALYSIS_FAILED,
    REPORT_GENERATION_FAILED,
    JSON_PARSE_FAILED,
    TEMPLATE_RENDER_FAILED,
    LLM_CALL_FAILED,
    LLM_TIMEOUT,
    VALIDATION_FAILED,
    UNKNOWN
}

