package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ParsedSqlQuery;
import com.biz.sccba.sqlanalyzer.repository.ParsedSqlQueryRepository;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBatis Mapper XML解析服务
 * 解析MyBatis Mapper XML文件，提取所有可能的SQL查询
 */
@Service
public class MyBatisMapperParserService {

    private static final Logger logger = LoggerFactory.getLogger(MyBatisMapperParserService.class);

    @Autowired
    private ParsedSqlQueryRepository parsedSqlQueryRepository;

    /**
     * 解析MyBatis Mapper XML内容
     */
    @Transactional
    public ParseResult parseMapperXml(String xmlContent, String mapperNamespace) {
        logger.info("开始解析MyBatis Mapper XML: namespace={}", mapperNamespace);

        try {
            // 删除旧数据
            parsedSqlQueryRepository.deleteByMapperNamespace(mapperNamespace);

            SAXReader reader = new SAXReader();
            Document document = reader.read(new StringReader(xmlContent));
            Element root = document.getRootElement();

            // 验证根元素
            if (!"mapper".equals(root.getName())) {
                throw new IllegalArgumentException("不是有效的MyBatis Mapper XML文件");
            }

            // 提取namespace（如果XML中没有，使用传入的）
            String namespace = root.attributeValue("namespace");
            if (namespace == null || namespace.trim().isEmpty()) {
                namespace = mapperNamespace;
            }

            List<ParsedSqlQuery> queries = new ArrayList<>();

            // 解析所有SQL语句
            parseSelectStatements(root, namespace, queries);
            parseInsertStatements(root, namespace, queries);
            parseUpdateStatements(root, namespace, queries);
            parseDeleteStatements(root, namespace, queries);

            // 保存所有查询
            parsedSqlQueryRepository.saveAll(queries);

            logger.info("解析完成，共解析出 {} 个SQL查询", queries.size());

            ParseResult result = new ParseResult();
            result.setMapperNamespace(namespace);
            result.setQueryCount(queries.size());
            result.setQueries(queries);
            return result;

        } catch (DocumentException e) {
            logger.error("解析XML失败", e);
            throw new RuntimeException("解析XML失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析select语句
     */
    private void parseSelectStatements(Element root, String namespace, List<ParsedSqlQuery> queries) {
        @SuppressWarnings("unchecked")
        List<Element> selects = root.elements("select");
        for (Element select : selects) {
            parseSqlStatement(select, namespace, "select", queries);
        }
    }

    /**
     * 解析insert语句
     */
    private void parseInsertStatements(Element root, String namespace, List<ParsedSqlQuery> queries) {
        @SuppressWarnings("unchecked")
        List<Element> inserts = root.elements("insert");
        for (Element insert : inserts) {
            parseSqlStatement(insert, namespace, "insert", queries);
        }
    }

    /**
     * 解析update语句
     */
    private void parseUpdateStatements(Element root, String namespace, List<ParsedSqlQuery> queries) {
        @SuppressWarnings("unchecked")
        List<Element> updates = root.elements("update");
        for (Element update : updates) {
            parseSqlStatement(update, namespace, "update", queries);
        }
    }

    /**
     * 解析delete语句
     */
    private void parseDeleteStatements(Element root, String namespace, List<ParsedSqlQuery> queries) {
        @SuppressWarnings("unchecked")
        List<Element> deletes = root.elements("delete");
        for (Element delete : deletes) {
            parseSqlStatement(delete, namespace, "delete", queries);
        }
    }

    /**
     * 解析SQL语句，处理动态SQL
     */
    private void parseSqlStatement(Element statementElement, String namespace, String queryType, List<ParsedSqlQuery> queries) {
        String statementId = statementElement.attributeValue("id");
        if (statementId == null || statementId.trim().isEmpty()) {
            logger.warn("跳过没有id的{}语句", queryType);
            return;
        }

        String originalSql = getElementText(statementElement);
        
        // 处理动态SQL，生成所有可能的SQL组合
        List<SqlVariant> variants = processDynamicSql(statementElement, new HashMap<>(), "");

        for (SqlVariant variant : variants) {
            ParsedSqlQuery query = new ParsedSqlQuery();
            query.setMapperNamespace(namespace);
            query.setStatementId(statementId);
            query.setQueryType(queryType);
            query.setSql(variant.getSql());
            query.setOriginalSqlFragment(originalSql);
            query.setDynamicConditions(variant.getConditionsDescription());
            
            // 提取表名
            String tableName = extractTableName(variant.getSql());
            query.setTableName(tableName);

            queries.add(query);
        }
    }

    /**
     * 处理动态SQL，生成所有可能的SQL组合
     */
    private List<SqlVariant> processDynamicSql(Element element, Map<String, Boolean> conditions, String prefix) {
        List<SqlVariant> results = new ArrayList<>();
        
        StringBuilder sql = new StringBuilder(prefix);
        List<Element> children = element.elements();

        int i = 0;
        while (i < children.size()) {
            Element child = children.get(i);
            String tagName = child.getName();

            switch (tagName) {
                case "if":
                    // 处理<if>标签：生成两个分支（条件为true和false）
                    String test = child.attributeValue("test");
                    if (test != null) {
                        String conditionKey = test.trim();
                        
                        // 分支1：条件为true
                        Map<String, Boolean> trueConditions = new HashMap<>(conditions);
                        trueConditions.put(conditionKey, true);
                        String trueText = getElementText(child);
                        List<SqlVariant> trueVariants = processDynamicSql(child, trueConditions, sql.toString() + trueText);
                        results.addAll(trueVariants);
                        
                        // 分支2：条件为false（跳过if内容）
                        Map<String, Boolean> falseConditions = new HashMap<>(conditions);
                        falseConditions.put(conditionKey, false);
                        List<SqlVariant> falseVariants = processDynamicSql(child, falseConditions, sql.toString());
                        results.addAll(falseVariants);
                        
                        return results; // 遇到if后需要返回，因为已经处理了所有分支
                    }
                    break;
                    
                case "choose":
                    // 处理<choose>标签：处理when和otherwise
                    List<SqlVariant> chooseResults = processChoose(child, conditions, sql.toString());
                    results.addAll(chooseResults);
                    return results;
                    
                case "where":
                case "set":
                case "trim":
                    // 这些标签只是包装器，递归处理子元素
                    List<SqlVariant> wrappedResults = processDynamicSql(child, conditions, sql.toString());
                    results.addAll(wrappedResults);
                    return results;
                    
                case "foreach":
                    // 处理<foreach>标签：生成多个SQL变体
                    List<SqlVariant> foreachResults = processForeach(child, conditions, sql.toString());
                    results.addAll(foreachResults);
                    return results;
                    
                default:
                    // 普通文本或SQL片段
                    String text = getElementText(child);
                    sql.append(text);
                    break;
            }
            i++;
        }

        // 如果没有动态标签，直接返回当前SQL
        if (results.isEmpty()) {
            String finalSql = sql.toString() + getElementText(element);
            SqlVariant variant = new SqlVariant();
            variant.setSql(finalSql.trim());
            variant.setConditionsDescription(buildConditionsDescription(conditions));
            results.add(variant);
        }

        return results;
    }

    /**
     * 处理<choose>标签
     */
    private List<SqlVariant> processChoose(Element chooseElement, Map<String, Boolean> conditions, String prefix) {
        List<SqlVariant> results = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        List<Element> children = chooseElement.elements();
        
        boolean hasWhen = false;
        for (Element child : children) {
            if ("when".equals(child.getName())) {
                hasWhen = true;
                String test = child.attributeValue("test");
                if (test != null) {
                    Map<String, Boolean> newConditions = new HashMap<>(conditions);
                    newConditions.put(test.trim(), true);
                    String whenText = getElementText(child);
                    List<SqlVariant> whenVariants = processDynamicSql(child, newConditions, prefix + whenText);
                    results.addAll(whenVariants);
                }
            } else if ("otherwise".equals(child.getName())) {
                String otherwiseText = getElementText(child);
                List<SqlVariant> otherwiseVariants = processDynamicSql(child, conditions, prefix + otherwiseText);
                results.addAll(otherwiseVariants);
            }
        }
        
        // 如果没有when分支被满足，使用otherwise（如果存在）
        if (!hasWhen) {
            for (Element child : children) {
                if ("otherwise".equals(child.getName())) {
                    String otherwiseText = getElementText(child);
                    List<SqlVariant> otherwiseVariants = processDynamicSql(child, conditions, prefix + otherwiseText);
                    results.addAll(otherwiseVariants);
                }
            }
        }
        
        return results;
    }

    /**
     * 处理<foreach>标签
     */
    private List<SqlVariant> processForeach(Element foreachElement, Map<String, Boolean> conditions, String prefix) {
        List<SqlVariant> results = new ArrayList<>();
        
        String collection = foreachElement.attributeValue("collection");
        String item = foreachElement.attributeValue("item");
        String open = foreachElement.attributeValue("open");
        String close = foreachElement.attributeValue("close");
        String separator = foreachElement.attributeValue("separator");
        
        // 获取foreach内容
        String content = getElementText(foreachElement);
        
        // 简化处理：生成一个示例SQL（实际应用中可能需要更复杂的处理）
        // 这里我们生成一个包含占位符的SQL
        StringBuilder sql = new StringBuilder(prefix);
        if (open != null) {
            sql.append(open);
        }
        sql.append(content.replace("#{" + item + "}", "?"));
        if (close != null) {
            sql.append(close);
        }
        
        SqlVariant variant = new SqlVariant();
        variant.setSql(sql.toString().trim());
        variant.setConditionsDescription(buildConditionsDescription(conditions) + 
            " [foreach: " + collection + "]");
        results.add(variant);
        
        return results;
    }

    /**
     * 获取元素的文本内容（包括子元素的文本）
     */
    private String getElementText(Element element) {
        StringBuilder text = new StringBuilder();
        
        @SuppressWarnings("unchecked")
        List<Element> children = element.elements();
        
        if (children.isEmpty()) {
            // 没有子元素，直接返回文本
            return element.getTextTrim();
        } else {
            // 有子元素，递归获取文本
            for (Element child : children) {
                String childText = getElementText(child);
                text.append(childText);
            }
            // 也添加元素本身的文本
            text.append(element.getTextTrim());
        }
        
        return text.toString();
    }

    /**
     * 构建条件描述
     */
    private String buildConditionsDescription(Map<String, Boolean> conditions) {
        if (conditions.isEmpty()) {
            return "无动态条件";
        }
        
        List<String> descList = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : conditions.entrySet()) {
            descList.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join(", ", descList);
    }

    /**
     * 从SQL中提取表名
     */
    private String extractTableName(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }

        // 简单的表名提取逻辑（可以改进）
        Pattern pattern = Pattern.compile(
            "(?i)(?:FROM|JOIN|UPDATE|INTO)\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = pattern.matcher(sql);
        Set<String> tableNames = new LinkedHashSet<>();
        
        while (matcher.find()) {
            String tableName = matcher.group(1);
            // 移除数据库名前缀
            if (tableName.contains(".")) {
                tableName = tableName.substring(tableName.indexOf(".") + 1);
            }
            tableNames.add(tableName);
        }
        
        return String.join(",", tableNames);
    }

    /**
     * 根据表名获取相关查询
     */
    public List<Map<String, Object>> getQueriesByTable(String tableName) {
        List<ParsedSqlQuery> queries = parsedSqlQueryRepository.findByTableName(tableName);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (ParsedSqlQuery query : queries) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", query.getId());
            map.put("mapperNamespace", query.getMapperNamespace());
            map.put("statementId", query.getStatementId());
            map.put("queryType", query.getQueryType());
            map.put("sql", query.getSql());
            map.put("tableName", query.getTableName());
            map.put("dynamicConditions", query.getDynamicConditions());
            result.add(map);
        }
        
        return result;
    }

    /**
     * SQL变体（处理动态SQL时使用）
     */
    private static class SqlVariant {
        private String sql;
        private String conditionsDescription;

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public String getConditionsDescription() {
            return conditionsDescription;
        }

        public void setConditionsDescription(String conditionsDescription) {
            this.conditionsDescription = conditionsDescription;
        }
    }

    /**
     * 解析结果
     */
    public static class ParseResult {
        private String mapperNamespace;
        private int queryCount;
        private List<ParsedSqlQuery> queries;

        public String getMapperNamespace() {
            return mapperNamespace;
        }

        public void setMapperNamespace(String mapperNamespace) {
            this.mapperNamespace = mapperNamespace;
        }

        public int getQueryCount() {
            return queryCount;
        }

        public void setQueryCount(int queryCount) {
            this.queryCount = queryCount;
        }

        public List<ParsedSqlQuery> getQueries() {
            return queries;
        }

        public void setQueries(List<ParsedSqlQuery> queries) {
            this.queries = queries;
        }
    }
}

