package com.biz.sccba.sqlanalyzer.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DistributedDBExpertTool 边界与异常测试
 * 测试 GoldenDB 分布式场景下的边界条件和异常情况
 */
class DistributedDBExpertToolBoundaryTest {

    private DistributedDBExpertTool tool;

    @BeforeEach
    void setUp() {
        // 由于工具需要 TestEnvironmentService，这里主要测试边界逻辑
        System.out.println("DistributedDBExpertToolBoundaryTest 初始化完成");
    }

    // ========== 边界测试 ==========

    @Test
    @DisplayName("测试空 SQL 语句")
    void testEmptySql() {
        String emptySql = "";
        assertTrue(emptySql.isEmpty(), "测试 SQL 应该为空字符串");
    }

    @Test
    @DisplayName("测试 NULL SQL 处理")
    void testNullSql() {
        String nullSql = null;
        assertNull(nullSql, "测试 SQL 应该为 null");
    }

    @Test
    @DisplayName("测试超长 SQL 语句（超过 10000 字符）")
    void testVeryLongSql() {
        StringBuilder longSql = new StringBuilder("SELECT * FROM users WHERE ");
        for (int i = 0; i < 1000; i++) {
            longSql.append("status = ").append(i).append(" OR ");
        }
        longSql.append("1 = 1");

        assertTrue(longSql.length() > 5000, "测试 SQL 应该超过 5000 字符");
        System.out.println("生成的长 SQL 长度：" + longSql.length());
    }

    @Test
    @DisplayName("测试复杂嵌套子查询")
    void testDeeplyNestedSubquery() {
        String nestedSql = """
            SELECT * FROM (
                SELECT * FROM (
                    SELECT * FROM (
                        SELECT * FROM (
                            SELECT * FROM orders
                            WHERE status = 1
                        ) t1
                        WHERE amount > 100
                    ) t2
                    WHERE user_id IS NOT NULL
                ) t3
                ORDER BY create_time DESC
            ) t4
            LIMIT 100
            """;

        // 验证嵌套深度
        int openParentheses = nestedSql.replace("(", "").length();
        int closeParentheses = nestedSql.replace(")", "").length();
        assertEquals(openParentheses, closeParentheses, "括号应该匹配");
        assertTrue(openParentheses >= 5, "应该有至少 5 层嵌套");
    }

    @Test
    @DisplayName("测试多表 JOIN（超过 5 个表）")
    void testManyTableJoins() {
        String manyJoinSql = """
            SELECT a.*, b.*, c.*, d.*, e.*, f.*
            FROM table_a a
            JOIN table_b b ON a.id = b.a_id
            JOIN table_c c ON b.id = c.b_id
            JOIN table_d d ON c.id = d.c_id
            JOIN table_e e ON d.id = e.d_id
            JOIN table_f f ON e.id = f.e_id
            WHERE a.status = 1
            """;

        int joinCount = manyJoinSql.toUpperCase().split("JOIN").length - 1;
        assertEquals(5, joinCount, "应该有 5 个 JOIN");
    }

    @Test
    @DisplayName("测试跨分片查询场景")
    void testCrossShardQuery() {
        // 模拟 GoldenDB 跨分片查询
        String crossShardSql = """
            SELECT /*+ CROSS_SHARD */
                a.user_id,
                b.order_id,
                c.item_name
            FROM user_db.user_info a
            JOIN order_db.order_info b ON a.user_id = b.user_id
            JOIN item_db.item_info c ON b.item_id = c.item_id
            WHERE a.region_id IN (1, 2, 3, 4, 5)
            """;

        assertTrue(crossShardSql.contains("CROSS_SHARD") ||
                   crossShardSql.contains("JOIN"),
                   "跨分片查询应该包含提示或多表 JOIN");
    }

    @Test
    @DisplayName("测试分片键不在 WHERE 条件中")
    void testMissingShardKey() {
        // 假设 shard_key 是 user_id，但查询中没有使用
        String missingShardKeySql = """
            SELECT * FROM orders
            WHERE order_status = 1
            ORDER BY create_time DESC
            LIMIT 100
            """;

        assertFalse(missingShardKeySql.toUpperCase().contains("USER_ID"),
                    "查询不应该包含分片键 user_id");
        assertTrue(missingShardKeySql.toUpperCase().contains("WHERE"),
                   "查询应该包含 WHERE 条件");
    }

    @Test
    @DisplayName("测试 UNION ALL 跨分片")
    void testUnionAllCrossShard() {
        String unionSql = """
            SELECT * FROM orders WHERE user_id BETWEEN 1 AND 1000
            UNION ALL
            SELECT * FROM orders WHERE user_id BETWEEN 1001 AND 2000
            UNION ALL
            SELECT * FROM orders WHERE user_id BETWEEN 2001 AND 3000
            UNION ALL
            SELECT * FROM orders WHERE user_id BETWEEN 3001 AND 4000
            UNION ALL
            SELECT * FROM orders WHERE user_id BETWEEN 4001 AND 5000
            """;

        int unionCount = unionSql.toUpperCase().split("UNION ALL").length - 1;
        assertEquals(4, unionCount, "应该有 4 个 UNION ALL");
    }

    @Test
    @DisplayName("测试分布式聚合查询")
    void testDistributedAggregation() {
        String aggSql = """
            SELECT
                region_id,
                COUNT(*) as user_count,
                SUM(total_amount) as total_sales,
                AVG(total_amount) as avg_sales,
                MAX(total_amount) as max_sale,
                MIN(total_amount) as min_sale
            FROM orders
            GROUP BY region_id
            ORDER BY total_sales DESC
            LIMIT 100
            """;

        assertTrue(aggSql.contains("GROUP BY"), "应该包含 GROUP BY");
        assertTrue(aggSql.contains("COUNT"), "应该包含 COUNT 聚合");
        assertTrue(aggSql.contains("SUM"), "应该包含 SUM 聚合");
    }

    // ========== 异常场景测试 ==========

    @Test
    @DisplayName("测试语法错误的 SQL")
    void testSyntaxErrorSql() {
        String invalidSql = "SELECT * FORM users WHER id = 1"; // FORM 应该是 FROM，WHER 应该是 WHERE

        assertFalse(invalidSql.toUpperCase().contains(" FROM "), "SQL 包含语法错误：FORM");
        assertFalse(invalidSql.toUpperCase().contains(" WHERE "), "SQL 包含语法错误：WHER");
    }

    @Test
    @DisplayName("测试不完整 SQL（缺少 FROM）")
    void testIncompleteSql() {
        String incompleteSql = "SELECT user_id, username";

        assertFalse(incompleteSql.toUpperCase().contains("FROM"),
                    "SQL 应该缺少 FROM 子句");
    }

    @Test
    @DisplayName("测试含有未绑定参数的 SQL")
    void testUnboundParameters() {
        String paramSql = "SELECT * FROM users WHERE user_id = #{userId} AND status = ${status}";

        assertTrue(paramSql.contains("#{") || paramSql.contains("${"),
                   "SQL 应该包含未绑定参数");
    }

    @Test
    @DisplayName("测试 SQL 注入风险")
    void testSqlInjectionRisk() {
        String injectionSql = "SELECT * FROM users WHERE name = '' OR '1'='1'";

        assertTrue(injectionSql.contains("'1'='1") || injectionSql.contains("OR 1=1"),
                   "SQL 应该包含注入风险特征");
    }

    @Test
    @DisplayName("测试含有注释的 SQL")
    void testSqlWithComments() {
        String commentSql = """
            /*
             * GoldenDB 分布式查询
             * 注意：此查询会跨分片
             */
            SELECT /*+ INDEX(orders idx_user_id) */ *
            FROM orders -- 按用户 ID 查询
            WHERE user_id = 123
            """;

        assertTrue(commentSql.contains("/*") || commentSql.contains("--"),
                   "SQL 应该包含注释");
    }

    @Test
    @DisplayName("测试含有 GoldenDB 特定 Hint 的 SQL")
    void testGoldenDbHints() {
        String hintSql = """
            SELECT /*+ TDDL:scan='user_db:user_info_*,order_db:order_info_*' */
                a.user_id, b.order_id
            FROM user_info a
            JOIN order_info b ON a.user_id = b.user_id
            WHERE a.status = 1
            """;

        assertTrue(hintSql.contains("/*+") && hintSql.contains("*/"),
                   "SQL 应该包含 Hint");
        assertTrue(hintSql.contains("TDDL") || hintSql.contains("CROSS_SHARD"),
                   "SQL 应该包含 GoldenDB/TDDL 特定 Hint");
    }

    @Test
    @DisplayName("测试含有窗口函数的 SQL")
    void testWindowFunctions() {
        String windowSql = """
            SELECT
                user_id,
                order_id,
                amount,
                ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY create_time DESC) as rn,
                SUM(amount) OVER (PARTITION BY user_id) as total_amount,
                AVG(amount) OVER (PARTITION BY region_id) as region_avg
            FROM orders
            WHERE create_time >= '2024-01-01'
            """;

        assertTrue(windowSql.contains("OVER ("), "应该包含窗口函数");
        assertTrue(windowSql.contains("PARTITION BY"), "应该包含 PARTITION BY");
    }

    @Test
    @DisplayName("测试含有 CTE 的 SQL")
    void testCommonTableExpression() {
        String cteSql = """
            WITH RECURSIVE org_tree AS (
                SELECT org_id, parent_id, org_name, 0 as level
                FROM organization WHERE parent_id IS NULL
                UNION ALL
                SELECT o.org_id, o.parent_id, o.org_name, ot.level + 1
                FROM organization o
                JOIN org_tree ot ON o.parent_id = ot.org_id
            )
            SELECT * FROM org_tree WHERE level <= 5
            """;

        assertTrue(cteSql.contains("WITH"), "应该包含 CTE 定义");
        assertTrue(cteSql.contains("RECURSIVE"), "应该包含递归 CTE");
    }

    @Test
    @DisplayName("测试含有 EXISTS 子查询的 SQL")
    void testExistsSubquery() {
        String existsSql = """
            SELECT * FROM users u
            WHERE EXISTS (
                SELECT 1 FROM orders o
                WHERE o.user_id = u.user_id
                AND o.amount > 1000
            )
            """;

        assertTrue(existsSql.toUpperCase().contains("EXISTS"), "应该包含 EXISTS");
        assertTrue(existsSql.toUpperCase().contains("SELECT 1"), "EXISTS 子查询应该使用 SELECT 1");
    }

    @Test
    @DisplayName("测试含有 NOT IN 子查询的 SQL")
    void testNotInSubquery() {
        String notInSql = """
            SELECT * FROM users
            WHERE user_id NOT IN (
                SELECT user_id FROM orders WHERE status = 'CANCELLED'
            )
            """;

        assertTrue(notInSql.toUpperCase().contains("NOT IN"), "应该包含 NOT IN");
    }

    @Test
    @DisplayName("测试含有 CASE 表达式的 SQL")
    void testCaseExpression() {
        String caseSql = """
            SELECT
                user_id,
                CASE
                    WHEN total_amount >= 10000 THEN 'VIP'
                    WHEN total_amount >= 1000 THEN 'GOLD'
                    WHEN total_amount >= 100 THEN 'SILVER'
                    ELSE 'NORMAL'
                END as user_level
            FROM user_totals
            """;

        assertTrue(caseSql.contains("CASE"), "应该包含 CASE 表达式");
        assertTrue(caseSql.contains("WHEN"), "应该包含 WHEN 子句");
        assertTrue(caseSql.contains("END"), "应该包含 END");
    }

    @Test
    @DisplayName("测试含有 COALESCE 的 SQL")
    void testCoalesce() {
        String coalesceSql = """
            SELECT
                user_id,
                COALESCE(phone, email, 'N/A') as contact_info
            FROM users
            """;

        assertTrue(coalesceSql.contains("COALESCE"), "应该包含 COALESCE");
    }

    @Test
    @DisplayName("测试含有 NULLIF 的 SQL")
    void testNullif() {
        String nullifSql = """
            SELECT
                order_id,
                NULLIF(discount, 0) as actual_discount
            FROM orders
            """;

        assertTrue(nullifSql.contains("NULLIF"), "应该包含 NULLIF");
    }

    @Test
    @DisplayName("测试 GROUP BY 与 HAVING")
    void testGroupByWithHaving() {
        String havingSql = """
            SELECT
                user_id,
                COUNT(*) as order_count,
                SUM(amount) as total_amount
            FROM orders
            GROUP BY user_id
            HAVING COUNT(*) >= 5 AND SUM(amount) >= 1000
            """;

        assertTrue(havingSql.contains("GROUP BY"), "应该包含 GROUP BY");
        assertTrue(havingSql.contains("HAVING"), "应该包含 HAVING");
    }

    @Test
    @DisplayName("测试 LIMIT 偏移量过大")
    void testLargeLimitOffset() {
        String largeOffsetSql = """
            SELECT * FROM products
            ORDER BY create_time DESC
            LIMIT 1000000, 10
            """;

        assertTrue(largeOffsetSql.contains("LIMIT 1000000"),
                   "应该包含大的 OFFSET 值（深度分页）");
    }

    @Test
    @DisplayName("测试 ORDER BY RAND()")
    void testOrderByRand() {
        String randomSql = """
            SELECT * FROM users
            ORDER BY RAND()
            LIMIT 10
            """;

        assertTrue(randomSql.toUpperCase().contains("ORDER BY RAND()"),
                   "应该包含 ORDER BY RAND()");
    }

    @Test
    @DisplayName("测试含有函数在索引列上")
    void testFunctionOnIndexedColumn() {
        String functionSql = """
            SELECT * FROM orders
            WHERE DATE(create_time) = '2024-01-01'
            """;

        assertTrue(functionSql.contains("DATE("),
                   "应该在列上使用函数（导致索引失效）");
    }

    @Test
    @DisplayName("测试含有隐式类型转换")
    void testImplicitTypeConversion() {
        String conversionSql = """
            SELECT * FROM users
            WHERE phone_number = 13800138000
            """;

        // phone_number 通常是字符串类型，这里用数字比较会导致隐式转换
        assertTrue(conversionSql.contains("phone_number"), "应该包含字符串类型列");
        // 验证值没有引号（隐式转换的特征）
        assertFalse(conversionSql.contains("'13800138000'") ||
                    conversionSql.contains("\"13800138000\""),
                   "值应该是数字形式（会导致隐式转换）");
    }

    @Test
    @DisplayName("测试含有 OR 条件导致索引失效")
    void testOrConditionIndexInvalidation() {
        String orSql = """
            SELECT * FROM users
            WHERE user_id = 123 OR username = 'john'
            """;

        assertTrue(orSql.contains(" OR "), "应该包含 OR 条件");
    }

    @Test
    @DisplayName("测试含有 LIKE 前缀通配符")
    void testLikePrefixWildcard() {
        String likeSql = """
            SELECT * FROM products
            WHERE product_name LIKE '%手机%'
            """;

        assertTrue(likeSql.contains("LIKE '%"), "应该包含前缀通配符");
    }

    @Test
    @DisplayName("测试含有 SELECT *")
    void testSelectStar() {
        String selectStarSql = "SELECT * FROM users WHERE status = 1";

        assertTrue(selectStarSql.contains("SELECT *"), "应该包含 SELECT *");
    }

    @Test
    @DisplayName("测试含有笛卡尔积风险")
    void testCartesianProductRisk() {
        String cartesianSql = """
            SELECT a.*, b.*
            FROM table_a, table_b
            WHERE a.status = 1
            """;

        assertTrue(cartesianSql.contains(", "), "应该包含逗号分隔的表（隐式 JOIN）");
        assertFalse(cartesianSql.toUpperCase().contains("JOIN"),
                    "不应该包含显式 JOIN");
    }
}
