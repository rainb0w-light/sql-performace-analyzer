package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.api.ApiAuthenticationException;
import com.biz.sccba.sqlanalyzer.api.IdempotencyConflictException;
import com.biz.sccba.sqlanalyzer.controller.AnalysisRunController;
import com.biz.sccba.sqlanalyzer.controller.ArtifactController;
import com.biz.sccba.sqlanalyzer.controller.MapperStatementController;
import com.biz.sccba.sqlanalyzer.controller.ReportController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Plugin-controller Problem Details without changing the shared global error handler. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        ArtifactController.class, MapperStatementController.class,
        AnalysisRunController.class, ReportController.class
})
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class PluginApiErrorAdvice {

    @ExceptionHandler(ApiAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> unauthorized(
            ApiAuthenticationException error, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", error.getMessage(),
                false, request, null);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> idempotency(
            IdempotencyConflictException error, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", error.getMessage(),
                false, request, null);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> unsupported(
            UnsupportedOperationException error, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED", error.getMessage(),
                false, request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalid(
            IllegalArgumentException error, HttpServletRequest request) {
        boolean unsupported = error.getMessage() != null
                && error.getMessage().startsWith("UNSUPPORTED:");
        return problem(unsupported ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_REQUEST,
                unsupported ? "UNSUPPORTED" : "VALIDATION_FAILED", error.getMessage(),
                false, request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> state(
            IllegalStateException error, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "RUN_STATE_CONFLICT", error.getMessage(),
                false, request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> fields(
            MethodArgumentNotValidException error, HttpServletRequest request) {
        var errors = error.getBindingResult().getFieldErrors().stream()
                .map(field -> Map.of("field", field.getField(), "nodeId", "",
                        "code", "INVALID", "message",
                        field.getDefaultMessage() == null ? "参数无效" : field.getDefaultMessage()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "请求参数无效", false, request, errors);
    }

    private ResponseEntity<Map<String, Object>> problem(
            HttpStatus status, String code, String detail, boolean retryable,
            HttpServletRequest request, Object errors) {
        String requestId = request == null ? null : request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = "req_" + UUID.randomUUID();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://sql-analyzer.local/problems/"
                + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail == null ? status.getReasonPhrase() : detail);
        body.put("code", code);
        body.put("requestId", requestId);
        body.put("retryable", retryable);
        if (errors != null) body.put("errors", errors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("X-Request-Id", requestId).body(body);
    }
}
