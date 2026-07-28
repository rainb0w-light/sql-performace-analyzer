package com.biz.sccba.sqlanalyzer.controller;

import jakarta.servlet.http.HttpServletRequest;
import com.biz.sccba.sqlanalyzer.api.ApiAuthenticationException;
import com.biz.sccba.sqlanalyzer.api.ApiForbiddenException;
import com.biz.sccba.sqlanalyzer.api.IdempotencyConflictException;
import com.biz.sccba.sqlanalyzer.api.ResourceNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified error mapping to RFC 9457 Problem Details (docs/contracts/rest-api.md §3).
 * Every problem carries type/title/status/detail/code/requestId/retryable.
 */
@RestControllerAdvice(basePackages = "com.biz.sccba.sqlanalyzer.controller")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ApiErrorHandler {

    private static final String PROBLEM_BASE = "https://sql-analyzer.local/problems/";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @ExceptionHandler(ApiAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> unauthorized(ApiAuthenticationException exception,
                                                            HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "UNAUTHORIZED",
                exception.getMessage() == null ? "认证失败" : exception.getMessage(), false, request);
    }

    @ExceptionHandler(ApiForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(ApiForbiddenException exception,
                                                         HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "FORBIDDEN",
                exception.getMessage() == null ? "无权执行该操作" : exception.getMessage(), false, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(ResourceNotFoundException exception,
                                                        HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "NOT_FOUND",
                exception.getMessage() == null ? "资源不存在" : exception.getMessage(), false, request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> idempotencyConflict(
            IdempotencyConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "idempotency-conflict", "IDEMPOTENCY_CONFLICT",
                exception.getMessage(), false, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception,
                                                          HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "INVALID_REQUEST",
                exception.getMessage() == null ? "请求无效" : exception.getMessage(), false, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> conflict(IllegalStateException exception,
                                                        HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "conflict", "CONFLICT",
                exception.getMessage() == null ? "当前状态不允许该操作" : exception.getMessage(), true, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception,
                                                          HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = new ArrayList<>();
        exception.getBindingResult().getFieldErrors().forEach(fe -> {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("field", fe.getField());
            entry.put("message", fe.getDefaultMessage() == null ? "参数无效" : fe.getDefaultMessage());
            fieldErrors.add(entry);
        });
        ResponseEntity<Map<String, Object>> response =
                problem(HttpStatus.BAD_REQUEST, "validation-failed", "VALIDATION_FAILED",
                        "请求参数无效", false, request);
        Map<String, Object> body = new LinkedHashMap<>(response.getBody());
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(REQUEST_ID_HEADER, (String) body.get("requestId"))
                .body(body);
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String typeSlug, String code,
                                                        String detail, boolean retryable,
                                                        HttpServletRequest request) {
        String requestId = request == null ? null : request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = "req_" + UUID.randomUUID();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", PROBLEM_BASE + typeSlug);
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("code", code);
        body.put("requestId", requestId);
        body.put("retryable", retryable);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(REQUEST_ID_HEADER, requestId)
                .body(body);
    }
}
