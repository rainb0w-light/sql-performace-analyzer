package com.biz.sccba.sqlanalyzer.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlAnalysisToolsValidationTest {

    private SqlTestDataGenerator testDataGenerator;
    private SqlAnalysisValidator validator;

    @BeforeEach
    void setUp() {
        testDataGenerator = new SqlTestDataGenerator(42);
        validator = new SqlAnalysisValidator();
    }

    @Test
    @DisplayName("测试生成简单 SELECT 查询")
    void testGenerateSimpleSelect() {
        String sql = testDataGenerator.generateSimpleSelect();
        
        assertNotNull(sql);
        assertTrue(sql.toUpperCase().contains("SELECT"));
        assertTrue(sql.toUpperCase().contains("FROM"));
        assertTrue(sql.toUpperCase().contains("WHERE"));
    }

    @Test
    @DisplayName("测试生成 JOIN 查询")
    void testGenerateJoinQuery() {
        String sql = testDataGenerator.generateJoinQuery();
        
        assertNotNull(sql);
        assertTrue(sql.toUpperCase().contains("JOIN"));
        assertTrue(sql.toUpperCase().contains("ON"));
    }

    @Test
    @DisplayName("测试生成子查询")
    void testGenerateSubqueryQuery() {
        String sql = testDataGenerator.generateSubqueryQuery();
        
        assertNotNull(sql);
        assertTrue(sql.toUpperCase().contains("SELECT"), "Should contain SELECT");
        int selectCount = sql.toUpperCase().split("SELECT").length - 1;
        assertTrue(selectCount >= 2, "Should have at least 2 SELECT statements");
    }

    @Test
    @DisplayName("测试生成分组聚合查询")
    void testGenerateAggregationQuery() {
        String sql = testDataGenerator.generateAggregationQuery();
        
        assertNotNull(sql);
        assertTrue(sql.toUpperCase().contains("GROUP BY"));
        assertTrue(sql.toUpperCase().matches(".*\\b(COUNT|SUM|AVG)\\b.*"));
    }

    @Test
    @DisplayName("测试生成深度分页查询")
    void testGenerateDeepPaginationQuery() {
        String sql = testDataGenerator.generateDeepPaginationQuery();
        
        assertNotNull(sql);
        assertTrue(sql.contains("LIMIT"));
        assertTrue(sql.contains("ORDER BY"));
        assertTrue(sql.contains("DESC"));
    }

    @Test
    @DisplayName("测试生成复杂查询")
    void testGenerateComplexQuery() {
        String sql = testDataGenerator.generateComplexQuery();
        
        assertNotNull(sql);
        assertTrue(sql.toUpperCase().contains("JOIN"));
        assertTrue(sql.toUpperCase().contains("GROUP BY"));
        assertTrue(sql.toUpperCase().contains("HAVING"));
        assertTrue(sql.toUpperCase().contains("ORDER BY"));
    }

    @Test
    @DisplayName("测试生成 LIKE 查询")
    void testGenerateLikeQuery() {
        String sql = testDataGenerator.generateLikeQuery();
        
        assertNotNull(sql);
        assertTrue(sql.toUpperCase().contains("LIKE"));
        assertTrue(sql.contains("%"));
    }

    @Test
    @DisplayName("测试生成测试数据批")
    void testGenerateTestDataSqls() {
        List<String> sqls = testDataGenerator.generateTestDataSqls(10);
        
        assertEquals(10, sqls.size());
        assertTrue(sqls.stream().allMatch(s -> s != null && !s.trim().isEmpty()));
    }

    @Test
    @DisplayName("测试生成 SQL 元数据")
    void testGenerateSqlMetadata() {
        String sql = "SELECT * FROM users WHERE id = 1";
        Map<String, Object> metadata = testDataGenerator.generateSqlMetadata(sql);
        
        assertNotNull(metadata);
        assertEquals(sql, metadata.get("sql"));
        assertTrue((Boolean) metadata.get("hasWhere"));
        assertFalse((Boolean) metadata.get("hasJoin"));
    }

    @Test
    @DisplayName("测试验证复杂度分析结果")
    void testValidateComplexityAnalysis() {
        String sql = testDataGenerator.generateComplexQuery();
        
        SqlAnalysisValidator.ValidationResult result = 
            validator.validateComplexityAnalysis(sql, "mock_result");
        
        assertNotNull(result);
        assertEquals("Complexity Analysis Validation", result.getTestName());
    }

    @Test
    @DisplayName("测试验证空 SQL 处理")
    void testValidateEmptySql() {
        SqlAnalysisValidator.ValidationResult result = 
            validator.validateComplexityAnalysis("", "mock_result");
        
        assertNotNull(result);
        assertTrue(result.isAllPassed() || result.getFailures().size() > 0);
    }

    @Test
    @DisplayName("测试验证索引使用分析结果")
    void testValidateIndexUsageAnalysis() {
        String sql = "SELECT * FROM users WHERE id = 1";
        
        SqlAnalysisValidator.ValidationResult result = 
            validator.validateIndexUsageAnalysis(sql, "users", "mock_result");
        
        assertNotNull(result);
        assertEquals("Index Usage Analysis Validation", result.getTestName());
    }

    @Test
    @DisplayName("测试验证测试元数据")
    void testValidateTestMetadata() {
        SqlTestDataGenerator.TestSqlMetadata metadata = new SqlTestDataGenerator.TestSqlMetadata(
            "SELECT * FROM users",
            "SIMPLE",
            5,
            List.of()
        );
        
        SqlAnalysisValidator.ValidationResult result = 
            validator.validateSqlTestMetadata(metadata);
        
        assertNotNull(result);
        assertTrue(result.getPasses().size() > 0);
    }

    @Test
    @DisplayName("测试验证多组分析结果")
    void testValidateMultipleAnalyses() {
        List<String> sqls = testDataGenerator.generateTestDataSqls(5);
        List<Object> results = sqls.stream().map(s -> (Object) "mock_result").toList();
        
        List<SqlAnalysisValidator.ValidationResult> validationResults = 
            validator.validateMultipleAnalyses(sqls, results);
        
        assertEquals(5, validationResults.size());
        assertTrue(validationResults.stream().allMatch(r -> r != null));
    }

    @Test
    @DisplayName("测试验证性能指标")
    void testValidatePerformanceMetrics() {
        Map<String, Number> metrics = Map.of(
            "executionTimeMs", 150,
            "rowsExamined", 1000,
            "rowsReturned", 100
        );
        
        SqlAnalysisValidator.ValidationResult result = 
            validator.validatePerformanceMetrics(metrics);
        
        assertNotNull(result);
        assertTrue(result.getPasses().size() > 0);
    }

    @Test
    @DisplayName("测试验证结果 toString 方法")
    void testValidationResultToString() {
        SqlAnalysisValidator.ValidationResult result = new SqlAnalysisValidator.ValidationResult();
        result.setTestName("Test");
        result.setAllPassed(true);
        result.addPass("Test 1 passed");
        result.addFailure("Test 2 failed");
        
        String str = result.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("Test"));
        assertTrue(str.contains("passed"));
    }

    @Test
    @DisplayName("测试验证结果添加失败")
    void testValidationResultAddFailure() {
        SqlAnalysisValidator.ValidationResult result = new SqlAnalysisValidator.ValidationResult();
        result.setAllPassed(true);
        
        result.addFailure("Failure message");
        
        assertFalse(result.isAllPassed());
        assertEquals(1, result.getFailures().size());
    }

    @Test
    @DisplayName("测试验证结果添加警告")
    void testValidationResultAddWarning() {
        SqlAnalysisValidator.ValidationResult result = new SqlAnalysisValidator.ValidationResult();
        
        result.addWarning("Warning message");
        
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.isAllPassed());
    }

    @Test
    @DisplayName("测试验证套件运行")
    void testRunValidationSuite() {
        SqlTestDataGenerator.TestSqlMetadata metadata1 = new SqlTestDataGenerator.TestSqlMetadata(
            "SELECT * FROM users",
            "SIMPLE",
            5,
            List.of()
        );
        
        SqlTestDataGenerator.TestSqlMetadata metadata2 = new SqlTestDataGenerator.TestSqlMetadata(
            "SELECT * FROM orders WHERE amount > 100",
            "FILTERED",
            10,
            List.of("Consider adding index")
        );
        
        SqlAnalysisValidator.ValidationResult suiteResult = 
            validator.runValidationSuite(metadata1, metadata2);
        
        assertNotNull(suiteResult);
        assertNotNull(suiteResult.getSummary());
    }
}
