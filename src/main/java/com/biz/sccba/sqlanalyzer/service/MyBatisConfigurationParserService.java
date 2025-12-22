package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ParseResult;
import com.biz.sccba.sqlanalyzer.model.ParsedSqlQuery;
import com.biz.sccba.sqlanalyzer.repository.ParsedSqlQueryRepository;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用MyBatis内置解析器解析Mapper XML配置
 * 只支持基于XML模板的SQL配置解析
 */
@Service
public class MyBatisConfigurationParserService {

    private static final Logger logger = LoggerFactory.getLogger(MyBatisConfigurationParserService.class);

    @Autowired
    private ParsedSqlQueryRepository parsedSqlQueryRepository;

    /**
     * 从XML内容解析Mapper（使用MyBatis内置解析器）
     * 
     * @param xmlContent XML内容
     * @param mapperNamespace Mapper命名空间
     * @return 解析结果
     */
    @Transactional
    public ParseResult parseMapperXml(String xmlContent, String mapperNamespace) {
        logger.info("开始使用MyBatis内置解析器解析XML: namespace={}", mapperNamespace);

        try {
            // 删除旧数据
            parsedSqlQueryRepository.deleteByMapperNamespace(mapperNamespace);

            // 创建MyBatis Configuration
            Configuration configuration = createMyBatisConfiguration();
            
            // 解析XML
            parseMapperXml(configuration, xmlContent, mapperNamespace);

            // 从Configuration中提取所有MappedStatement
            List<ParsedSqlQuery> queries = extractQueriesFromConfiguration(configuration, mapperNamespace);

            // 保存所有查询
            parsedSqlQueryRepository.saveAll(queries);

            logger.info("解析完成，共解析出 {} 个SQL查询", queries.size());

            ParseResult result = new ParseResult();
            result.setMapperNamespace(mapperNamespace);
            result.setQueryCount(queries.size());
            result.setQueries(queries);
            return result;

        } catch (Exception e) {
            logger.error("解析XML失败", e);
            throw new RuntimeException("解析XML失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建MyBatis Configuration
     */
    private Configuration createMyBatisConfiguration() {
        Configuration configuration = new Configuration();
        // 设置一些基本配置
        configuration.setMapUnderscoreToCamelCase(true);
        return configuration;
    }

    /**
     * 解析Mapper XML（使用MyBatis内置解析器）
     */
    private void parseMapperXml(Configuration configuration, String xmlContent, String mapperNamespace) throws IOException {
        Resource resource = new ByteArrayResource(xmlContent.getBytes("UTF-8"));
        XMLMapperBuilder mapperParser = new XMLMapperBuilder(
            resource.getInputStream(),
            configuration,
            mapperNamespace,
            configuration.getSqlFragments()
        );
        mapperParser.parse();
    }

    /**
     * 从Configuration中提取所有MappedStatement并转换为ParsedSqlQuery
     */
    private List<ParsedSqlQuery> extractQueriesFromConfiguration(Configuration configuration, String mapperNamespace) {
        List<ParsedSqlQuery> queries = new ArrayList<>();
        
        Collection<String> statementIds = configuration.getMappedStatementNames();
        
        for (String statementId : statementIds) {
            // 只处理当前命名空间的语句
            if (!statementId.startsWith(mapperNamespace + ".")) {
                continue;
            }
            
            MappedStatement mappedStatement = configuration.getMappedStatement(statementId);
            
            ParsedSqlQuery query = new ParsedSqlQuery();
            query.setMapperNamespace(mapperNamespace);
            
            // 提取方法名（statementId的最后一部分）
            String methodName = statementId.substring(mapperNamespace.length() + 1);
            query.setStatementId(methodName);
            
            // 获取SQL命令类型
            SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
            query.setQueryType(sqlCommandType.name().toLowerCase());
            
            // 获取SQL源码（包含动态SQL标签）
            String sqlSource = mappedStatement.getSqlSource().getClass().getSimpleName();
            
            // 尝试获取SQL文本
            // 注意：对于动态SQL，这里可能无法直接获取最终SQL
            // 我们需要使用BoundSql来获取（但这需要参数）
            String sql = extractSqlFromMappedStatement(mappedStatement);
            query.setSql(sql);
            query.setOriginalSqlFragment(sql);
            
            // 提取表名
            String tableName = extractTableName(sql);
            query.setTableName(tableName);
            
            // 标记为使用MyBatis内置解析器
            query.setDynamicConditions("使用MyBatis内置解析器解析");
            
            queries.add(query);
        }
        
        return queries;
    }

    /**
     * 从MappedStatement提取SQL
     * 注意：对于动态SQL，这里只能获取到原始SQL片段
     */
    private String extractSqlFromMappedStatement(MappedStatement mappedStatement) {
        try {
            // 创建一个空的参数对象来获取BoundSql
            // 对于动态SQL，这可能会失败，所以需要捕获异常
            org.apache.ibatis.mapping.BoundSql boundSql = mappedStatement.getBoundSql(null);
            String sql = boundSql.getSql();
            
            // 清理SQL（移除多余的空格和换行）
            sql = sql.replaceAll("\\s+", " ").trim();
            
            return sql;
        } catch (Exception e) {
            logger.debug("无法从MappedStatement提取SQL: {}", e.getMessage());
            // 如果无法获取BoundSql，返回占位符
            return "[动态SQL - 需要参数才能解析]";
        }
    }

    /**
     * 从SQL中提取表名
     */
    private String extractTableName(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }

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

}
