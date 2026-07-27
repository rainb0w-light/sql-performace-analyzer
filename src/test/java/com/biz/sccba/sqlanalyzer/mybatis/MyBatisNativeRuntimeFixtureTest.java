package com.biz.sccba.sqlanalyzer.mybatis;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MyBatis official-tag fixture matrix (development-guide §13): every assertion below comes from
 * {@code XMLMapperBuilder -> MappedStatement.getBoundSql}, never from hand-built SQL.
 */
class MyBatisNativeRuntimeFixtureTest {

    private static final String DOCTYPE =
            "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">";

    private static final String MAPPER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
            <mapper namespace="com.example.UserMapper">
              <sql id="baseCols">id, name, status</sql>

              <select id="staticSelect" resultType="map">
                SELECT <include refid="baseCols"/> FROM users
              </select>

              <select id="findByCondition" resultType="map">
                SELECT <include refid="baseCols"/> FROM users
                <where>
                  <if test="status != null">AND status = #{status}</if>
                  <if test="name != null and name != ''">AND name LIKE #{name}</if>
                  <if test="user != null and user.age != null">AND age &gt; #{user.age}</if>
                </where>
              </select>

              <select id="chooseOne" resultType="map">
                SELECT id FROM users
                <where>
                  <choose>
                    <when test="byEmail != null">email = #{byEmail}</when>
                    <when test="byPhone != null">phone = #{byPhone}</when>
                    <otherwise>1 = 1</otherwise>
                  </choose>
                </where>
              </select>

              <select id="inList" resultType="map">
                SELECT id FROM users WHERE id IN
                <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
              </select>

              <update id="updateUser">
                UPDATE users
                <set>
                  <if test="name != null">name = #{name},</if>
                  <if test="status != null">status = #{status},</if>
                </set>
                WHERE id = #{id}
              </update>

              <select id="trimmed" resultType="map">
                SELECT id FROM users
                <trim prefix="WHERE" prefixOverrides="AND |OR ">
                  <if test="status != null">AND status = #{status}</if>
                </trim>
              </select>

              <select id="bound" resultType="map">
                <bind name="pattern" value="'%' + name + '%'"/>
                SELECT id FROM users WHERE name LIKE #{pattern}
              </select>

              <select id="dbSpecific" databaseId="mysql" resultType="map">SELECT NOW()</select>
              <select id="dbSpecific" databaseId="pg" resultType="map">SELECT CURRENT_TIMESTAMP</select>

              <select id="interpolated" resultType="map">
                SELECT id FROM users ORDER BY ${orderColumn}
              </select>
            </mapper>
            """;

    private final MyBatisStatementRuntime runtime = new MyBatisStatementRuntime(null, "mysql");
    private final Configuration configuration = runtime.loadConfiguration(bytes(MAPPER), "UserMapper.xml");

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String norm(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    @Test
    void loadsAllStatementsOfMapper() {
        var loaded = runtime.load(bytes(MAPPER), "UserMapper.xml");
        assertEquals("com.example.UserMapper", loaded.namespace());
        assertTrue(loaded.statementIds().contains("com.example.UserMapper.staticSelect"));
        assertTrue(loaded.statementIds().contains("com.example.UserMapper.findByCondition"));
        assertTrue(loaded.statementIds().contains("com.example.UserMapper.updateUser"));
    }

    @Test
    void staticSqlWithInclude() {
        var result = runtime.bind(configuration, "com.example.UserMapper.staticSelect", Map.of());
        assertFalse(result.isUnsupported(), result.unsupported());
        assertEquals("SELECT id, name, status FROM users", norm(result.sql()));
        assertTrue(result.parameterMappings().isEmpty());
    }

    @Test
    void multipleIfsWithNestedOgnl() {
        var all = runtime.bind(configuration, "com.example.UserMapper.findByCondition",
                Map.of("status", "PAID", "name", "jo", "user", Map.of("age", 30)));
        assertFalse(all.isUnsupported(), all.unsupported());
        String sql = norm(all.sql());
        assertTrue(sql.contains("status = ?"), sql);
        assertTrue(sql.contains("name LIKE ?"), sql);
        assertTrue(sql.contains("age > ?"), sql);
        assertTrue(all.parameterMappings().stream().anyMatch(m -> m.property().equals("user.age")),
                "nested OGNL property must surface in parameter mappings");

        var partial = runtime.bind(configuration, "com.example.UserMapper.findByCondition", Map.of("status", "PAID"));
        String partialSql = norm(partial.sql());
        assertTrue(partialSql.contains("status = ?"));
        assertFalse(partialSql.contains("LIKE"), partialSql);
        assertFalse(partialSql.contains("age >"), partialSql);

        var none = runtime.bind(configuration, "com.example.UserMapper.findByCondition", java.util.Collections.emptyMap());
        String noneSql = norm(none.sql());
        assertFalse(noneSql.toUpperCase().contains("WHERE"), "empty <where> must collapse: " + noneSql);
    }

    @Test
    void chooseWhenOtherwise() {
        var byEmail = runtime.bind(configuration, "com.example.UserMapper.chooseOne", Map.of("byEmail", "a@b.c"));
        assertTrue(norm(byEmail.sql()).contains("email = ?"));

        var byPhone = runtime.bind(configuration, "com.example.UserMapper.chooseOne", Map.of("byPhone", "123"));
        assertTrue(norm(byPhone.sql()).contains("phone = ?"));

        var otherwise = runtime.bind(configuration, "com.example.UserMapper.chooseOne", java.util.Collections.emptyMap());
        assertTrue(norm(otherwise.sql()).contains("1 = 1"));
    }

    @Test
    void foreachEmptySingleMulti() {
        var multi = runtime.bind(configuration, "com.example.UserMapper.inList", Map.of("ids", List.of(1, 2, 3)));
        assertFalse(multi.isUnsupported(), multi.unsupported());
        String sql = norm(multi.sql());
        assertTrue(sql.contains("IN ( ? , ? , ? )") || sql.contains("IN (? , ? , ?)") || sql.contains("IN ( ? , ? , ? )"),
                "foreach multi: " + sql);
        assertTrue(multi.parameterMappings().stream().anyMatch(m -> m.property().startsWith("__frch_")),
                "foreach item mappings must be exposed");
        assertEquals(3, multi.additionalParameters().size(), "foreach additional parameters must be recorded");

        var single = runtime.bind(configuration, "com.example.UserMapper.inList", Map.of("ids", List.of(1)));
        assertTrue(norm(single.sql()).contains("( ? )"), norm(single.sql()));

        var empty = runtime.bind(configuration, "com.example.UserMapper.inList", Map.of("ids", List.of()));
        assertFalse(empty.isUnsupported(), String.valueOf(empty.unsupported()));
        // MyBatis emits no parentheses at all for an empty collection — a sharp edge the report
        // must surface as a boundary risk, documented here by pinning the exact official output.
        String emptySql = norm(empty.sql()).replaceAll("\\s+", "");
        assertTrue(emptySql.endsWith("IN"), "empty foreach yields a dangling IN: " + emptySql);
    }

    @Test
    void setAndTrimTags() {
        var update = runtime.bind(configuration, "com.example.UserMapper.updateUser", Map.of("name", "n", "id", 7));
        String sql = norm(update.sql());
        assertTrue(sql.startsWith("UPDATE users SET name = ?"), sql);
        assertFalse(sql.contains("? ,"), "trailing comma must be removed: " + sql);
        assertTrue(sql.endsWith("WHERE id = ?"), sql);

        var trimmed = runtime.bind(configuration, "com.example.UserMapper.trimmed", Map.of("status", "PAID"));
        String trimmedSql = norm(trimmed.sql());
        assertTrue(trimmedSql.contains("WHERE status = ?"), trimmedSql);
        assertFalse(trimmedSql.contains("WHERE AND"), trimmedSql);
    }

    @Test
    void bindTagProducesAdditionalParameter() {
        var result = runtime.bind(configuration, "com.example.UserMapper.bound", Map.of("name", "jo"));
        assertFalse(result.isUnsupported(), result.unsupported());
        assertTrue(norm(result.sql()).contains("LIKE ?"));
        assertTrue(result.parameterMappings().stream().anyMatch(m -> m.property().equals("pattern")));
    }

    @Test
    void databaseIdSelectsVariant() {
        var mysql = runtime.bind(configuration, "com.example.UserMapper.dbSpecific", Map.of());
        assertEquals("SELECT NOW()", norm(mysql.sql()));

        var pgRuntime = new MyBatisStatementRuntime(null, "pg");
        var pgConfig = pgRuntime.loadConfiguration(bytes(MAPPER), "UserMapper.xml");
        var pg = pgRuntime.bind(pgConfig, "com.example.UserMapper.dbSpecific", Map.of());
        assertEquals("SELECT CURRENT_TIMESTAMP", norm(pg.sql()));
    }

    @Test
    void dollarInterpolationGoesThroughOfficialPath() {
        var result = runtime.bind(configuration, "com.example.UserMapper.interpolated", Map.of("orderColumn", "name"));
        assertFalse(result.isUnsupported(), result.unsupported());
        assertTrue(norm(result.sql()).contains("ORDER BY name"),
                "${} must be interpolated by MyBatis itself");
    }

    @Test
    void missingStatementIsUnsupportedNotAnError() {
        var result = runtime.bind(configuration, "com.example.UserMapper.doesNotExist", Map.of());
        assertTrue(result.isUnsupported());
        assertTrue(result.unsupported().startsWith("UNSUPPORTED"));
    }

    @Test
    void invalidXmlFailsToLoad() {
        assertThrows(MyBatisStatementRuntime.MapperLoadException.class,
                () -> runtime.loadConfiguration(bytes("<mapper><select id='x'></mapper"), "bad.xml"));
    }

    @Test
    void missingIncludeSurfacesAsUnsupportedAtBind() {
        // MyBatis queues statements with unresolvable <include> as incomplete instead of failing
        // the whole mapper; the runtime must surface them as UNSUPPORTED, never as broken SQL.
        String bad = DOCTYPE + "<mapper namespace='x'><select id='s' resultType='map'>SELECT <include refid='ghost'/></select></mapper>";
        Configuration config = runtime.loadConfiguration(bytes(bad), "ghost.xml");
        var result = runtime.bind(config, "x.s", Map.of());
        assertTrue(result.isUnsupported(), "unresolvable include must be UNSUPPORTED");
    }

    @Test
    void unknownProjectParameterTypeFailsToLoad() {
        String bad = DOCTYPE + "<mapper namespace='x'><select id='s' parameterType='com.example.DoesNotExist' resultType='map'>SELECT 1</select></mapper>";
        assertThrows(MyBatisStatementRuntime.MapperLoadException.class,
                () -> runtime.loadConfiguration(bytes(bad), "unknown-type.xml"));
    }

    @Test
    void rawLanguageDriverWithDynamicNodesIsUnsupported() {
        String raw = DOCTYPE + """
                <mapper namespace='x'>
                  <select id='s' resultType='map' lang='org.apache.ibatis.scripting.defaults.RawLanguageDriver'>
                    SELECT id FROM users <if test="status != null">WHERE status = #{status}</if>
                  </select>
                </mapper>
                """;
        // RawLanguageDriver rejects dynamic content at load — surfaced as UNSUPPORTED, never silent.
        assertThrows(MyBatisStatementRuntime.MapperLoadException.class,
                () -> runtime.loadConfiguration(bytes(raw), "raw.xml"));
    }

    @Test
    void sqlAndParameterListStaySeparated() {
        var result = runtime.bind(configuration, "com.example.UserMapper.findByCondition", Map.of("status", "PAID'; DROP TABLE users;--"));
        assertNotNull(result.sql());
        assertFalse(result.sql().contains("DROP TABLE"),
                "#{} must remain a placeholder; values never inline into SQL");
        assertTrue(result.sql().contains("?"));
    }
}
