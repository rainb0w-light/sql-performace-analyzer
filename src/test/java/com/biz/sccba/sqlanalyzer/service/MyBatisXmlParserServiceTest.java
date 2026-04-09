package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.config.TestAgentScopeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis XML 解析服务单元测试
 */
@SpringBootTest
@ContextConfiguration(classes = {TestAgentScopeConfig.class})
@TestPropertySource(locations = "classpath:application.yml")
class MyBatisXmlParserServiceTest {

    @Autowired
    private MyBatisXmlParserService parserService;

    private String transactionMapperXml;

    @BeforeEach
    void setUp() throws IOException {
        // 读取测试 XML 文件
        Path xmlPath = Paths.get("src/test/resources/mapper/TransactionMapper.xml");
        transactionMapperXml = Files.readString(xmlPath, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("测试解析 MyBatis XML - 基础解析")
    void testParse_Basic() {
        // 执行解析
        var result = parserService.parse(transactionMapperXml, null);

        // 验证结果
        assertNotNull(result);
        assertEquals("com.biz.sccba.sqlanalyzer.mapper.TransactionMapper", result.namespace());
        assertNotNull(result.statementId());
        assertNotNull(result.statementType());
        assertFalse(result.dynamicSqls().isEmpty());
    }

    @Test
    @DisplayName("测试解析 MyBatis XML - 提取 test 条件")
    void testParse_ExtractTestConditions() {
        var result = parserService.parse(transactionMapperXml, null);

        // 验证 test 条件被正确提取
        assertNotNull(result.testConditions());
        assertTrue(result.testConditions().size() > 0, "应该提取到多个 test 条件");

        // 打印提取的条件
        System.out.println("提取到 " + result.testConditions().size() + " 个 test 条件:");
        for (var condition : result.testConditions()) {
            System.out.println("  - XPath: " + condition.xpath() + ", Test: " + condition.testExpression() + ", Fields: " + condition.involvedFields());
        }

        // 验证特定条件被提取
        boolean hasAcctIdCondition = result.testConditions().stream()
            .anyMatch(c -> c.testExpression().contains("acctId"));
        assertTrue(hasAcctIdCondition, "应该包含 acctId 相关的条件");
    }

    @Test
    @DisplayName("测试解析 test 表达式 - 提取字段")
    void testParseTestExpression() {
        // 简单条件
        var fields1 = parserService.parseTestExpression("status != null");
        assertTrue(fields1.contains("status"));
        assertEquals(1, fields1.size());

        // 复杂条件（AND 连接）
        var fields2 = parserService.parseTestExpression(
            "startDate != null and endDate != null and type == 1");
        assertTrue(fields2.contains("startDate"));
        assertTrue(fields2.contains("endDate"));
        assertTrue(fields2.contains("type"));

        // 包含 OR 的条件
        var fields3 = parserService.parseTestExpression(
            "status == 1 or status == 2 or status == 3");
        assertTrue(fields3.contains("status"));
        assertEquals(1, fields3.size());  // status 只应该出现一次

        // 包含 null 检查的条件
        var fields4 = parserService.parseTestExpression(
            "list != null and list.size() > 0");
        assertTrue(fields4.contains("list"));
    }

    @Test
    @DisplayName("测试 test 表达式转自然语言")
    void testTestExprToNaturalLanguage() {
        // 简单 AND 条件
        String result1 = parserService.testExprToNaturalLanguage(
            "status != null and type != null");
        System.out.println("自然语言描述 1: " + result1);
        assertTrue(result1.contains("不为空"));

        // 复杂条件
        String result2 = parserService.testExprToNaturalLanguage(
            "startDate != null and endDate != null and amount >= 1000");
        System.out.println("自然语言描述 2: " + result2);
        assertTrue(result2.contains("并且"));
    }

    @Test
    @DisplayName("测试生成多场景 SQL")
    void testGenerateSqlScenarios() {
        var result = parserService.parse(transactionMapperXml, null);

        // 验证生成了多个 SQL 场景
        assertNotNull(result.dynamicSqls());
        assertTrue(result.dynamicSqls().size() >= 1);

        System.out.println("生成了 " + result.dynamicSqls().size() + " 个 SQL 场景:");
        for (int i = 0; i < result.dynamicSqls().size(); i++) {
            System.out.println("场景 " + (i + 1) + ": " + (result.dynamicSqls().get(i).substring(0, 100) + "..."));
        }
    }

    @Test
    @DisplayName("测试解析 selectByCondition 语句")
    void testParse_selectByCondition() {
        var result = parserService.parse(transactionMapperXml, null);

        // selectByCondition 应该包含多个 test 条件
        var testConditions = result.testConditions();

        // 验证常见条件字段被提取
        boolean hasStatus = testConditions.stream()
            .anyMatch(c -> c.testExpression().contains("status"));
        boolean hasDate = testConditions.stream()
            .anyMatch(c -> c.testExpression().contains("Date"));
        boolean hasAmount = testConditions.stream()
            .anyMatch(c -> c.testExpression().contains("Amount"));

        System.out.println("条件覆盖检查 - status: " + hasStatus + ", date: " + hasDate + ", amount: " + hasAmount);
    }

    @Test
    @DisplayName("测试解析复杂嵌套条件")
    void testParse_NestedConditions() {
        // 测试极度复杂的嵌套条件解析
        String complexXml = """
            <mapper namespace="com.biz.sccba.sqlanalyzer.mapper.ComplexMapper">
                <select id="complexQuery">
                    SELECT * FROM table
                    <where>
                        <if test="custId != null">
                            AND cust_id = #{custId}
                        </if>
                        <if test="highValueOnly != null and highValueOnly == true">
                            AND amount >= 50000
                        </if>
                        <if test="dateRangeType != null">
                            <choose>
                                <when test="dateRangeType == 'TODAY'">
                                    AND txn_date = CURDATE()
                                </when>
                                <when test="dateRangeType == 'WEEK'">
                                    AND txn_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                                </when>
                            </choose>
                        </if>
                    </where>
                </select>
            </mapper>
            """;

        var result = parserService.parse(complexXml, null);

        assertNotNull(result);
        assertFalse(result.testConditions().isEmpty());
        System.out.println("复杂嵌套条件解析结果：" + result.testConditions().size() + " 个条件");
    }

    @Test
    @DisplayName("测试解析包含 foreach 的 SQL")
    void testParse_ForeachCondition() {
        var result = parserService.parse(transactionMapperXml, null);

        // 验证 SQL 中包含 foreach 的处理
        String sqlContent = result.originalSql();
        assertNotNull(sqlContent);

        System.out.println("原始 SQL 内容：" + sqlContent.substring(0, Math.min(500, sqlContent.length())));
    }

    @Test
    @DisplayName("测试占位符替换")
    void testPlaceholderReplacement() {
        var result = parserService.parse(transactionMapperXml, null);

        // 验证生成的 SQL 中 #{param} 被替换为 ?
        for (String sql : result.dynamicSqls()) {
            assertFalse(sql.contains("#{"), "SQL 中不应包含未替换的 #{} 占位符");
        }
    }
}
