package com.biz.sccba.sqlanalyzer.mybatis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Definition-of-done guard: no hand-written dynamic-SQL evaluation or assembly may exist in
 * production sources; final SQL comes exclusively from MappedStatement.getBoundSql.
 */
class NoHandwrittenDynamicSqlTest {

    /** Patterns characteristic of the removed hand-built scenario generation. */
    private static final List<String> FORBIDDEN_PATTERNS = List.of(
            "generateSqlScenarios",
            "replacePlaceholders",
            "/* IF: ",
            "/* FOREACH: ",
            "/* CHOOSE */",
            "dynamicSqls");

    @Test
    void noHandwrittenScenarioEvaluationInMainSources() throws IOException {
        List<String> violations = new ArrayList<>();
        Path root = Paths.get("src/main/java");
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file);
                for (String pattern : FORBIDDEN_PATTERNS) {
                    if (content.contains(pattern)) {
                        violations.add(file + " contains forbidden pattern: " + pattern);
                    }
                }
                // No regex-based replacement of MyBatis placeholders in production code.
                if (content.contains("replaceAll(\"#")) {
                    violations.add(file + " rewrites #{} placeholders by regex");
                }
                if (content.contains("replaceAll(\"/\\\\* IF")) {
                    violations.add(file + " rewrites IF markers by regex");
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Hand-written dynamic SQL evaluation detected:\n  " + String.join("\n  ", violations));
    }

    @Test
    void officialBoundSqlPathIsPresent() throws IOException {
        Path engine = Paths.get("src/main/java/com/biz/sccba/sqlanalyzer/scenario/ScenarioEngine.java");
        Path runtime = Paths.get("src/main/java/com/biz/sccba/sqlanalyzer/mybatis/MyBatisStatementRuntime.java");
        assertTrue(Files.exists(engine) && Files.exists(runtime));
        assertTrue(Files.readString(runtime).contains("getBoundSql(parameterObject)"),
                "final SQL must come from MappedStatement.getBoundSql(parameterObject)");
        assertTrue(Files.readString(runtime).contains("XMLMapperBuilder"),
                "mappers must load through the official XMLMapperBuilder");
    }

    @Test
    void legacyOrchestratorAndWebsocketStayRemoved() throws IOException {
        Path root = Paths.get("src/main/java");
        List<String> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("EnhancedSQLAnalysis" + "Orchestrator.java")
                        || name.equals("SQLAnalysis" + "Orchestrator.java")
                        || name.contains("WebSocket") || name.contains("OpenTui")
                        || name.contains("SpringAi")) {
                    found.add(file.toString());
                }
            }
        }
        assertFalse(found.contains(null));
        assertTrue(found.isEmpty(), "legacy architecture must stay removed: " + found);
    }
}
