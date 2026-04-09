package com.biz.sccba.sqlanalyzer.tool;

import java.util.*;
import java.util.stream.IntStream;

/**
 * SQL 分析测试数据生成器
 */
public class SqlTestDataGenerator {

    private final Random random;

    public SqlTestDataGenerator() {
        this.random = new Random();
    }

    public SqlTestDataGenerator(long seed) {
        this.random = new Random(seed);
    }

    public String generateSimpleSelect() {
        String[] columns = {"id", "name", "email", "status", "created_at", "updated_at"};
        int columnCount = 1 + random.nextInt(4);
        
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < columnCount; i++) {
            if (i > 0) sql.append(", ");
            sql.append(columns[random.nextInt(columns.length)]);
        }
        sql.append(" FROM users");
        sql.append(" WHERE status = ").append(random.nextInt(2));
        
        return sql.toString();
    }

    public String generateJoinQuery() {
        int joinCount = 1 + random.nextInt(3);
        
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append("a.id, a.name, b.data, c.status");
        sql.append(" FROM table_a a");
        
        for (int i = 0; i < joinCount; i++) {
            String tableName = switch (i) {
                case 0 -> "table_b b ON a.id = b.a_id";
                case 1 -> "table_c c ON b.id = c.b_id";
                case 2 -> "table_d d ON c.id = d.c_id";
                default -> "table_" + (char)('b' + i) + " " + (char)('b' + i) + " ON a.id = " + (char)('b' + i) + ".a_id";
            };
            sql.append(" JOIN ").append(tableName);
        }
        
        sql.append(" WHERE a.status = 1");
        
        if (random.nextBoolean()) {
            sql.append(" AND b.created_at > '2024-01-01'");
        }
        
        return sql.toString();
    }

    public String generateSubqueryQuery() {
        int depth = 1 + random.nextInt(2);
        
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE id IN (");
        
        for (int i = 0; i < depth; i++) {
            if (i > 0) {
                sql.append("SELECT user_id FROM order_").append(i);
                sql.append(" WHERE order_id IN (");
            } else {
                sql.append("SELECT user_id FROM orders");
            }
        }
        
        sql.append(" WHERE amount > ").append(100 + random.nextInt(900));
        
        for (int i = 0; i < depth; i++) {
            sql.append(")");
        }
        
        return sql.toString();
    }

    public String generateAggregationQuery() {
        StringBuilder sql = new StringBuilder("SELECT ");
        
        String[] groupByColumns = {"category", "status", "user_id", "DATE(created_at)"};
        int groupByCount = 1 + random.nextInt(2);
        
        for (int i = 0; i < groupByCount; i++) {
            if (i > 0) sql.append(", ");
            sql.append(groupByColumns[i]);
        }
        
        sql.append(", COUNT(*) as cnt");
        sql.append(", SUM(amount) as total");
        sql.append(", AVG(price) as avg_price");
        
        sql.append(" FROM orders");
        sql.append(" WHERE created_at >= '2024-01-01'");
        sql.append(" GROUP BY ");
        
        for (int i = 0; i < groupByCount; i++) {
            if (i > 0) sql.append(", ");
            sql.append(groupByColumns[i]);
        }
        
        if (random.nextBoolean()) {
            sql.append(" HAVING COUNT(*) > ").append(10 + random.nextInt(40));
        }
        
        sql.append(" ORDER BY total DESC");
        sql.append(" LIMIT ").append(10 + random.nextInt(90));
        
        return sql.toString();
    }

    public String generateDeepPaginationQuery() {
        int offset = 1000 + random.nextInt(9) * 1000;
        int limit = 10 + random.nextInt(10);
        
        return "SELECT * FROM large_table ORDER BY created_at DESC LIMIT " + offset + ", " + limit;
    }

    public String generateComplexQuery() {
        return """
            SELECT 
                u.id,
                u.name,
                COUNT(DISTINCT o.id) as order_count,
                SUM(o.amount) as total_amount,
                AVG(o.amount) as avg_amount,
                MAX(o.created_at) as last_order_date
            FROM users u
            LEFT JOIN orders o ON u.id = o.user_id
            LEFT JOIN order_items oi ON o.id = oi.order_id
            LEFT JOIN products p ON oi.product_id = p.id
            WHERE u.status = 1
                AND o.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)
                AND (p.category = 'Electronics' OR p.category IS NULL)
            GROUP BY u.id, u.name
            HAVING COUNT(DISTINCT o.id) >= 2
            ORDER BY total_amount DESC
            LIMIT 50
            """;
    }

    public String generateLikeQuery() {
        String[] patterns = {"%john%", "admin%", "%@example.com", "%2024%"};
        String pattern = patterns[random.nextInt(patterns.length)];
        
        return "SELECT * FROM users WHERE name LIKE '" + pattern + "'";
    }

    public String generateOrUpdateQuery() {
        if (random.nextBoolean()) {
            int id = 1 + random.nextInt(1000);
            return "UPDATE users SET name = 'test', updated_at = NOW() WHERE id = " + id;
        } else {
            int id = 1 + random.nextInt(1000);
            return "DELETE FROM temp_data WHERE created_at < DATE_SUB(NOW(), INTERVAL 7 DAY) AND id = " + id;
        }
    }

    public String generateUnionQuery() {
        return """
            SELECT id, name, 'user' as type FROM users WHERE status = 1
            UNION ALL
            SELECT id, name, 'customer' as type FROM customers WHERE active = 1
            UNION ALL
            SELECT id, name, 'vendor' as type FROM vendors WHERE active = 1
            ORDER BY name
            LIMIT 100
            """;
    }

    public String generateCTEQuery() {
        return """
            WITH RECURSIVE org_tree AS (
                SELECT id, name, parent_id, 0 as level
                FROM organizations
                WHERE parent_id IS NULL
                UNION ALL
                SELECT o.id, o.name, o.parent_id, ot.level + 1
                FROM organizations o
                INNER JOIN org_tree ot ON o.parent_id = ot.id
            )
            SELECT * FROM org_tree WHERE level <= 5
            ORDER BY level, name
            """;
    }

    public List<String> generateTestDataSqls(int count) {
        List<String> sqls = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            double rand = random.nextDouble();
            String sql = switch ((int) (rand * 10)) {
                case 0, 1, 2 -> generateSimpleSelect();
                case 3, 4 -> generateJoinQuery();
                case 5 -> generateSubqueryQuery();
                case 6 -> generateAggregationQuery();
                case 7 -> generateDeepPaginationQuery();
                case 8 -> generateLikeQuery();
                case 9 -> generateOrUpdateQuery();
                default -> generateSimpleSelect();
            };
            sqls.add(sql);
        }
        
        return sqls;
    }

    public Map<String, Object> generateSqlMetadata(String sql) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sql", sql);
        metadata.put("length", sql.length());
        metadata.put("hasWhere", sql.toUpperCase().contains("WHERE"));
        metadata.put("hasJoin", sql.toUpperCase().contains("JOIN"));
        metadata.put("hasSubquery", sql.toUpperCase().matches("(?i).*\\bSELECT\\b.*\\bSELECT\\b.*"));
        metadata.put("hasGroupBy", sql.toUpperCase().contains("GROUP BY"));
        metadata.put("hasOrderBy", sql.toUpperCase().contains("ORDER BY"));
        metadata.put("hasLimit", sql.toUpperCase().contains("LIMIT"));
        metadata.put("hasAggregation", sql.toUpperCase().matches("(?i).*\\b(COUNT|SUM|AVG|MIN|MAX)\\b.*"));
        
        return metadata;
    }

    public static class TestSqlMetadata {
        private final String sql;
        private final String category;
        private final int expectedComplexity;
        private final List<String> expectedIssues;

        public TestSqlMetadata(String sql, String category, int expectedComplexity, List<String> expectedIssues) {
            this.sql = sql;
            this.category = category;
            this.expectedComplexity = expectedComplexity;
            this.expectedIssues = expectedIssues != null ? expectedIssues : List.of();
        }

        public String getSql() {
            return sql;
        }

        public String getCategory() {
            return category;
        }

        public int getExpectedComplexity() {
            return expectedComplexity;
        }

        public List<String> getExpectedIssues() {
            return expectedIssues;
        }
    }

    public List<TestSqlMetadata> generateTestSuite() {
        return List.of(
            new TestSqlMetadata(
                "SELECT * FROM users WHERE id = 1",
                "SIMPLE",
                5,
                List.of()
            ),
            new TestSqlMetadata(
                "SELECT * FROM orders WHERE created_at LIKE '%2024%'",
                "LIKE_QUERY",
                15,
                List.of("Leading wildcard prevents index usage")
            ),
            new TestSqlMetadata(
                generateDeepPaginationQuery(),
                "DEEP_PAGINATION",
                20,
                List.of("Deep pagination performance issue")
            ),
            new TestSqlMetadata(
                generateJoinQuery(),
                "JOIN_QUERY",
                25,
                List.of()
            ),
            new TestSqlMetadata(
                generateComplexQuery(),
                "COMPLEX",
                50,
                List.of("Multiple joins", "Aggregation with grouping")
            )
        );
    }
}
