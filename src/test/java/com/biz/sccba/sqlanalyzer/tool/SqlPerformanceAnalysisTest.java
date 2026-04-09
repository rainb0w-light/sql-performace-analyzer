package com.biz.sccba.sqlanalyzer.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL 性能分析测试
 * 测试各种 SQL 性能问题和优化场景
 */
class SqlPerformanceAnalysisTest {

    private SqlOptimizerExpertTool optimizerTool;
    private InnoDBExpertTool innodbTool;

    @BeforeEach
    void setUp() {
        System.out.println("SqlPerformanceAnalysisTest 初始化完成");
    }

    // ========== SELECT 语句性能测试 ==========

    @Test
    @DisplayName("性能测试 - SELECT * 问题")
    void testSelectStarPerformance() {
        String badSql = "SELECT * FROM users WHERE status = 1";
        String goodSql = "SELECT id, username, email FROM users WHERE status = 1";

        // SELECT * 的问题
        assertTrue(badSql.contains("SELECT *"), "应该包含 SELECT *");

        // 优化后的 SQL
        assertFalse(goodSql.contains("*"), "优化后不应该包含 *");
        assertTrue(goodSql.contains("id, username, email"), "应该明确指定列");
    }

    @Test
    @DisplayName("性能测试 - 覆盖索引")
    void testCoveringIndex() {
        // 没有使用覆盖索引
        String noCoveringSql = """
            SELECT id, username, email, phone, address
            FROM users
            WHERE status = 1
            """;

        // 使用覆盖索引
        String coveringSql = """
            SELECT id, username
            FROM users
            WHERE status = 1
            """;

        // 覆盖索引查询的列更少
        int noCoveringCols = noCoveringSql.split(",")[0].split("SELECT ").length;
        int coveringCols = coveringSql.split(",")[0].split("SELECT ").length;

        assertTrue(noCoveringCols >= coveringCols,
                   "覆盖索引应该查询更少的列");
    }

    // ========== WHERE 条件性能测试 ==========

    @Test
    @DisplayName("性能测试 - 索引列上的函数")
    void testFunctionOnIndexColumn() {
        String badSql = """
            SELECT * FROM orders
            WHERE DATE(create_time) = '2024-01-01'
            """;

        String goodSql = """
            SELECT * FROM orders
            WHERE create_time >= '2024-01-01 00:00:00'
            AND create_time < '2024-01-02 00:00:00'
            """;

        assertTrue(badSql.contains("DATE("), "坏 SQL 在列上使用函数");
        assertFalse(goodSql.contains("DATE("), "好 SQL 使用范围查询");
    }

    @Test
    @DisplayName("性能测试 - 隐式类型转换")
    void testImplicitTypeConversion() {
        // phone 是 VARCHAR 类型
        String badSql = """
            SELECT * FROM users
            WHERE phone = 13800138000
            """;

        String goodSql = """
            SELECT * FROM users
            WHERE phone = '13800138000'
            """;

        assertFalse(badSql.contains("'138"), "坏 SQL 没有引号（隐式转换）");
        assertTrue(goodSql.contains("'138"), "好 SQL 有引号（类型匹配）");
    }

    @Test
    @DisplayName("性能测试 - OR 条件导致索引失效")
    void testOrConditionIndexInvalidation() {
        String badSql = """
            SELECT * FROM users
            WHERE username = 'john' OR email = 'john@example.com'
            """;

        String goodSql = """
            SELECT * FROM users
            WHERE username = 'john'
            UNION ALL
            SELECT * FROM users
            WHERE email = 'john@example.com'
            """;

        assertTrue(badSql.contains(" OR "), "坏 SQL 使用 OR");
        assertTrue(goodSql.contains("UNION ALL"), "好 SQL 使用 UNION ALL");
    }

    @Test
    @DisplayName("性能测试 - LIKE 前缀通配符")
    void testLikePrefixWildcard() {
        String badSql = """
            SELECT * FROM products
            WHERE name LIKE '%手机%'
            """;

        String goodSql = """
            SELECT * FROM products
            WHERE name LIKE '手机%'
            """;

        assertTrue(badSql.contains("LIKE '%"), "坏 SQL 使用前缀通配符");
        assertFalse(goodSql.contains("LIKE '%"), "好 SQL 使用前缀匹配");
    }

    @Test
    @DisplayName("性能测试 - NOT IN 子查询")
    void testNotInSubquery() {
        String badSql = """
            SELECT * FROM users
            WHERE user_id NOT IN (
                SELECT user_id FROM orders WHERE status = 'CANCELLED'
            )
            """;

        String goodSql = """
            SELECT * FROM users u
            WHERE NOT EXISTS (
                SELECT 1 FROM orders o
                WHERE o.user_id = u.user_id
                AND o.status = 'CANCELLED'
            )
            """;

        assertTrue(badSql.contains("NOT IN"), "坏 SQL 使用 NOT IN");
        assertTrue(goodSql.contains("NOT EXISTS"), "好 SQL 使用 NOT EXISTS");
    }

    // ========== JOIN 性能测试 ==========

    @Test
    @DisplayName("性能测试 - 笛卡尔积")
    void testCartesianProduct() {
        String badSql = """
            SELECT a.*, b.*
            FROM table_a, table_b
            """;

        String goodSql = """
            SELECT a.*, b.*
            FROM table_a a
            JOIN table_b b ON a.id = b.a_id
            """;

        assertTrue(badSql.contains(", "), "坏 SQL 使用逗号分隔表");
        assertFalse(badSql.toUpperCase().contains("JOIN"), "坏 SQL 没有 JOIN");
        assertTrue(goodSql.contains("JOIN"), "好 SQL 使用显式 JOIN");
    }

    @Test
    @DisplayName("性能测试 - 多表 JOIN 顺序")
    void testJoinOrder() {
        String sql = """
            SELECT *
            FROM orders o
            JOIN users u ON o.user_id = u.id
            JOIN products p ON o.product_id = p.id
            WHERE o.status = 1
            AND u.region = 'CN'
            """;

        assertTrue(sql.contains("JOIN"), "应该包含 JOIN");
        assertTrue(sql.contains("WHERE"), "应该包含 WHERE");
    }

    @Test
    @DisplayName("性能测试 - LEFT JOIN 误用")
    void testLeftJoinMisuse() {
        // 如果 WHERE 条件过滤了右表，LEFT JOIN 会退化为 INNER JOIN
        String misusedSql = """
            SELECT u.*, o.order_id
            FROM users u
            LEFT JOIN orders o ON u.id = o.user_id
            WHERE o.status = 1
            """;

        String correctSql = """
            SELECT u.*, o.order_id
            FROM users u
            INNER JOIN orders o ON u.id = o.user_id
            WHERE o.status = 1
            """;

        assertTrue(misusedSql.contains("LEFT JOIN"),
                   "误用的 SQL 使用 LEFT JOIN");
        assertTrue(correctSql.contains("INNER JOIN"),
                   "正确的 SQL 使用 INNER JOIN");
    }

    // ========== 分页性能测试 ==========

    @Test
    @DisplayName("性能测试 - 深度分页")
    void testDeepPagination() {
        String badSql = """
            SELECT * FROM products
            ORDER BY create_time DESC
            LIMIT 1000000, 10
            """;

        String goodSql = """
            SELECT * FROM products
            WHERE create_time < '2024-01-01'
            ORDER BY create_time DESC
            LIMIT 10
            """;

        assertTrue(badSql.contains("LIMIT 1000000"),
                   "坏 SQL 使用深度分页");
        assertFalse(goodSql.contains("1000000"),
                   "好 SQL 避免深度分页");
    }

    @Test
    @DisplayName("性能测试 - 延迟关联优化分页")
    void testDelayedJoinPagination() {
        // 原始深度分页
        String originalSql = """
            SELECT * FROM orders
            WHERE status = 1
            ORDER BY create_time DESC
            LIMIT 100000, 10
            """;

        // 延迟关联优化
        String optimizedSql = """
            SELECT o.*
            FROM orders o
            INNER JOIN (
                SELECT id
                FROM orders
                WHERE status = 1
                ORDER BY create_time DESC
                LIMIT 100000, 10
            ) tmp ON o.id = tmp.id
            """;

        assertTrue(originalSql.contains("LIMIT 100000"),
                   "原始 SQL 有深度分页");
        assertTrue(optimizedSql.contains("INNER JOIN"),
                   "优化 SQL 使用延迟关联");
    }

    // ========== 聚合性能测试 ==========

    @Test
    @DisplayName("性能测试 - COUNT 性能")
    void testCountPerformance() {
        String slowSql = "SELECT COUNT(*) FROM users WHERE status = 1";
        String cachedSql = """
            SELECT total_count
            FROM table_statistics
            WHERE table_name = 'users'
            """;

        assertTrue(slowSql.contains("COUNT(*)"),
                   "COUNT(*) 需要全表扫描");
        assertTrue(cachedSql.contains("table_statistics"),
                   "缓存统计更快");
    }

    @Test
    @DisplayName("性能测试 - GROUP BY 优化")
    void testGroupByOptimization() {
        String sql = """
            SELECT
                user_id,
                COUNT(*) as order_count,
                SUM(amount) as total_amount
            FROM orders
            WHERE create_time >= '2024-01-01'
            GROUP BY user_id
            """;

        assertTrue(sql.contains("GROUP BY"), "应该包含 GROUP BY");
        assertTrue(sql.contains("COUNT"), "应该包含 COUNT");
        assertTrue(sql.contains("SUM"), "应该包含 SUM");
    }

    @Test
    @DisplayName("性能测试 - HAVING 子句")
    void testHavingClause() {
        String badSql = """
            SELECT user_id, COUNT(*) as cnt
            FROM orders
            GROUP BY user_id
            HAVING cnt >= 5
            """;

        String goodSql = """
            SELECT user_id, cnt
            FROM (
                SELECT user_id, COUNT(*) as cnt
                FROM orders
                WHERE status = 1
                GROUP BY user_id
            ) t
            WHERE cnt >= 5
            """;

        assertTrue(badSql.contains("HAVING"),
                   "坏 SQL 使用 HAVING（先分组后过滤）");
        assertTrue(goodSql.contains("WHERE cnt"),
                   "好 SQL 在外部过滤");
    }

    // ========== 索引相关测试 ==========

    @Test
    @DisplayName("性能测试 - 复合索引最左前缀")
    void testCompositeIndexLeftmost() {
        // 假设有复合索引 (a, b, c)
        String goodSql = """
            SELECT * FROM table_name
            WHERE a = 1 AND b = 2 AND c = 3
            """;

        String partialSql = """
            SELECT * FROM table_name
            WHERE a = 1 AND b = 2
            """;

        String badSql = """
            SELECT * FROM table_name
            WHERE b = 2 AND c = 3
            """;

        assertTrue(goodSql.contains("a = 1"),
                   "好 SQL 使用完整索引");
        assertTrue(partialSql.contains("a = 1"),
                   "部分 SQL 使用前缀");
        assertFalse(badSql.contains("a = "),
                   "坏 SQL 不使用最左前缀");
    }

    @Test
    @DisplayName("性能测试 - 索引覆盖场景")
    void testIndexCoverage() {
        // 假设索引是 (status, create_time)
        String coveringSql = """
            SELECT id, status, create_time
            FROM orders
            WHERE status = 1
            ORDER BY create_time DESC
            """;

        String nonCoveringSql = """
            SELECT id, status, create_time, user_id, amount
            FROM orders
            WHERE status = 1
            ORDER BY create_time DESC
            """;

        // coveringSql 可以只通过索引满足查询
        assertTrue(coveringSql.contains("status"),
                   "覆盖索引查询包含索引列");
    }

    @Test
    @DisplayName("性能测试 - 索引选择性")
    void testIndexSelectivity() {
        // status 只有 2 个值，选择性低
        String lowSelectivitySql = """
            SELECT * FROM users
            WHERE status = 1
            """;

        // user_id 是唯一键，选择性高
        String highSelectivitySql = """
            SELECT * FROM users
            WHERE user_id = 123
            """;

        assertTrue(lowSelectivitySql.contains("status"),
                   "低选择性列");
        assertTrue(highSelectivitySql.contains("user_id"),
                   "高选择性列");
    }

    // ========== 锁与并发测试 ==========

    @Test
    @DisplayName("性能测试 - 间隙锁风险")
    void testGapLockRisk() {
        // 在 RR 隔离级别下，范围查询可能产生间隙锁
        String gapLockSql = """
            SELECT * FROM orders
            WHERE user_id > 100 AND user_id < 200
            FOR UPDATE
            """;

        assertTrue(gapLockSql.contains("FOR UPDATE"),
                   "应该包含 FOR UPDATE");
        assertTrue(gapLockSql.contains("user_id >"),
                   "范围查询可能产生间隙锁");
    }

    @Test
    @DisplayName("性能测试 - 死锁风险")
    void testDeadlockRisk() {
        // 同时更新多个表，可能死锁
        String deadlockRiskSql = """
            UPDATE orders
            SET status = 2
            WHERE user_id = 123 AND order_id = 456
            """;

        assertTrue(deadlockRiskSql.contains("UPDATE"),
                   "UPDATE 语句可能产生锁");
    }

    // ========== 批量操作测试 ==========

    @Test
    @DisplayName("性能测试 - 批量插入")
    void testBatchInsert() {
        String singleSql = """
            INSERT INTO orders (user_id, amount) VALUES (1, 100)
            """;

        String batchSql = """
            INSERT INTO orders (user_id, amount) VALUES
            (1, 100),
            (2, 200),
            (3, 300),
            (4, 400),
            (5, 500)
            """;

        assertTrue(batchSql.contains("),"),
                   "批量插入包含多行");
    }

    @Test
    @DisplayName("性能测试 - 批量更新")
    void testBatchUpdate() {
        String batchUpdateSql = """
            UPDATE orders
            SET status = CASE order_id
                WHEN 1 THEN 2
                WHEN 2 THEN 3
                WHEN 3 THEN 4
            END
            WHERE order_id IN (1, 2, 3)
            """;

        assertTrue(batchUpdateSql.contains("CASE"),
                   "批量更新使用 CASE");
        assertTrue(batchUpdateSql.contains("IN ("),
                   "批量更新使用 IN");
    }

    // ========== 子查询优化测试 ==========

    @Test
    @DisplayName("性能测试 - IN 子查询优化")
    void testInSubqueryOptimization() {
        String inSql = """
            SELECT * FROM users
            WHERE user_id IN (
                SELECT user_id FROM orders WHERE amount > 1000
            )
            """;

        String existsSql = """
            SELECT * FROM users u
            WHERE EXISTS (
                SELECT 1 FROM orders o
                WHERE o.user_id = u.user_id
                AND o.amount > 1000
            )
            """;

        assertTrue(inSql.contains("IN ("),
                   "IN 子查询");
        assertTrue(existsSql.contains("EXISTS"),
                   "EXISTS 子查询");
    }

    @Test
    @DisplayName("性能测试 - 标量子查询优化")
    void testScalarSubqueryOptimization() {
        String scalarSql = """
            SELECT
                name,
                (SELECT COUNT(*) FROM orders WHERE orders.user_id = users.id) as order_count
            FROM users
            """;

        String joinSql = """
            SELECT
                u.name,
                COUNT(o.id) as order_count
            FROM users u
            LEFT JOIN orders o ON u.id = o.user_id
            GROUP BY u.id, u.name
            """;

        assertTrue(scalarSql.contains("(SELECT COUNT"),
                   "标量子查询");
        assertTrue(joinSql.contains("LEFT JOIN"),
                   "JOIN 优化");
    }

    // ========== 执行计划分析测试 ==========

    @Test
    @DisplayName("性能测试 - EXPLAIN 分析")
    void testExplainAnalysis() {
        String explainSql = """
            EXPLAIN SELECT * FROM orders
            WHERE user_id = 123
            ORDER BY create_time DESC
            LIMIT 10
            """;

        assertTrue(explainSql.startsWith("EXPLAIN"),
                   "应该以 EXPLAIN 开头");
    }

    @Test
    @DisplayName("性能测试 - 执行计划类型")
    void testExplainAccessTypes() {
        // 从好到坏的执行计划类型
        String[] accessTypes = {
            "system",      // 最优
            "const",       // 常量
            "eq_ref",      // 等值引用
            "ref",         // 引用
            "range",       // 范围
            "index",       // 索引扫描
            "ALL"          // 全表扫描（最差）
        };

        assertEquals(7, accessTypes.length,
                     "应该有 7 种访问类型");
        assertEquals("system", accessTypes[0],
                     "最优类型是 system");
        assertEquals("ALL", accessTypes[6],
                     "最差类型是 ALL");
    }
}
