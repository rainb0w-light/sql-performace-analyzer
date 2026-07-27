package com.biz.sccba.sqlanalyzer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 contract: unified Problem Details (RFC 9457) error shape
 * (docs/contracts/rest-api.md §3): type/title/status/detail/code/requestId/retryable.
 */
class ApiErrorHandlerTest {

    private final ApiErrorHandler handler = new ApiErrorHandler();

    @Test
    void illegalArgumentBecomes400ProblemDetails() {
        ResponseEntity<Map<String, Object>> response =
                handler.badRequest(new IllegalArgumentException("会话不存在"), null);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("status"));
        assertEquals("INVALID_REQUEST", body.get("code"));
        assertEquals("会话不存在", body.get("detail"));
        assertEquals(false, body.get("retryable"));
        assertNotNull(body.get("requestId"));
        assertNotNull(body.get("type"));
    }

    @Test
    void illegalStateBecomes409RetryableProblemDetails() {
        ResponseEntity<Map<String, Object>> response =
                handler.conflict(new IllegalStateException("当前客户端同时运行的 Agent Session 已达到上限：10"), null);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(409, body.get("status"));
        assertEquals("CONFLICT", body.get("code"));
        assertEquals(true, body.get("retryable"), "capacity/queue conflicts are retryable");
    }

    @Test
    void requestIdHeaderIsEchoed() {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req_contract_123");
        ResponseEntity<Map<String, Object>> response =
                handler.badRequest(new IllegalArgumentException("x"), request);
        assertEquals("req_contract_123", response.getBody().get("requestId"));
        assertEquals("req_contract_123", response.getHeaders().getFirst("X-Request-Id"));
    }

    @Test
    void missingRequestIdIsGenerated() {
        ResponseEntity<Map<String, Object>> response =
                handler.badRequest(new IllegalArgumentException("x"), null);
        String requestId = (String) response.getBody().get("requestId");
        assertFalse(requestId.isBlank());
        assertTrue(requestId.startsWith("req_"));
    }
}
