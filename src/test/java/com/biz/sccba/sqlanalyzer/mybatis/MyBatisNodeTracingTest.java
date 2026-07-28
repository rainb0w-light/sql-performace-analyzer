package com.biz.sccba.sqlanalyzer.mybatis;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyBatisNodeTracingTest {

    @Test
    void recordsOnlyNodesThatActuallyAppendSql() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE mapper
                  PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
                  "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="demo.Mapper">
                  <select id="find">
                    select * from loan
                    <where>
                      <if test="status != null">status = #{status}</if>
                      <choose>
                        <when test="memberId != null">and member_id = #{memberId}</when>
                        <otherwise>and archived = false</otherwise>
                      </choose>
                      <foreach collection="ids" item="id" open="and id in (" close=")"
                               separator=",">#{id}</foreach>
                    </where>
                  </select>
                </mapper>
                """;
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        DynamicNodeCatalog catalog = new DynamicNodeCatalog();
        var statement = catalog.scan(xml).statements().get(0);
        MyBatisStatementRuntime runtime = new MyBatisStatementRuntime(null, null);
        var configuration = runtime.loadConfiguration(bytes, "trace-test");
        var hits = MyBatisNodeTracing.instrument(
                configuration, "demo.Mapper.find", statement.nodes());

        var bound = runtime.bind(configuration, "demo.Mapper.find",
                Map.of("status", "ACTIVE", "memberId", 7L, "ids", List.of(1L, 2L)));

        assertFalse(bound.isUnsupported());
        assertTrue(hits.contains("find#where[0]"));
        assertTrue(hits.contains("find#where[0]/if[0]"), hits.toString());
        assertTrue(hits.contains("find#where[0]/choose[0]"));
        assertTrue(hits.contains("find#where[0]/choose[0]/when[0]"));
        assertFalse(hits.contains("find#where[0]/choose[0]/otherwise[0]"));
        assertTrue(hits.contains("find#where[0]/foreach[0]"));
    }
}
