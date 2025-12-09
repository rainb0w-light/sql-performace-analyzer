package com.example.sqlanalyzer.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 分布式事务测试场景配置
 * 配置按thread分组，每个thread有独立的step列表
 * 所有thread完成当前step后，才能进入下一个step
 */
@Data
public class DistributedTransactionTestConfig {
    /**
     * 场景配置
     */
    private Scenario scenario;

    @Data
    public static class Scenario {
        /**
         * 场景名称
         */
        private String name;

        /**
         * 数据源名称（对应Spring Bean名称）
         */
        private String datasource;

        /**
         * 默认事务隔离级别（可选）
         * 支持：READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE
         */
        private String defaultIsolationLevel;

        /**
         * 线程配置，key为threadId，value为该thread的step列表
         * 所有thread的step数量应该相同，按索引对应（step0, step1, step2...）
         */
        private Map<String, ThreadConfig> threads;
    }

    @Data
    public static class ThreadConfig {
        /**
         * 该thread的step列表
         * 每个step可以包含多个SQL语句
         */
        private List<SqlExecutionStep> steps;
    }
}

