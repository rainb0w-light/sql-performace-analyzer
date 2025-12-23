package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.MapperParameter;
import com.biz.sccba.sqlanalyzer.model.ParseResult;
import com.biz.sccba.sqlanalyzer.model.ParsedSqlQuery;
import com.biz.sccba.sqlanalyzer.repository.MapperParameterRepository;
import com.biz.sccba.sqlanalyzer.repository.ParsedSqlQueryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private MapperParameterRepository mapperParameterRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
     * 只解析查询SQL（SELECT语句）
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
            
            // 只处理SELECT查询
            if (mappedStatement.getSqlCommandType() != SqlCommandType.SELECT) {
                continue;
            }
            
            // 提取SQL（使用存储的参数）
            String sql = extractSqlFromMappedStatement(mappedStatement, statementId);
            
            ParsedSqlQuery query = new ParsedSqlQuery();
            query.setMapperNamespace(mapperNamespace);  
            query.setStatementId(statementId.substring(mapperNamespace.length() + 1));
            query.setQueryType("select");
            query.setSql(sql);
            query.setOriginalSqlFragment(sql);
            query.setTableName(extractTableName(sql));
            query.setDynamicConditions("使用MyBatis内置解析器解析");
            
            queries.add(query);
        }
        
        return queries;
    }

    /**
     * 从MappedStatement提取SQL
     * 优先使用H2数据库中存储的参数来解析动态SQL
     * 
     * @param mappedStatement MyBatis MappedStatement
     * @param statementId 完整的statement ID（格式：namespace.statementId）
     * @return 解析后的SQL
     */
    private String extractSqlFromMappedStatement(MappedStatement mappedStatement, String statementId) {
        try {
            // 尝试从数据库获取存储的参数
            Map<String, Object> parameters = getStoredParameters(statementId);
            
            // 使用参数获取BoundSql（如果有参数则使用参数，否则使用null）
            Object parameterObject = parameters != null && !parameters.isEmpty() ? parameters : null;
            org.apache.ibatis.mapping.BoundSql boundSql = mappedStatement.getBoundSql(parameterObject);
            String sql = boundSql.getSql();
            
            // 清理SQL（移除多余的空格和换行）
            sql = sql.replaceAll("\\s+", " ").trim();
            
            return sql;
        } catch (Exception e) {
            logger.debug("无法从MappedStatement提取SQL: statementId={}, error={}", statementId, e.getMessage());
            // 如果无法获取BoundSql，返回占位符
            return "[动态SQL - 需要参数才能解析]";
        }
    }

    /**
     * 从H2数据库获取存储的参数
     * 支持层级查找：从最具体到最外层逐级查找
     * 例如：com.example.demo.selectByKey -> com.example.demo -> com.example -> com
     * 
     * @param mapperId Mapper ID（格式：namespace.statementId 或 namespace）
     * @return 参数Map，如果不存在则返回null
     */
    private Map<String, Object> getStoredParameters(String mapperId) {
        if (mapperId == null || mapperId.trim().isEmpty()) {
            return null;
        }
        
        // 生成所有可能的层级路径（从最具体到最外层）
        List<String> searchPaths = generateHierarchyPaths(mapperId);
        
        // 按顺序查找，找到第一个匹配的就返回
        for (String path : searchPaths) {
            try {
                Optional<MapperParameter> parameterOpt = mapperParameterRepository.findByMapperId(path);
                if (parameterOpt.isPresent()) {
                    MapperParameter parameter = parameterOpt.get();
                    logger.debug("找到参数: mapperId={}, 匹配路径={}", mapperId, path);
                    // 将JSON字符串解析为Map<String,Object>
                    return objectMapper.readValue(
                        parameter.getParameterJson(),
                        new TypeReference<Map<String, Object>>() {}
                    );
                }
            } catch (Exception e) {
                logger.debug("解析存储的参数失败: mapperId={}, path={}, error={}", mapperId, path, e.getMessage());
                // 继续查找下一个层级
            }
        }
        
        logger.debug("未找到参数: mapperId={}, 已搜索路径={}", mapperId, searchPaths);
        return null;
    }

    /**
     * 生成层级查找路径列表
     * 从最具体到最外层
     * 例如：com.example.demo.selectByKey -> [com.example.demo.selectByKey, com.example.demo, com.example, com]
     * 
     * @param mapperId 完整的Mapper ID
     * @return 层级路径列表
     */
    private List<String> generateHierarchyPaths(String mapperId) {
        List<String> paths = new ArrayList<>();
        
        if (mapperId == null || mapperId.trim().isEmpty()) {
            return paths;
        }
        
        // 先添加完整路径
        paths.add(mapperId.trim());
        
        // 如果包含句号，则逐级向上查找
        String current = mapperId.trim();
        while (current.contains(".")) {
            // 找到最后一个句号的位置
            int lastDotIndex = current.lastIndexOf(".");
            // 截取到最后一个句号之前的部分
            current = current.substring(0, lastDotIndex);
            if (!current.isEmpty()) {
                paths.add(current);
            }
        }
        
        return paths;
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

    /**
     * 保存或更新Mapper参数
     * 
     * @param mapperId Mapper ID（格式：namespace.statementId）
     * @param parameters 参数Map
     * @return 保存的参数实体
     */
    @Transactional
    public MapperParameter saveMapperParameter(String mapperId, Map<String, Object> parameters) {
        try {
            // 将参数Map转换为JSON字符串
            String parameterJson = objectMapper.writeValueAsString(parameters);
            
            // 查找是否已存在
            Optional<MapperParameter> existingOpt = mapperParameterRepository.findByMapperId(mapperId);
            MapperParameter parameter;
            
            if (existingOpt.isPresent()) {
                // 更新现有参数
                parameter = existingOpt.get();
                parameter.setParameterJson(parameterJson);
            } else {
                // 创建新参数
                parameter = new MapperParameter();
                parameter.setMapperId(mapperId);
                parameter.setParameterJson(parameterJson);
            }
            
            return mapperParameterRepository.save(parameter);
        } catch (Exception e) {
            logger.error("保存Mapper参数失败: mapperId={}", mapperId, e);
            throw new RuntimeException("保存Mapper参数失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取所有Mapper参数
     * 
     * @return 所有Mapper参数列表
     */
    public List<MapperParameter> getAllMapperParameters() {
        return mapperParameterRepository.findAll();
    }

    /**
     * 根据Mapper ID获取参数
     * 
     * @param mapperId Mapper ID（格式：namespace.statementId）
     * @return 参数Map，如果不存在则返回null
     */
    public Map<String, Object> getMapperParameter(String mapperId) {
        return getStoredParameters(mapperId);
    }

    /**
     * 删除Mapper参数
     * 
     * @param mapperId Mapper ID（格式：namespace.statementId）
     */
    @Transactional
    public void deleteMapperParameter(String mapperId) {
        mapperParameterRepository.deleteById(mapperId);
    }

}
