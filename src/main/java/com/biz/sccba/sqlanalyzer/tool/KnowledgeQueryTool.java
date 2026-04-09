package com.biz.sccba.sqlanalyzer.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识查询工具
 * 查询 InnoDB、分布式数据库等技术知识
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeQueryTool {

    // 知识库缓存
    private final Map<String, String> knowledgeCache = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 查询知识
     *
     * @param query    查询内容
     * @param category 知识类别 (innodb/distributed/general)
     * @return 知识结果 JSON
     */
    @Tool(name = "query_knowledge", description = "查询 InnoDB、分布式数据库等技术知识")
    public String query(
            @ToolParam(name = "query", description = "查询内容", required = true) String query,
            @ToolParam(name = "category", description = "知识类别 (innodb/distributed/general)", required = false) String category) {
        log.info("查询知识：{} (类别：{})", query, category);

        // 构建查询键
        String queryKey = (category != null ? category + ":" : "") + query.toLowerCase();

        // 先检查缓存
        String cachedResult = knowledgeCache.get(queryKey);
        if (cachedResult != null) {
            try {
                return objectMapper.writeValueAsString(KnowledgeResult.builder()
                    .success(true)
                    .query(query)
                    .category(category)
                    .answer(cachedResult)
                    .fromCache(true)
                    .build());
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }

        // 查询知识库
        String answer = searchKnowledge(query, category);

        KnowledgeResult result = KnowledgeResult.builder()
            .success(answer != null)
            .query(query)
            .category(category)
            .answer(answer)
            .fromCache(false)
            .build();

        // 缓存结果
        if (answer != null) {
            knowledgeCache.put(queryKey, answer);
        }

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取所有知识类别
     */
    public List<String> getCategories() {
        return List.of("innodb", "distributed", "general", "optimization");
    }

    /**
     * 搜索知识
     */
    private String searchKnowledge(String query, String category) {
        // 基于关键词匹配
        String queryLower = query.toLowerCase();

        // InnoDB 知识
        if (category == null || "innodb".equals(category)) {
            if (queryLower.contains("索引") || queryLower.contains("index")) {
                return getInnoDBIndexKnowledge();
            }
            if (queryLower.contains("锁") || queryLower.contains("lock")) {
                return getInnoDBLockKnowledge();
            }
            if (queryLower.contains("事务") || queryLower.contains("transaction")) {
                return getInnoDBTransactionKnowledge();
            }
        }

        // 分布式数据库知识
        if (category == null || "distributed".equals(category)) {
            if (queryLower.contains("分片") || queryLower.contains("sharding")) {
                return getDistributedShardingKnowledge();
            }
            if (queryLower.contains("分布式事务") || queryLower.contains("distributed transaction")) {
                return getDistributedTransactionKnowledge();
            }
        }

        // 优化知识
        if (category == null || "optimization".equals(category)) {
            if (queryLower.contains("慢 sql") || queryLower.contains("慢查询") || queryLower.contains("slow")) {
                return getSlowSQLKnowledge();
            }
        }

        return null;
    }

    /**
     * InnoDB 索引知识
     */
    private String getInnoDBIndexKnowledge() {
        return """
            ## InnoDB 索引优化指南

            ### B+ 树索引结构
            - **聚簇索引**: 主键自动创建，数据按主键顺序存储
            - **二级索引**: 叶子节点存储主键值，需要回表查询
            - **联合索引**: 遵循最左前缀原则

            ### 索引选择原则
            1. 选择性高的列优先 (区分度高)
            2. 覆盖索引减少回表
            3. 避免在低选择性列创建索引
            4. 考虑索引维护成本

            ### 常见优化技巧
            - 使用 EXPLAIN 分析索引使用情况
            - 避免 SELECT *，只查询需要的列
            - 使用索引提示 FORCE INDEX/USE INDEX
            - 定期使用 ANALYZE TABLE 更新统计信息
            """;
    }

    /**
     * InnoDB 锁知识
     */
    private String getInnoDBLockKnowledge() {
        return """
            ## InnoDB 锁机制

            ### 行锁类型
            - **共享锁 (S 锁)**: 读锁，允许其他事务读
            - **排他锁 (X 锁)**: 写锁，阻塞其他事务

            ### 间隙锁 (Gap Lock)
            - 防止幻读
            - 在 RR 隔离级别下生效
            - 锁定索引记录之间的间隙

            ### 死锁检测与预防
            1. 保持事务简短
            2. 按固定顺序访问表
            3. 设置合理的锁等待超时
            4. 使用 SHOW ENGINE INNODB STATUS 分析死锁
            """;
    }

    /**
     * InnoDB 事务知识
     */
    private String getInnoDBTransactionKnowledge() {
        return """
            ## InnoDB 事务隔离级别

            ### 隔离级别
            - **READ UNCOMMITTED**: 可能读到未提交数据
            - **READ COMMITTED**: 读已提交，每次读取最新数据
            - **REPEATABLE READ**: 可重复读，MVCC 实现
            - **SERIALIZABLE**: 串行化，最高隔离级别

            ### MVCC 机制
            - 每行数据包含隐藏列 (DB_TRX_ID, DB_ROLL_PTR)
            - Undo Log 实现版本链
            - Read View 决定可见性
            """;
    }

    /**
     * 分布式数据库分片知识
     */
    private String getDistributedShardingKnowledge() {
        return """
            ## 分布式数据库分片策略

            ### 分片键选择
            - 热点分散：避免单点热点
            - 数据均匀：保证负载均衡
            - 业务关联：考虑跨分片查询

            ### 分片策略
            - **Hash 分片**: 数据均匀，但不支持范围查询
            - **Range 分片**: 支持范围查询，但可能热点
            - **复合分片**: 结合多种策略

            ### GoldenDB 特性
            - 支持自动分片
            - 支持在线扩容
            - 跨分片 JOIN 优化
            """;
    }

    /**
     * 分布式事务知识
     */
    private String getDistributedTransactionKnowledge() {
        return """
            ## 分布式事务

            ### 两阶段提交 (2PC)
            1. Prepare 阶段：所有参与者准备
            2. Commit/Rollback 阶段：协调者决定提交或回滚

            ### 最终一致性方案
            - TCC(Try-Confirm-Cancel)
            - Sagas 模式
            - 本地消息表

            ### GoldenDB 分布式事务
            - 支持 XA 协议
            - 优化两阶段提交性能
            - 自动故障恢复
            """;
    }

    /**
     * 慢 SQL 知识
     */
    private String getSlowSQLKnowledge() {
        return """
            ## 慢 SQL 优化

            ### 慢 SQL 识别
            - 全表扫描 (ACCESS_TYPE=ALL)
            - 索引失效
            - 大量数据扫描 (rows_examined 过高)
            - 临时表使用
            - 文件排序

            ### 优化步骤
            1. 使用 EXPLAIN 分析执行计划
            2. 检查索引使用情况
            3. 分析表结构和统计信息
            4. 考虑 SQL 重写
            5. 评估硬件资源

            ### 常见优化手段
            - 添加合适索引
            - 分页优化
            - 子查询改 JOIN
            - 避免函数操作索引列
            """;
    }

    /**
     * 知识查询结果
     */
    @lombok.Data
    @lombok.Builder
    public static class KnowledgeResult {
        private boolean success;
        private String query;
        private String category;
        private String answer;
        private boolean fromCache;
        private List<String> relatedTopics;
    }
}
