package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.config.DataSourceConfig.DataSourceConfigProperties;
import com.biz.sccba.sqlanalyzer.data.DataSourceConfigModel;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多数据源测试环境管理服务
 *
 * 功能:
 * 1. 管理多个测试环境数据源
 * 2. 支持动态切换数据源进行分析
 * 3. DDL 操作在指定数据源上执行
 * 4. 测试环境状态监控
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestEnvironmentService {

    private final DataSourceConfigProperties configProperties;

    // 动态注册的数据源（运行时创建）
    private final Map<String, TestEnvironment> dynamicEnvironments = new ConcurrentHashMap<>();

    // 数据源名称 -> JdbcTemplate 映射
    private final Map<String, JdbcTemplate> jdbcTemplateCache = new ConcurrentHashMap<>();

    /**
     * 测试环境信息
     */
    public record TestEnvironment(
        String name,
        String description,
        String jdbcUrl,
        String username,
        String status,          // ACTIVE, INACTIVE, ERROR
        Map<String, Object> metadata,
        long createdAt,
        long lastUsedAt
    ) {}

    /**
     * 获取所有可用的测试环境
     */
    public List<TestEnvironment> getAllTestEnvironments() {
        List<TestEnvironment> environments = new ArrayList<>();

        // 添加配置中的数据源
        if (configProperties.getConfigs() != null) {
            for (var config : configProperties.getConfigs()) {
                environments.add(new TestEnvironment(
                    config.getName(),
                    "配置数据源",
                    config.getUrl(),
                    config.getUsername(),
                    "ACTIVE",
                    Map.of("driver", config.getDriverClassName()),
                    System.currentTimeMillis(),
                    System.currentTimeMillis()
                ));
            }
        }

        // 添加动态注册的数据源
        environments.addAll(dynamicEnvironments.values());

        return environments;
    }

    /**
     * 根据名称获取测试环境
     */
    public TestEnvironment getEnvironment(String name) {
        // 先从动态环境查找
        if (dynamicEnvironments.containsKey(name)) {
            return dynamicEnvironments.get(name);
        }

        // 再从配置中查找
        if (configProperties.getConfigs() != null) {
            for (var config : configProperties.getConfigs()) {
                if (config.getName().equals(name)) {
                    return new TestEnvironment(
                        config.getName(),
                        "配置数据源",
                        config.getUrl(),
                        config.getUsername(),
                        "ACTIVE",
                        Map.of("driver", config.getDriverClassName()),
                        System.currentTimeMillis(),
                        System.currentTimeMillis()
                    );
                }
            }
        }

        return null;
    }

    /**
     * 动态注册测试环境
     */
    public TestEnvironment registerEnvironment(String name, String jdbcUrl,
                                                String username, String password,
                                                String driverClassName,
                                                Map<String, Object> metadata) {
        log.info("注册测试环境：{}", name);

        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setDriverClassName(driverClassName);
            hikariConfig.setMaximumPoolSize(5);
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setConnectionTimeout(10000);

            HikariDataSource dataSource = new HikariDataSource(hikariConfig);

            // 测试连接
            try (var conn = dataSource.getConnection()) {
                log.info("测试环境 {} 连接成功", name);
            }

            TestEnvironment env = new TestEnvironment(
                name,
                "动态注册的测试环境",
                jdbcUrl,
                username,
                "ACTIVE",
                metadata != null ? metadata : Map.of(),
                System.currentTimeMillis(),
                System.currentTimeMillis()
            );

            dynamicEnvironments.put(name, env);
            jdbcTemplateCache.put(name, new JdbcTemplate(dataSource));

            return env;

        } catch (Exception e) {
            log.error("注册测试环境失败：{}", name, e);
            TestEnvironment env = new TestEnvironment(
                name,
                "动态注册的测试环境",
                jdbcUrl,
                username,
                "ERROR",
                Map.of("error", e.getMessage()),
                System.currentTimeMillis(),
                System.currentTimeMillis()
            );
            dynamicEnvironments.put(name, env);
            return env;
        }
    }

    /**
     * 获取指定数据源的 JdbcTemplate
     */
    public JdbcTemplate getJdbcTemplate(String datasourceName) {
        if (datasourceName == null || datasourceName.isEmpty()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }

        // 先从缓存获取
        if (jdbcTemplateCache.containsKey(datasourceName)) {
            updateLastUsed(datasourceName);
            return jdbcTemplateCache.get(datasourceName);
        }

        // 从配置中创建
        if (configProperties.getConfigs() != null) {
            for (var config : configProperties.getConfigs()) {
                if (config.getName().equals(datasourceName)) {
                    JdbcTemplate template = createJdbcTemplate(config);
                    jdbcTemplateCache.put(datasourceName, template);
                    return template;
                }
            }
        }

        throw new IllegalArgumentException("未找到数据源：" + datasourceName);
    }

    /**
     * 创建 JdbcTemplate
     */
    private JdbcTemplate createJdbcTemplate(DataSourceConfigModel config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriverClassName());

        if (config.getHikari() != null) {
            var hikari = config.getHikari();
            hikariConfig.setMaximumPoolSize(hikari.getMaximumPoolSize() != null ?
                                            hikari.getMaximumPoolSize() : 10);
            hikariConfig.setMinimumIdle(hikari.getMinimumIdle() != null ?
                                        hikari.getMinimumIdle() : 5);
            hikariConfig.setConnectionTimeout(hikari.getConnectionTimeout() != null ?
                                              hikari.getConnectionTimeout() : 30000);
        }

        HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        return new JdbcTemplate(dataSource);
    }

    /**
     * 更新最后使用时间
     */
    private void updateLastUsed(String datasourceName) {
        if (dynamicEnvironments.containsKey(datasourceName)) {
            var env = dynamicEnvironments.get(datasourceName);
            dynamicEnvironments.put(datasourceName,
                new TestEnvironment(
                    env.name(),
                    env.description(),
                    env.jdbcUrl(),
                    env.username(),
                    env.status(),
                    env.metadata(),
                    env.createdAt(),
                    System.currentTimeMillis()
                )
            );
        }
    }

    /**
     * 执行 DDL 语句
     */
    public int executeDdl(String datasourceName, String ddl) {
        log.info("在数据源 {} 上执行 DDL: {}", datasourceName, ddl);
        JdbcTemplate template = getJdbcTemplate(datasourceName);
        return template.update(ddl);
    }

    /**
     * 测试数据源连接
     */
    public boolean testConnection(String datasourceName) {
        try {
            JdbcTemplate template = getJdbcTemplate(datasourceName);
            template.execute("SELECT 1");
            log.info("数据源 {} 连接测试成功", datasourceName);
            return true;
        } catch (Exception e) {
            log.error("数据源 {} 连接测试失败", datasourceName, e);
            return false;
        }
    }

    /**
     * 获取数据源统计信息
     */
    public Map<String, Object> getDatasourceStats(String datasourceName) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("name", datasourceName);

        try {
            JdbcTemplate template = getJdbcTemplate(datasourceName);

            // 获取数据库版本
            String dbVersion = template.queryForObject(
                "SELECT VERSION()", String.class);
            stats.put("version", dbVersion);

            // 获取数据库名称
            String dbName = template.queryForObject(
                "SELECT DATABASE()", String.class);
            stats.put("database", dbName);

            // 获取表数量
            Integer tableCount = template.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = DATABASE()", Integer.class);
            stats.put("tableCount", tableCount);

            stats.put("status", "ACTIVE");

        } catch (Exception e) {
            stats.put("status", "ERROR");
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 移除测试环境
     */
    public void removeEnvironment(String name) {
        if (dynamicEnvironments.containsKey(name)) {
            dynamicEnvironments.remove(name);
            jdbcTemplateCache.remove(name);
            log.info("测试环境 {} 已移除", name);
        } else {
            log.warn("测试环境 {} 不存在", name);
        }
    }

    /**
     * 获取数据源列表（简化版，用于 API 返回）
     */
    public List<Map<String, Object>> getDatasourceList() {
        List<Map<String, Object>> result = new ArrayList<>();

        // 配置的数据源
        if (configProperties.getConfigs() != null) {
            for (var config : configProperties.getConfigs()) {
                Map<String, Object> info = new HashMap<>();
                info.put("name", config.getName());
                info.put("url", config.getUrl());
                info.put("username", config.getUsername());
                info.put("type", "CONFIG");
                info.put("status", "ACTIVE");
                result.add(info);
            }
        }

        // 动态注册的数据源
        for (var env : dynamicEnvironments.values()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", env.name());
            info.put("url", env.jdbcUrl());
            info.put("username", env.username());
            info.put("type", "DYNAMIC");
            info.put("status", env.status());
            result.add(info);
        }

        return result;
    }
}
