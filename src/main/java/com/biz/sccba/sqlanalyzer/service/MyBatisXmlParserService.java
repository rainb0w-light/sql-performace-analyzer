package com.biz.sccba.sqlanalyzer.service;

import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structural MyBatis XML parsing ONLY (statement ids, raw dynamic SQL text, test-condition
 * inventory). Scenario SQL generation lives in the native runtime
 * (scenario/ScenarioEngine via MappedStatement.getBoundSql) — this service never evaluates or
 * assembles SQL (development-guide §6.1).
 */
@Service
public class MyBatisXmlParserService {

    /** Structural parse result — no generated SQL. */
    public record ParseResult(
        String namespace,
        String statementId,
        String statementType,  // SELECT, UPDATE, INSERT, DELETE
        String originalSql,    // raw dynamic SQL text, tags preserved
        List<TestCondition> testConditions,
        Map<String, Object> parameters
    ) {}

    /** Complete mapper result used by the artifact pipeline. */
    public record MapperParseResult(String namespace, List<ParseResult> statements) {}

    /**
     * test 条件信息（结构化目录，不求值）
     */
    public record TestCondition(
        String xpath,
        String testExpression,
        String naturalLanguage,
        List<String> involvedFields
    ) {}

    public ParseResult parse(String xmlContent, String namespace) {
        try {
            xmlContent = xmlContent.replaceAll("<!DOCTYPE[^>]*>", "");
            XPathParser parser = new XPathParser(xmlContent);
            XNode mapperNode = parser.evalNode("/mapper");

            if (mapperNode == null) {
                throw new IllegalArgumentException("无效的 MyBatis XML，未找到 mapper 节点");
            }

            String actualNamespace = mapperNode.getStringAttribute("namespace");
            if (namespace == null) {
                namespace = actualNamespace;
            }

            List<XNode> statementNodes = new ArrayList<>();
            statementNodes.addAll(mapperNode.evalNodes("select"));
            statementNodes.addAll(mapperNode.evalNodes("update"));
            statementNodes.addAll(mapperNode.evalNodes("insert"));
            statementNodes.addAll(mapperNode.evalNodes("delete"));

            if (statementNodes.isEmpty()) {
                return new ParseResult(namespace, null, null, null,
                        Collections.emptyList(), Collections.emptyMap());
            }

            return structural(namespace, statementNodes.get(0));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析失败：" + e.getMessage(), e);
        }
    }

    /** Parses every select/update/insert/delete structurally, preserving statement boundaries. */
    public MapperParseResult parseMapper(String xmlContent, String namespace) {
        try {
            String safeXml = xmlContent.replaceAll("<!DOCTYPE[^>]*>", "");
            XPathParser parser = new XPathParser(safeXml);
            XNode mapperNode = parser.evalNode("/mapper");
            if (mapperNode == null) throw new IllegalArgumentException("无效的 MyBatis XML，未找到 mapper 节点");
            String actualNamespace = mapperNode.getStringAttribute("namespace");
            String resolvedNamespace = namespace == null || namespace.isBlank() ? actualNamespace : namespace;
            List<XNode> nodes = new ArrayList<>();
            nodes.addAll(mapperNode.evalNodes("select"));
            nodes.addAll(mapperNode.evalNodes("update"));
            nodes.addAll(mapperNode.evalNodes("insert"));
            nodes.addAll(mapperNode.evalNodes("delete"));
            List<ParseResult> results = new ArrayList<>();
            for (XNode node : nodes) {
                results.add(structural(resolvedNamespace, node));
            }
            return new MapperParseResult(resolvedNamespace, results);
        } catch (Exception e) {
            throw new RuntimeException("解析失败：" + e.getMessage(), e);
        }
    }

    private ParseResult structural(String namespace, XNode statementNode) {
        return new ParseResult(
                namespace,
                statementNode.getStringAttribute("id"),
                statementNode.getName().toUpperCase(Locale.ROOT),
                extractSqlContent(statementNode),
                extractTestConditions(statementNode),
                Collections.emptyMap());
    }

    /** Raw dynamic SQL text with nested text preserved (for display/audit; never executed). */
    private String extractSqlContent(XNode statementNode) {
        StringBuilder sql = new StringBuilder();
        List<XNode> children = statementNode.getChildren();
        if (children == null || children.isEmpty()) {
            return statementNode.getStringAttribute("resultType") != null ?
                   statementNode.getStringAttribute("resultType") : "";
        }
        for (XNode child : children) {
            sql.append(child.getStringBody(""));
            sql.append(extractSqlContent(child));
        }
        String textContent = statementNode.getStringBody("");
        if (textContent != null && !textContent.trim().isEmpty()) {
            sql.insert(0, textContent);
        }
        return sql.toString().trim();
    }

    private List<TestCondition> extractTestConditions(XNode node) {
        List<TestCondition> conditions = new ArrayList<>();
        extractTestConditionsRecursive(node, "", conditions);
        return conditions;
    }

    private void extractTestConditionsRecursive(XNode node, String xpath, List<TestCondition> conditions) {
        if (node == null) return;

        String currentXpath = xpath.isEmpty() ? node.getName() : xpath + "/" + node.getName();

        if ("if".equals(node.getName()) || "when".equals(node.getName())) {
            String testExpr = node.getStringAttribute("test");
            if (testExpr != null && !testExpr.isEmpty()) {
                List<String> fields = parseTestExpression(testExpr);
                conditions.add(new TestCondition(currentXpath, testExpr, "", fields));
            }
        }

        List<XNode> children = node.getChildren();
        if (children != null) {
            for (XNode child : children) {
                extractTestConditionsRecursive(child, currentXpath, conditions);
            }
        }
    }

    /** Structural extraction of left-hand identifiers from a test expression (no evaluation). */
    public List<String> parseTestExpression(String testExpr) {
        Set<String> fields = new LinkedHashSet<>();
        Pattern pattern = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(==|!=|<=|>=|<|>|&&|\\|\\||contains|startsWith|endsWith)\\s*");
        Matcher matcher = pattern.matcher(testExpr);
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            if (!"null".equals(fieldName) && !"true".equals(fieldName) && !"false".equals(fieldName)) {
                fields.add(fieldName);
            }
        }
        return new ArrayList<>(fields);
    }
}
