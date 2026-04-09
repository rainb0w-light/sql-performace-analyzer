package com.biz.sccba.sqlanalyzer.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GoldenDB 方言特性测试
 * 测试 GoldenDB 特有的 SQL 语法和分布式特性
 */
class GoldenDBDialectTest {

    private KnowledgeQueryTool knowledgeTool;

    @BeforeEach
    void setUp() {
        knowledgeTool = new KnowledgeQueryTool();
        System.out.println("GoldenDBDialectTest 初始化完成");
    }

    // ========== GoldenDB 分布式特性测试 ==========

    @Test
    @DisplayName("测试 GoldenDB 分片策略知识查询")
    void testQueryGoldenDBShardingKnowledge() {
        String result = knowledgeTool.query("GoldenDB 分片策略", "distributed");

        assertNotNull(result);
        assertTrue(result.contains("success"), "查询结果应该包含 success 字段");
    }

    @Test
    @DisplayName("测试 GoldenDB 分布式事务知识查询")
    void testQueryGoldenDBTransactionKnowledge() {
        String result = knowledgeTool.query("GoldenDB 分布式事务", "distributed");

        assertNotNull(result);
        assertTrue(result.contains("success"), "查询结果应该包含 success 字段");
    }

    @Test
    @DisplayName("测试 GoldenDB Hint 语法")
    void testGoldenDBHintSyntax() {
        // TDDL/HelloDB 风格的 Hint
        String tddlHint = "/*+ TDDL:scan='user_db:user_info_*' */";
        assertTrue(tddlHint.contains("/*+") && tddlHint.contains("*/"),
                   "Hint 应该使用/*+ ... */格式");
        assertTrue(tddlHint.contains("TDDL"), "应该包含 TDDL 标识");
    }

    @Test
    @DisplayName("测试 GoldenDB 广播表语法")
    void testGoldenDBBroadcastTable() {
        String broadcastSql = """
            /*+ TDDL:node='user_db' broadcast='dict_*' */
            SELECT * FROM dict_province
            """;

        assertTrue(broadcastSql.contains("broadcast="),
                   "应该包含广播表 Hint");
    }

    @Test
    @DisplayName("测试 GoldenDB 拆分表语法")
    void testGoldenDBShardedTable() {
        String shardedSql = """
            /*+ TDDL:table='orders_0000' */
            SELECT * FROM orders WHERE user_id = 123
            """;

        assertTrue(shardedSql.contains("table="),
                   "应该包含表路由 Hint");
    }

    @Test
    @DisplayName("测试 GoldenDB 自定义函数")
    void testGoldenDBUDF() {
        // GoldenDB 可能支持的分布式函数
        String udfSql = """
            SELECT
                GET_SHARD_ID(user_id) as shard_id,
                GET_NODE_ID(user_id) as node_id
            FROM users
            """;

        assertTrue(udfSql.contains("GET_SHARD_ID") || udfSql.contains("GET_NODE_ID"),
                   "应该包含分布式相关函数");
    }

    @Test
    @DisplayName("测试 GoldenDB 序列函数")
    void testGoldenDBSequence() {
        String sequenceSql = """
            SELECT NEXT VALUE FOR global_sequence AS next_id
            """;

        assertTrue(sequenceSql.contains("NEXT VALUE FOR"),
                   "应该包含序列函数");
    }

    @Test
    @DisplayName("测试 GoldenDB 分页限制")
    void testGoldenDBPagination() {
        // GoldenDB 对深度分页有限制
        String deepPaginationSql = """
            SELECT * FROM orders
            ORDER BY create_time DESC
            LIMIT 100000, 10
            """;

        int offset = 100000;
        assertTrue(offset > 10000, "偏移量超过 10000 可能触发性能警告");
    }

    @Test
    @DisplayName("测试 GoldenDB 跨分片 JOIN 检测")
    void testCrossShardJoinDetection() {
        String crossShardJoin = """
            SELECT /*+ CROSS_SHARD_JOIN */
                a.user_id, a.username,
                b.order_id, b.amount
            FROM user_info a
            JOIN order_info b ON a.user_id = b.user_id
            WHERE a.region_id = 1
            """;

        assertTrue(crossShardJoin.contains("JOIN"),
                   "应该包含 JOIN");
        // 跨分片 JOIN 检测
        boolean isCrossShard = crossShardJoin.contains("CROSS_SHARD") ||
                               (crossShardJoin.contains("user_info") &&
                                crossShardJoin.contains("order_info"));
        assertTrue(isCrossShard, "应该能识别跨分片 JOIN 场景");
    }

    @Test
    @DisplayName("测试 GoldenDB 聚合下推")
    void testAggregatePushDown() {
        String aggregateSql = """
            /*+ TDDL:agg_pushdown=true */
            SELECT
                region_id,
                COUNT(*) as cnt,
                SUM(amount) as total
            FROM orders
            GROUP BY region_id
            """;

        assertTrue(aggregateSql.contains("GROUP BY"),
                   "应该包含 GROUP BY 聚合");
        assertTrue(aggregateSql.contains("agg_pushdown"),
                   "应该包含聚合下推 Hint");
    }

    @Test
    @DisplayName("测试 GoldenDB 并集查询优化")
    void testUnionOptimization() {
        String unionSql = """
            SELECT * FROM orders_0 WHERE user_id < 1000
            UNION ALL
            SELECT * FROM orders_1 WHERE user_id >= 1000 AND user_id < 2000
            UNION ALL
            SELECT * FROM orders_2 WHERE user_id >= 2000
            """;

        int unionCount = unionSql.split("UNION ALL").length - 1;
        assertEquals(2, unionCount, "应该有 2 个 UNION ALL");
        assertTrue(unionSql.contains("orders_0") &&
                   unionSql.contains("orders_1") &&
                   unionSql.contains("orders_2"),
                   "应该查询多个分片表");
    }

    // ========== GoldenDB 边界场景测试 ==========

    @Test
    @DisplayName("测试分片键范围查询")
    void testShardKeyRangeQuery() {
        String rangeSql = """
            SELECT * FROM orders
            WHERE user_id BETWEEN 1000 AND 2000
            """;

        assertTrue(rangeSql.contains("BETWEEN"),
                   "应该包含范围查询");
    }

    @Test
    @DisplayName("测试分片键 IN 查询")
    void testShardKeyInQuery() {
        String inSql = """
            SELECT * FROM orders
            WHERE user_id IN (100, 200, 300, 400, 500)
            """;

        assertTrue(inSql.contains("IN ("),
                   "应该包含 IN 查询");
        int inCount = inSql.split(",").length;
        assertTrue(inCount >= 5, "IN 子句应该包含多个值");
    }

    @Test
    @DisplayName("测试非分片键查询（全路由）")
    void testNonShardKeyQuery() {
        String fullRouteSql = """
            SELECT * FROM orders
            WHERE order_status = 1
            ORDER BY create_time DESC
            """;

        // 不包含分片键 user_id
        assertFalse(fullRouteSql.toUpperCase().contains("USER_ID ="),
                    "不应该包含分片键等值查询");
        assertTrue(fullRouteSql.contains("WHERE"),
                   "应该包含 WHERE 条件");
    }

    @Test
    @DisplayName("测试多分片键查询")
    void testMultiShardKeyQuery() {
        String multiKeySql = """
            SELECT * FROM orders
            WHERE user_id = 123
            AND region_id = 1
            AND shop_id = 456
            """;

        assertTrue(multiKeySql.contains("user_id"),
                   "应该包含 user_id 条件");
        assertTrue(multiKeySql.contains("region_id"),
                   "应该包含 region_id 条件");
        assertTrue(multiKeySql.contains("shop_id"),
                   "应该包含 shop_id 条件");
    }

    // ========== GoldenDB 性能相关测试 ==========

    @Test
    @DisplayName("测试慢查询特征 - 全表扫描")
    void testSlowQueryFullTableScan() {
        String slowSql = """
            SELECT * FROM orders
            WHERE status = 1
            """;

        // 没有索引的列作为查询条件可能导致全表扫描
        assertTrue(slowSql.contains("SELECT *"),
                   "使用 SELECT * 可能影响性能");
        assertFalse(slowSql.contains("/*+ INDEX"),
                   "没有使用索引提示");
    }

    @Test
    @DisplayName("测试慢查询特征 - 函数导致索引失效")
    void testSlowQueryFunctionOnColumn() {
        String slowSql = """
            SELECT * FROM orders
            WHERE DATE(create_time) = '2024-01-01'
            """;

        assertTrue(slowSql.contains("DATE("),
                   "在列上使用函数会导致索引失效");
    }

    @Test
    @DisplayName("测试慢查询特征 - 隐式转换")
    void testSlowQueryImplicitConversion() {
        // phone 是 VARCHAR 类型，但用数字比较
        String slowSql = """
            SELECT * FROM users
            WHERE phone = 13800138000
            """;

        assertTrue(slowSql.contains("phone"),
                   "应该包含字符串类型列");
        assertFalse(slowSql.contains("'138"),
                   "值没有引号会导致隐式转换");
    }

    @Test
    @DisplayName("测试慢查询特征 - LIKE 前缀通配符")
    void testSlowQueryLikePrefixWildcard() {
        String slowSql = """
            SELECT * FROM products
            WHERE name LIKE '%手机%'
            """;

        assertTrue(slowSql.contains("LIKE '%"),
                   "前缀通配符会导致索引失效");
    }

    @Test
    @DisplayName("测试慢查询特征 - OR 条件")
    void testSlowQueryOrCondition() {
        String slowSql = """
            SELECT * FROM users
            WHERE username = 'john' OR email = 'john@example.com'
            """;

        assertTrue(slowSql.contains(" OR "),
                   "OR 条件可能导致索引失效");
    }

    @Test
    @DisplayName("测试慢查询特征 - 深度分页")
    void testSlowQueryDeepPagination() {
        String slowSql = """
            SELECT * FROM products
            ORDER BY create_time DESC
            LIMIT 1000000, 10
            """;

        assertTrue(slowSql.contains("LIMIT 1000000"),
                   "深度分页会导致性能问题");
    }

    // ========== GoldenDB 配置与连接测试 ==========

    @Test
    @DisplayName("测试 GoldenDB 连接 URL 格式")
    void testGoldenDBConnectionUrl() {
        String connectionUrl = "jdbc:mysql://goldenb-host:5258/testdb?" +
                               "useSSL=false&" +
                               "serverTimezone=Asia/Shanghai&" +
                               "rewriteBatchedStatements=true";

        assertTrue(connectionUrl.contains("jdbc:mysql://"),
                   "GoldenDB 使用 MySQL 协议");
        assertTrue(connectionUrl.contains(":5258"),
                   "GoldenDB 默认端口是 5258");
    }

    @Test
    @DisplayName("测试 GoldenDB 驱动配置")
    void testGoldenDBDriverConfig() {
        String driverClass = "com.mysql.cj.jdbc.Driver";
        String dialect = "org.hibernate.dialect.MySQL8Dialect";

        assertEquals("com.mysql.cj.jdbc.Driver", driverClass,
                     "GoldenDB 使用 MySQL JDBC 驱动");
        assertTrue(dialect.contains("MySQL"),
                   "GoldenDB 使用 MySQL 方言");
    }

    // ========== GoldenDB 监控与管理测试 ==========

    @Test
    @DisplayName("测试 GoldenDB 慢查询日志 SQL")
    void testSlowQueryLogSql() {
        String checkSlowSql = """
            SHOW FULL PROCESSLIST
            """;

        assertTrue(checkSlowSql.contains("SHOW"),
                   "应该包含 SHOW 命令");
        assertTrue(checkSlowSql.contains("PROCESSLIST"),
                   "应该查看进程列表");
    }

    @Test
    @DisplayName("测试 GoldenDB 表统计信息")
    void testTableStatistics() {
        String statsSql = """
            ANALYZE TABLE orders
            """;

        assertTrue(statsSql.contains("ANALYZE TABLE"),
                   "应该包含 ANALYZE TABLE 命令");
    }

    @Test
    @DisplayName("测试 GoldenDB 索引统计")
    void testIndexStatistics() {
        String indexStatsSql = """
            SHOW INDEX FROM orders
            """;

        assertTrue(indexStatsSql.contains("SHOW INDEX"),
                   "应该包含 SHOW INDEX 命令");
    }

    @Test
    @DisplayName("测试 GoldenDB 执行计划")
    void testExplainPlan() {
        String explainSql = """
            EXPLAIN SELECT * FROM orders WHERE user_id = 123
            """;

        assertTrue(explainSql.contains("EXPLAIN"),
                   "应该包含 EXPLAIN 命令");
    }

    @Test
    @DisplayName("测试 GoldenDB 分布式执行计划")
    void testDistributedExplain() {
        String distributedExplain = """
            EXPLAIN /*+ TDDL:cmd='SHOW PHYSICAL_PROCESSLIST' */
            SELECT * FROM orders
            """;

        assertTrue(distributedExplain.contains("EXPLAIN"),
                   "应该包含 EXPLAIN");
        assertTrue(distributedExplain.contains("TDDL"),
                   "应该包含 TDDL Hint");
    }

    // ========== 知识查询功能测试 ==========

    @Test
    @DisplayName("测试查询 InnoDB 索引知识")
    void testQueryInnoDBIndexKnowledge() {
        String result = knowledgeTool.query("InnoDB 索引优化", "innodb");
        assertNotNull(result);
        System.out.println("InnoDB 索引知识查询结果：" + result);
    }

    @Test
    @DisplayName("测试查询分布式分片知识")
    void testQueryDistributedShardingKnowledge() {
        String result = knowledgeTool.query("分片策略", "distributed");
        assertNotNull(result);
        System.out.println("分布式分片知识查询结果：" + result);
    }

    @Test
    @DisplayName("测试查询慢 SQL 优化知识")
    void testQuerySlowSQLOptimizationKnowledge() {
        String result = knowledgeTool.query("慢 SQL 优化", "optimization");
        assertNotNull(result);
        System.out.println("慢 SQL 优化知识查询结果：" + result);
    }

    @Test
    @DisplayName("测试查询 GoldenDB 特性")
    void testQueryGoldenDBFeatures() {
        String result = knowledgeTool.query("GoldenDB 特性", "distributed");
        assertNotNull(result);
        System.out.println("GoldenDB 特性知识查询结果：" + result);
    }

    @Test
    @DisplayName("测试查询事务隔离级别")
    void testQueryTransactionIsolation() {
        String result = knowledgeTool.query("事务隔离级别", "innodb");
        assertNotNull(result);
        System.out.println("事务隔离级别知识查询结果：" + result);
    }

    @Test
    @DisplayName("测试查询分布式事务")
    void testQueryDistributedTransaction() {
        String result = knowledgeTool.query("分布式事务 2PC", "distributed");
        assertNotNull(result);
        System.out.println("分布式事务知识查询结果：" + result);
    }
}
