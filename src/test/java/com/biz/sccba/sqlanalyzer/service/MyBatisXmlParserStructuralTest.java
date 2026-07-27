package com.biz.sccba.sqlanalyzer.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural parsing only (statement inventory + test-condition catalog). Scenario SQL
 * generation was removed in Phase 4 — final SQL comes exclusively from the official
 * MyBatis runtime (see MyBatisNativeRuntimeFixtureTest / ScenarioEngineFixtureTest).
 */
class MyBatisXmlParserStructuralTest {

    private static final String MAPPER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.UserMapper">
              <select id="findByCondition" resultType="map">
                SELECT id, name FROM users
                <where>
                  <if test="status != null">AND status = #{status}</if>
                  <if test="name != null and name != ''">AND name LIKE #{name}</if>
                </where>
              </select>
              <update id="updateStatus">UPDATE users SET status = #{status} WHERE id = #{id}</update>
            </mapper>
            """;

    private final MyBatisXmlParserService parser = new MyBatisXmlParserService();

    @Test
    void parseMapperResolvesNamespaceAndStatements() {
        var result = parser.parseMapper(MAPPER_XML, null);
        assertEquals("com.example.UserMapper", result.namespace());
        assertEquals(2, result.statements().size());
        assertEquals("findByCondition", result.statements().get(0).statementId());
        assertEquals("SELECT", result.statements().get(0).statementType());
        assertEquals("updateStatus", result.statements().get(1).statementId());
        assertEquals("UPDATE", result.statements().get(1).statementType());
    }

    @Test
    void explicitNamespaceOverridesMapperAttribute() {
        var result = parser.parseMapper(MAPPER_XML, "override.Namespace");
        assertEquals("override.Namespace", result.namespace());
    }

    @Test
    void testConditionsAreExtractedAsCatalogOnly() {
        var result = parser.parseMapper(MAPPER_XML, null);
        List<MyBatisXmlParserService.TestCondition> conditions = result.statements().get(0).testConditions();
        assertEquals(2, conditions.size());
        assertEquals("status != null", conditions.get(0).testExpression());
        assertEquals("name != null and name != ''", conditions.get(1).testExpression());
        assertTrue(conditions.get(0).involvedFields().contains("status"));
    }

    @Test
    void parseTestExpressionExtractsLeftHandFields() {
        assertEquals(List.of("status", "type"),
                parser.parseTestExpression("status != null and type == 1"));
    }

    @Test
    void originalSqlIsRawTextWithoutGeneratedScenarios() {
        var result = parser.parseMapper(MAPPER_XML, null);
        String raw = result.statements().get(0).originalSql();
        assertTrue(raw.contains("#{status}"), "raw dynamic text keeps placeholders: " + raw);
        assertTrue(raw.contains("#{name}"), raw);
    }

    @Test
    void invalidXmlIsRejected() {
        assertThrows(RuntimeException.class, () -> parser.parseMapper("<not-a-mapper/>", null));
    }
}
