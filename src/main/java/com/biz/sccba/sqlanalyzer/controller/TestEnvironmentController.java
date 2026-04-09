package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.service.TestEnvironmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 测试环境管理控制器
 *
 * 提供测试环境管理的 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/environments")
@RequiredArgsConstructor
public class TestEnvironmentController {

    private final TestEnvironmentService environmentService;

    /**
     * 获取所有测试环境
     * GET /api/v2/environments
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllEnvironments() {
        return ResponseEntity.ok(environmentService.getDatasourceList());
    }

    /**
     * 获取指定测试环境详情
     * GET /api/v2/environments/{name}
     */
    @GetMapping("/{name}")
    public ResponseEntity<Map<String, Object>> getEnvironment(
            @PathVariable String name) {
        var env = environmentService.getEnvironment(name);
        if (env == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "name", env.name(),
            "description", env.description(),
            "jdbcUrl", env.jdbcUrl(),
            "username", env.username(),
            "status", env.status(),
            "metadata", env.metadata()
        ));
    }

    /**
     * 注册新的测试环境
     * POST /api/v2/environments
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> registerEnvironment(
            @RequestBody RegisterEnvironmentRequest request) {
        log.info("注册测试环境：{}", request.getName());

        var env = environmentService.registerEnvironment(
            request.getName(),
            request.getJdbcUrl(),
            request.getUsername(),
            request.getPassword(),
            request.getDriverClassName(),
            request.getMetadata()
        );

        if ("ERROR".equals(env.status())) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "连接测试失败",
                "environment", env
            ));
        }

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "environment", env
        ));
    }

    /**
     * 测试数据源连接
     * POST /api/v2/environments/{name}/test
     */
    @PostMapping("/{name}/test")
    public ResponseEntity<Map<String, Object>> testConnection(
            @PathVariable String name) {
        log.info("测试数据源连接：{}", name);

        boolean success = environmentService.testConnection(name);

        if (success) {
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "连接测试成功",
                "datasource", name
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "连接测试失败",
                "datasource", name
            ));
        }
    }

    /**
     * 获取数据源统计信息
     * GET /api/v2/environments/{name}/stats
     */
    @GetMapping("/{name}/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @PathVariable String name) {
        log.info("获取数据源统计：{}", name);

        var stats = environmentService.getDatasourceStats(name);
        return ResponseEntity.ok(stats);
    }

    /**
     * 移除测试环境
     * DELETE /api/v2/environments/{name}
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<Map<String, String>> removeEnvironment(
            @PathVariable String name) {
        log.info("移除测试环境：{}", name);

        var env = environmentService.getEnvironment(name);
        if (env == null) {
            return ResponseEntity.notFound().build();
        }

        // 配置的数据源不允许删除
        if ("CONFIG".equals(env.metadata().get("type"))) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "配置的数据源不允许删除"));
        }

        environmentService.removeEnvironment(name);
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    /**
     * 在指定数据源上执行 DDL
     * POST /api/v2/environments/{name}/ddl
     */
    @PostMapping("/{name}/ddl")
    public ResponseEntity<Map<String, Object>> executeDdl(
            @PathVariable String name,
            @RequestBody ExecuteDdlRequest request) {
        log.info("在数据源 {} 上执行 DDL: {}", name, request.getDdl());

        try {
            int rows = environmentService.executeDdl(name, request.getDdl());
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "rowsAffected", rows,
                "datasource", name
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage(),
                "datasource", name
            ));
        }
    }

    // ========== 请求对象 ==========

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class RegisterEnvironmentRequest {
        private String name;
        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    public static class ExecuteDdlRequest {
        private String ddl;
        private String description;
    }
}
