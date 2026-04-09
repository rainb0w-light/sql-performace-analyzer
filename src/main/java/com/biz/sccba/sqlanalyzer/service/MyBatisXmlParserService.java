package com.biz.sccba.sqlanalyzer.service;

import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBatis XML 解析服务
 *
 * 基于 MyBatis 原生解析器，实现：
 * 1. 解析 XML 中的 SQL 语句
 * 2. 提取动态标签（if, where, foreach, choose 等）
 * 3. 解析 test 条件表达式
 * 4. 生成多场景 SQL
 */
@Service
public class MyBatisXmlParserService {

    /**
     * 解析结果
     */
    public record ParseResult(
        String namespace,
        String statementId,
        String statementType,  // SELECT, UPDATE, INSERT, DELETE
        String originalSql,    // 原始动态 SQL
        List<String> dynamicSqls,  // 生成的多个静态 SQL
        List<TestCondition> testConditions,  // 提取的 test 条件
        Map<String, Object> parameters  // 参数信息
    ) {}

    /**
     * test 条件信息
     */
    public record TestCondition(
        String xpath,          // XML 路径
        String testExpression, // test 表达式
        String naturalLanguage, // 自然语言描述
        List<String> involvedFields  // 涉及的字段
    ) {}

    /**
     * 解析 MyBatis XML 内容
     */
    public ParseResult parse(String xmlContent, String namespace) {
        System.out.println("[MyBatisXmlParserService] 开始解析 MyBatis XML, namespace=" + namespace);

        try {
            // 1. 使用 XPathParser 解析 XML
            XPathParser parser = new XPathParser(xmlContent);
            XNode mapperNode = parser.evalNode("/mapper");

            if (mapperNode == null) {
                throw new IllegalArgumentException("无效的 MyBatis XML，未找到 mapper 节点");
            }

            String actualNamespace = mapperNode.getStringAttribute("namespace");
            if (namespace == null) {
                namespace = actualNamespace;
            }

            // 2. 提取所有 SQL 语句节点
            List<XNode> statementNodes = new ArrayList<>();
            statementNodes.addAll(mapperNode.evalNodes("select"));
            statementNodes.addAll(mapperNode.evalNodes("update"));
            statementNodes.addAll(mapperNode.evalNodes("insert"));
            statementNodes.addAll(mapperNode.evalNodes("delete"));

            // 3. 解析第一个语句作为示例（完整解析所有语句会在后续迭代中实现）
            if (statementNodes.isEmpty()) {
                return new ParseResult(
                    namespace,
                    null,
                    null,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap()
                );
            }

            XNode statementNode = statementNodes.get(0);
            String statementId = statementNode.getStringAttribute("id");
            String statementType = statementNode.getName().toUpperCase();

            // 4. 提取 SQL 内容（包括动态标签）
            String sqlContent = extractSqlContent(statementNode);

            // 5. 提取所有 test 条件
            List<TestCondition> testConditions = extractTestConditions(statementNode);

            // 6. 生成多场景 SQL
            List<String> dynamicSqls = generateSqlScenarios(sqlContent, testConditions);

            return new ParseResult(
                namespace,
                statementId,
                statementType,
                sqlContent,
                dynamicSqls,
                testConditions,
                Collections.emptyMap()
            );

        } catch (Exception e) {
            System.err.println("[MyBatisXmlParserService] 解析 MyBatis XML 失败：" + e.getMessage());
            throw new RuntimeException("解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 提取 SQL 内容（保留动态标签标记）
     */
    private String extractSqlContent(XNode statementNode) {
        StringBuilder sql = new StringBuilder();

        // 获取所有子节点和文本内容
        List<XNode> children = statementNode.getChildren();
        if (children == null || children.isEmpty()) {
            return statementNode.getStringAttribute("resultType") != null ?
                   statementNode.getStringAttribute("resultType") : "";
        }

        for (XNode child : children) {
            String nodeName = child.getNode().getNodeName();
            String content = child.getStringBody("");

            switch (nodeName) {
                case "if":
                    String test = child.getStringAttribute("test");
                    sql.append("/* IF: ").append(test).append(" */ ");
                    sql.append(content);
                    break;
                case "where":
                    sql.append(" WHERE ");
                    sql.append(extractSqlContent(child));
                    break;
                case "foreach":
                    String collection = child.getStringAttribute("collection");
                    String item = child.getStringAttribute("item");
                    String open = child.getStringAttribute("open", "");
                    String close = child.getStringAttribute("close", "");
                    String separator = child.getStringAttribute("separator", ",");
                    sql.append("/* FOREACH: ").append(collection)
                       .append(" AS ").append(item)
                       .append(" OPEN=").append(open)
                       .append(" CLOSE=").append(close)
                       .append(" SEPARATOR=").append(separator)
                       .append(" */");
                    break;
                case "choose":
                case "when":
                case "otherwise":
                    sql.append("/* CHOOSE */ ");
                    sql.append(extractSqlContent(child));
                    break;
                case "trim":
                    sql.append(extractSqlContent(child));
                    break;
                case "set":
                    sql.append(" SET ");
                    sql.append(extractSqlContent(child));
                    break;
                default:
                    sql.append(content);
            }
        }

        // 添加节点本身的文本内容
        String textContent = statementNode.getStringBody("");
        if (textContent != null && !textContent.trim().isEmpty()) {
            sql.insert(0, textContent);
        }

        return sql.toString().trim();
    }

    /**
     * 提取所有 test 条件
     */
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
                // 解析 test 表达式，提取涉及的字段
                List<String> fields = parseTestExpression(testExpr);
                conditions.add(new TestCondition(
                    currentXpath,
                    testExpr,
                    "",  // 自然语言描述由 LLM 生成
                    fields
                ));
            }
        }

        // 递归处理子节点
        List<XNode> children = node.getChildren();
        if (children != null) {
            for (XNode child : children) {
                extractTestConditionsRecursive(child, currentXpath, conditions);
            }
        }
    }

    /**
     * 解析 test 表达式，提取涉及的字段名
     * 例如："status != null and type == 1" → ["status", "type"]
     *
     * @param testExpr test 表达式
     * @return 字段列表
     */
    public List<String> parseTestExpression(String testExpr) {
        Set<String> fields = new LinkedHashSet<>();  // 使用 Set 去重

        // 匹配字段名：字段名通常在比较运算符左侧
        // 模式：word 后面跟比较运算符
        Pattern pattern = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(==|!=|<=|>=|<|>|&&|\\|\\||contains|startsWith|endsWith)\\s*"
        );

        Matcher matcher = pattern.matcher(testExpr);
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            // 过滤掉 null、true、false 等关键字
            if (!"null".equals(fieldName) && !"true".equals(fieldName) && !"false".equals(fieldName)) {
                fields.add(fieldName);
            }
        }

        return new ArrayList<>(fields);
    }

    /**
     * 根据 test 条件生成多场景 SQL
     *
     * 策略：业务常见场景
     * 1. 全条件满足：所有 if 条件都为 true
     * 2. 最小条件：仅必填条件满足
     * 3. 常见组合：根据业务经验最常见的 2-3 种组合
     */
    private List<String> generateSqlScenarios(String sqlContent, List<TestCondition> conditions) {
        List<String> scenarios = new ArrayList<>();

        if (conditions.isEmpty()) {
            // 没有动态条件，直接返回原始 SQL
            scenarios.add(replacePlaceholders(sqlContent));
            return scenarios;
        }

        // 场景 1: 全条件满足 - 保留所有 if 内容
        StringBuilder allConditionsSql = new StringBuilder(sqlContent);
        for (TestCondition condition : conditions) {
            // 模拟条件为 true，移除注释标记
            allConditionsSql = new StringBuilder(
                allConditionsSql.toString()
                    .replace("/* IF: " + condition.testExpression() + " */", "")
            );
        }
        scenarios.add(replacePlaceholders(allConditionsSql.toString()));

        // 场景 2: 最小条件 - 只保留第一个条件（通常是主要过滤条件）
        if (conditions.size() > 1) {
            StringBuilder minSql = new StringBuilder(sqlContent);
            for (int i = 1; i < conditions.size(); i++) {
                // 移除后续条件
                minSql = new StringBuilder(
                    minSql.toString()
                        .replaceAll("/\\* IF: " + Pattern.quote(conditions.get(i).testExpression()) + " \\*/[^/]*", "")
                );
            }
            scenarios.add(replacePlaceholders(minSql.toString()));
        }

        return scenarios;
    }

    /**
     * 替换 SQL 占位符
     * #{param} → 预编译占位符 ?
     * ${param} → 直接替换（需要参数值）
     */
    private String replacePlaceholders(String sql) {
        // 替换 #{param} 为 ?
        String result = sql.replaceAll("#\\{[^}]+\\}", "?");

        // 替换 ${param} 需要实际参数值，这里暂时保留标记
        // 实际填充时由 SqlFillerTool 处理
        return result.trim();
    }

    /**
     * 将 test 表达式转换为自然语言描述（供 LLM 使用）
     */
    public String testExprToNaturalLanguage(String testExpr) {
        // 使用 LLM 进行转换，这里提供基础模板
        StringBuilder description = new StringBuilder();

        // 拆分 AND/OR 条件
        String[] andParts = testExpr.split("\\s+and\\s+", -1);
        for (int i = 0; i < andParts.length; i++) {
            String part = andParts[i].trim();
            if (part.contains("or")) {
                String[] orParts = part.split("\\s+or\\s+", -1);
                description.append("条件").append(i + 1).append(": (");
                for (int j = 0; j < orParts.length; j++) {
                    if (j > 0) description.append(" 或 ");
                    description.append(parseSimpleCondition(orParts[j].trim()));
                }
                description.append(")");
            } else {
                description.append("条件").append(i + 1).append(": ")
                        .append(parseSimpleCondition(part));
            }
            if (i < andParts.length - 1) {
                description.append("，并且 ");
            }
        }

        return description.toString();
    }

    /**
     * 解析简单条件表达式
     */
    private String parseSimpleCondition(String expr) {
        if (expr.contains("!= null")) {
            return expr.replace("!= null", "不为空");
        } else if (expr.contains("== null")) {
            return expr.replace("== null", "为空");
        } else if (expr.contains("!=")) {
            return expr.replace("!=", "不等于");
        } else if (expr.contains("==")) {
            return expr.replace("==", "等于");
        } else if (expr.contains(">=")) {
            return expr.replace(">=", "大于等于");
        } else if (expr.contains("<=")) {
            return expr.replace("<=", "小于等于");
        } else if (expr.contains(">")) {
            return expr.replace(">", "大于");
        } else if (expr.contains("<")) {
            return expr.replace("<", "小于");
        }
        return expr;
    }
}
