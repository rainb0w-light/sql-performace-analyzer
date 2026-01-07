package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.MapperParameter;
import com.biz.sccba.sqlanalyzer.data.ParseResult;
import com.biz.sccba.sqlanalyzer.model.ParsedSqlQuery;
import com.biz.sccba.sqlanalyzer.repository.MapperParameterRepository;
import com.biz.sccba.sqlanalyzer.repository.ParsedSqlQueryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.ChooseSqlNode;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.ForEachSqlNode;
import org.apache.ibatis.scripting.xmltags.IfSqlNode;
import org.apache.ibatis.scripting.xmltags.MixedSqlNode;
import org.apache.ibatis.scripting.xmltags.SetSqlNode;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.apache.ibatis.scripting.xmltags.TrimSqlNode;
import org.apache.ibatis.scripting.xmltags.WhereSqlNode;
import org.apache.ibatis.session.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Field;
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

    @Autowired(required = false)
    private ApplicationContext applicationContext;

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
            Map<String, Object> extractionResult = extractQueriesFromConfiguration(configuration, mapperNamespace);
            @SuppressWarnings("unchecked")
            List<ParsedSqlQuery> queries = (List<ParsedSqlQuery>) extractionResult.get("queries");
            @SuppressWarnings("unchecked")
            Map<String, Set<String>> testExpressionsMap = (Map<String, Set<String>>) extractionResult.get("testExpressionsMap");

            // 保存所有查询
            parsedSqlQueryRepository.saveAll(queries);

            // 保存 test 表达式作为参数（同时保存到 statementId 和 namespace 两个层级）
            for (Map.Entry<String, Set<String>> entry : testExpressionsMap.entrySet()) {
                String statementId = entry.getKey();
                Set<String> testExpressions = entry.getValue();
                
                // 保存到完整路径（namespace.statementId）
                saveTestExpressionsAsParameters(statementId, testExpressions);
            }

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
     * 
     * @param configuration MyBatis Configuration
     * @param mapperNamespace Mapper命名空间
     * @return 包含查询列表和 test 表达式的映射（key: statementId, value: test 表达式集合）
     */
    private Map<String, Object> extractQueriesFromConfiguration(Configuration configuration, String mapperNamespace) {
        List<ParsedSqlQuery> queries = new ArrayList<>();
        Map<String, Set<String>> testExpressionsMap = new HashMap<>();
        
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
            
            // 提取 test 表达式（在提取 SQL 之前）
            Set<String> testExpressions = extractTestExpressionsFromSqlSource(mappedStatement, statementId);
            if (!testExpressions.isEmpty()) {
                testExpressionsMap.put(statementId, testExpressions);
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
        
        Map<String, Object> result = new HashMap<>();
        result.put("queries", queries);
        result.put("testExpressionsMap", testExpressionsMap);
        return result;
    }

    /**
     * 使用反射安全访问 DynamicSqlSource 的 rootSqlNode 字段
     * 
     * @param dynamicSqlSource DynamicSqlSource 实例
     * @return rootSqlNode，如果访问失败则返回 null
     */
    private SqlNode getRootSqlNode(DynamicSqlSource dynamicSqlSource) {
        try {
            Field rootSqlNodeField = DynamicSqlSource.class.getDeclaredField("rootSqlNode");
            rootSqlNodeField.setAccessible(true);
            return (SqlNode) rootSqlNodeField.get(dynamicSqlSource);
        } catch (Exception e) {
            logger.warn("无法访问 DynamicSqlSource 的 rootSqlNode 字段: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射安全访问 IfSqlNode 的 testExpression 字段
     * 
     * @param ifSqlNode IfSqlNode 实例
     * @return test 表达式，如果访问失败则返回 null
     */
    private String getTestExpression(IfSqlNode ifSqlNode) {
        try {
            Field testExpressionField = IfSqlNode.class.getDeclaredField("test");
            testExpressionField.setAccessible(true);
            return (String) testExpressionField.get(ifSqlNode);
        } catch (Exception e) {
            logger.warn("无法访问 IfSqlNode 的 test 字段: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射安全访问 ChooseSqlNode 的 ifSqlNodes 字段
     * 
     * @param chooseSqlNode ChooseSqlNode 实例
     * @return ifSqlNodes 列表，如果访问失败则返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<SqlNode> getIfSqlNodes(ChooseSqlNode chooseSqlNode) {
        try {
            Field ifSqlNodesField = ChooseSqlNode.class.getDeclaredField("ifSqlNodes");
            ifSqlNodesField.setAccessible(true);
            return (List<SqlNode>) ifSqlNodesField.get(chooseSqlNode);
        } catch (Exception e) {
            logger.warn("无法访问 ChooseSqlNode 的 ifSqlNodes 字段: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 使用反射安全访问 MixedSqlNode 的 contents 字段
     * 
     * @param mixedSqlNode MixedSqlNode 实例
     * @return contents 列表，如果访问失败则返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<SqlNode> getContents(MixedSqlNode mixedSqlNode) {
        try {
            Field contentsField = MixedSqlNode.class.getDeclaredField("contents");
            contentsField.setAccessible(true);
            return (List<SqlNode>) contentsField.get(mixedSqlNode);
        } catch (Exception e) {
            logger.warn("无法访问 MixedSqlNode 的 contents 字段: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 使用反射安全访问 TrimSqlNode 的 contents 字段
     * 
     * @param trimSqlNode TrimSqlNode 实例
     * @return contents SqlNode，如果访问失败则返回 null
     */
    private SqlNode getTrimContents(TrimSqlNode trimSqlNode) {
        try {
            Field contentsField = TrimSqlNode.class.getDeclaredField("contents");
            contentsField.setAccessible(true);
            return (SqlNode) contentsField.get(trimSqlNode);
        } catch (Exception e) {
            logger.warn("无法访问 TrimSqlNode 的 contents 字段: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射安全访问 WhereSqlNode 的 contents 字段
     * 
     * @param whereSqlNode WhereSqlNode 实例
     * @return contents SqlNode，如果访问失败则返回 null
     */
    private SqlNode getWhereContents(WhereSqlNode whereSqlNode) {
        try {
            Field contentsField = WhereSqlNode.class.getDeclaredField("contents");
            contentsField.setAccessible(true);
            return (SqlNode) contentsField.get(whereSqlNode);
        } catch (Exception e) {
            logger.warn("无法访问 WhereSqlNode 的 contents 字段: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射安全访问 SetSqlNode 的 contents 字段
     * 
     * @param setSqlNode SetSqlNode 实例
     * @return contents SqlNode，如果访问失败则返回 null
     */
    private SqlNode getSetContents(SetSqlNode setSqlNode) {
        try {
            Field contentsField = SetSqlNode.class.getDeclaredField("contents");
            contentsField.setAccessible(true);
            return (SqlNode) contentsField.get(setSqlNode);
        } catch (Exception e) {
            logger.warn("无法访问 SetSqlNode 的 contents 字段: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射安全访问 ForEachSqlNode 的 contents 字段
     * 
     * @param forEachSqlNode ForEachSqlNode 实例
     * @return contents SqlNode，如果访问失败则返回 null
     */
    private SqlNode getForEachContents(ForEachSqlNode forEachSqlNode) {
        try {
            Field contentsField = ForEachSqlNode.class.getDeclaredField("contents");
            contentsField.setAccessible(true);
            return (SqlNode) contentsField.get(forEachSqlNode);
        } catch (Exception e) {
            logger.warn("无法访问 ForEachSqlNode 的 contents 字段: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 SqlSource 提取所有 test 表达式
     * 
     * @param mappedStatement MyBatis MappedStatement
     * @param statementId 完整的statement ID（格式：namespace.statementId）
     * @return test 表达式集合（去重）
     */
    private Set<String> extractTestExpressionsFromSqlSource(MappedStatement mappedStatement, String statementId) {
        Set<String> testExpressions = new LinkedHashSet<>();
        
        try {
            SqlSource sqlSource = mappedStatement.getSqlSource();
            
            // 检查是否为 DynamicSqlSource
            if (sqlSource instanceof DynamicSqlSource) {
                DynamicSqlSource dynamicSqlSource = (DynamicSqlSource) sqlSource;
                SqlNode rootSqlNode = getRootSqlNode(dynamicSqlSource);
                
                if (rootSqlNode != null) {
                    extractTestExpressionsFromSqlNode(rootSqlNode, testExpressions);
                    logger.debug("从 DynamicSqlSource 提取到 {} 个 test 表达式: statementId={}", 
                        testExpressions.size(), statementId);
                } else {
                    logger.debug("无法获取 rootSqlNode: statementId={}", statementId);
                }
            } else {
                logger.debug("SqlSource 不是 DynamicSqlSource 类型: statementId={}, type={}", 
                    statementId, sqlSource.getClass().getName());
            }
        } catch (Exception e) {
            logger.warn("提取 test 表达式失败: statementId={}, error={}", statementId, e.getMessage());
        }
        
        return testExpressions;
    }

    /**
     * 递归遍历 SqlNode 树，提取所有 test 表达式
     * 
     * @param sqlNode SqlNode 节点
     * @param testExpressions 用于收集 test 表达式的集合
     */
    private void extractTestExpressionsFromSqlNode(SqlNode sqlNode, Set<String> testExpressions) {
        if (sqlNode == null) {
            return;
        }

        try {
            // 处理 IfSqlNode
            if (sqlNode instanceof IfSqlNode) {
                IfSqlNode ifSqlNode = (IfSqlNode) sqlNode;
                String testExpression = getTestExpression(ifSqlNode);
                if (testExpression != null && !testExpression.trim().isEmpty()) {
                    testExpressions.add(testExpression.trim());
                }
                // IfSqlNode 内部可能还有子节点，需要递归处理
                SqlNode contents = getIfSqlNodeContents(ifSqlNode);
                if (contents != null) {
                    extractTestExpressionsFromSqlNode(contents, testExpressions);
                }
            }
            // 处理 ChooseSqlNode
            else if (sqlNode instanceof ChooseSqlNode) {
                ChooseSqlNode chooseSqlNode = (ChooseSqlNode) sqlNode;
                List<SqlNode> ifSqlNodes = getIfSqlNodes(chooseSqlNode);
                for (SqlNode whenNode : ifSqlNodes) {
                    extractTestExpressionsFromSqlNode(whenNode, testExpressions);
                }
                // 处理 otherwise 节点
                SqlNode defaultSqlNode = getChooseDefaultSqlNode(chooseSqlNode);
                if (defaultSqlNode != null) {
                    extractTestExpressionsFromSqlNode(defaultSqlNode, testExpressions);
                }
            }
            // 处理 MixedSqlNode
            else if (sqlNode instanceof MixedSqlNode) {
                MixedSqlNode mixedSqlNode = (MixedSqlNode) sqlNode;
                List<SqlNode> contents = getContents(mixedSqlNode);
                for (SqlNode childNode : contents) {
                    extractTestExpressionsFromSqlNode(childNode, testExpressions);
                }
            }
            // 处理 TrimSqlNode
            else if (sqlNode instanceof TrimSqlNode) {
                TrimSqlNode trimSqlNode = (TrimSqlNode) sqlNode;
                SqlNode contents = getTrimContents(trimSqlNode);
                if (contents != null) {
                    extractTestExpressionsFromSqlNode(contents, testExpressions);
                }
            }
            // 处理 WhereSqlNode
            else if (sqlNode instanceof WhereSqlNode) {
                WhereSqlNode whereSqlNode = (WhereSqlNode) sqlNode;
                SqlNode contents = getWhereContents(whereSqlNode);
                if (contents != null) {
                    extractTestExpressionsFromSqlNode(contents, testExpressions);
                }
            }
            // 处理 SetSqlNode
            else if (sqlNode instanceof SetSqlNode) {
                SetSqlNode setSqlNode = (SetSqlNode) sqlNode;
                SqlNode contents = getSetContents(setSqlNode);
                if (contents != null) {
                    extractTestExpressionsFromSqlNode(contents, testExpressions);
                }
            }
            // 处理 ForEachSqlNode
            else if (sqlNode instanceof ForEachSqlNode) {
                ForEachSqlNode forEachSqlNode = (ForEachSqlNode) sqlNode;
                SqlNode contents = getForEachContents(forEachSqlNode);
                if (contents != null) {
                    extractTestExpressionsFromSqlNode(contents, testExpressions);
                }
            }
            // 其他类型的 SqlNode（StaticTextSqlNode、TextSqlNode 等）不需要处理
            else {
                logger.debug("跳过处理 SqlNode 类型: {}", sqlNode.getClass().getName());
            }
        } catch (Exception e) {
            logger.debug("处理 SqlNode 时出错: type={}, error={}", 
                sqlNode.getClass().getName(), e.getMessage());
        }
    }

    /**
     * 使用反射安全访问 IfSqlNode 的 contents 字段
     * 
     * @param ifSqlNode IfSqlNode 实例
     * @return contents SqlNode，如果访问失败则返回 null
     */
    private SqlNode getIfSqlNodeContents(IfSqlNode ifSqlNode) {
        try {
            Field contentsField = IfSqlNode.class.getDeclaredField("contents");
            contentsField.setAccessible(true);
            return (SqlNode) contentsField.get(ifSqlNode);
        } catch (Exception e) {
            logger.debug("无法访问 IfSqlNode 的 contents 字段: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用反射安全访问 ChooseSqlNode 的 defaultSqlNode 字段
     * 
     * @param chooseSqlNode ChooseSqlNode 实例
     * @return defaultSqlNode，如果访问失败则返回 null
     */
    private SqlNode getChooseDefaultSqlNode(ChooseSqlNode chooseSqlNode) {
        try {
            Field defaultSqlNodeField = ChooseSqlNode.class.getDeclaredField("defaultSqlNode");
            defaultSqlNodeField.setAccessible(true);
            return (SqlNode) defaultSqlNodeField.get(chooseSqlNode);
        } catch (Exception e) {
            logger.debug("无法访问 ChooseSqlNode 的 defaultSqlNode 字段: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 OGNL 表达式中提取参数名
     * 例如：a != null -> a, status == 'active' -> status, user.name != null -> user
     * 
     * @param testExpression OGNL 表达式
     * @return 提取出的参数名集合
     */
    private Set<String> extractParametersFromOgnlExpression(String testExpression) {
        Set<String> parameters = new LinkedHashSet<>();
        
        if (testExpression == null || testExpression.trim().isEmpty()) {
            return parameters;
        }
        
        try {
            // OGNL 关键字，需要排除
            Set<String> keywords = new HashSet<>(Arrays.asList(
                "null", "true", "false", "this", "root", "class",
                "new", "instanceof", "and", "or", "not", "in", "not in"
            ));
            
            // 移除字符串字面量（单引号和双引号包围的内容）
            String cleaned = testExpression
                .replaceAll("'[^']*'", "")  // 移除单引号字符串
                .replaceAll("\"[^\"]*\"", ""); // 移除双引号字符串
            
            // 移除数字字面量
            cleaned = cleaned.replaceAll("\\b\\d+\\.?\\d*\\b", "");
            
            // 提取变量名：以字母或下划线开头，可以包含字母、数字、下划线和点号
            // 但点号后面必须跟字母或下划线（属性访问）
            Pattern pattern = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*\\b");
            Matcher matcher = pattern.matcher(cleaned);
            
            while (matcher.find()) {
                String candidate = matcher.group();
                
                // 排除关键字
                if (keywords.contains(candidate.toLowerCase())) {
                    continue;
                }
                
                // 如果是方法调用（包含括号），移除括号部分
                if (candidate.contains("(")) {
                    candidate = candidate.substring(0, candidate.indexOf("("));
                }
                
                // 如果是属性访问（包含点号），提取第一个部分作为参数名
                // 例如：user.name -> user, order.status -> order
                if (candidate.contains(".")) {
                    String firstPart = candidate.substring(0, candidate.indexOf("."));
                    if (!firstPart.isEmpty() && !keywords.contains(firstPart.toLowerCase())) {
                        parameters.add(firstPart);
                    }
                } else {
                    // 直接变量名
                    parameters.add(candidate);
                }
            }
            
            logger.debug("从 OGNL 表达式提取参数: expression={}, parameters={}", 
                testExpression, parameters);
        } catch (Exception e) {
            logger.warn("解析 OGNL 表达式失败: expression={}, error={}", 
                testExpression, e.getMessage());
        }
        
        return parameters;
    }

    /**
     * 保存 test 表达式作为 MapperParameter（append_only 模式）
     * 同时保存原始 test 表达式和从表达式中提取的参数
     * 
     * @param mapperId Mapper ID（格式：namespace.statementId 或 namespace）
     * @param testExpressions test 表达式集合
     */
    @Transactional
    private void saveTestExpressionsAsParameters(String mapperId, Set<String> testExpressions) {
        if (testExpressions == null || testExpressions.isEmpty()) {
            return;
        }

        try {
            // 获取现有的参数，用于检查是否已存在
            // 检查时需要考虑 parameterName 和 testExpression 的组合
            List<MapperParameter> existingParameters = mapperParameterRepository.findAllByMapperId(mapperId);
            Set<String> existingKeys = new HashSet<>();
            for (MapperParameter param : existingParameters) {
                // 使用 parameterName + testExpression 作为唯一键
                String key = param.getParameterName() + "|" + 
                    (param.getTestExpression() != null ? param.getTestExpression() : "");
                existingKeys.add(key);
            }

            int savedCount = 0;
            
            // 对每个 test 表达式，提取参数并保存
            for (String testExpression : testExpressions) {
                // 从 test 表达式中提取参数名
                Set<String> parameters = extractParametersFromOgnlExpression(testExpression);
                
                if (parameters.isEmpty()) {
                    // 如果没有提取到参数，仍然保存 test 表达式本身作为参数名
                    parameters.add(testExpression);
                }
                
                // 为每个提取出的参数创建一条记录
                for (String parameterName : parameters) {
                    String key = parameterName + "|" + testExpression;
                    
                    // 检查是否已存在（根据 parameterName 和 testExpression 的组合）
                    if (!existingKeys.contains(key)) {
                        MapperParameter parameter = new MapperParameter();
                        parameter.setMapperId(mapperId);
                        parameter.setParameterName(parameterName);
                        parameter.setParameterValue("?");
                        parameter.setTestExpression(testExpression);
                        
                        mapperParameterRepository.save(parameter);
                        savedCount++;
                        existingKeys.add(key); // 添加到已存在集合，避免重复保存
                        
                        logger.debug("保存 test 表达式和参数: mapperId={}, parameterName={}, testExpression={}", 
                            mapperId, parameterName, testExpression);
                    } else {
                        logger.debug("跳过已存在的参数: mapperId={}, parameterName={}, testExpression={}", 
                            mapperId, parameterName, testExpression);
                    }
                }
            }

            if (savedCount > 0) {
                logger.info("保存了 {} 个参数记录（包含 test 表达式）: mapperId={}", savedCount, mapperId);
            }
        } catch (Exception e) {
            logger.error("保存 test 表达式作为参数失败: mapperId={}", mapperId, e);
        }
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
            BoundSql boundSql = mappedStatement.getBoundSql(parameterObject);
            
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
                List<MapperParameter> parameters = mapperParameterRepository.findAllByMapperId(path);
                if (parameters != null && !parameters.isEmpty()) {
                    logger.debug("找到参数: mapperId={}, 匹配路径={}, 参数数量={}", mapperId, path, parameters.size());
                    // 将多个参数记录转换为Map<String,Object>
                    Map<String, Object> result = new HashMap<>();
                    for (MapperParameter param : parameters) {
                        try {
                            // 尝试将参数值解析为对象（可能是JSON字符串）
                            Object value = parseParameterValue(param.getParameterValue());
                            result.put(param.getParameterName(), value);
                        } catch (Exception e) {
                            logger.warn("解析参数值失败: mapperId={}, parameterName={}, error={}", 
                                path, param.getParameterName(), e.getMessage());
                            // 如果解析失败，直接使用字符串值
                            result.put(param.getParameterName(), param.getParameterValue());
                        }
                    }
                    return result;
                }
            } catch (Exception e) {
                logger.debug("获取存储的参数失败: mapperId={}, path={}, error={}", mapperId, path, e.getMessage());
                // 继续查找下一个层级
            }
        }
        
        logger.debug("未找到参数: mapperId={}, 已搜索路径={}", mapperId, searchPaths);
        return null;
    }
    
    /**
     * 解析参数值
     * 如果值是JSON字符串，则解析为对象；否则返回原值
     */
    private Object parseParameterValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }
        // 尝试解析为JSON
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception e) {
            // 如果不是JSON，返回原字符串
            return value;
        }
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
     * @return 保存的第一个参数实体（用于兼容旧代码）
     */
    @Transactional
    public MapperParameter saveMapperParameter(String mapperId, Map<String, Object> parameters) {
        try {
            // 先删除该mapperId的所有现有参数
            mapperParameterRepository.deleteAllByMapperId(mapperId);
            
            // 将参数Map拆分为多个MapperParameter记录
            MapperParameter firstParameter = null;
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                MapperParameter parameter = new MapperParameter();
                parameter.setMapperId(mapperId);
                parameter.setParameterName(entry.getKey());
                
                // 将参数值转换为字符串
                // 如果是复杂对象，序列化为JSON字符串；否则直接转为字符串
                String parameterValue;
                Object value = entry.getValue();
                if (value == null) {
                    parameterValue = "";
                } else if (value instanceof String) {
                    parameterValue = (String) value;
                } else {
                    // 复杂对象序列化为JSON
                    parameterValue = objectMapper.writeValueAsString(value);
                }
                parameter.setParameterValue(parameterValue);
                
                MapperParameter saved = mapperParameterRepository.save(parameter);
                if (firstParameter == null) {
                    firstParameter = saved;
                }
            }
            
            if (firstParameter == null) {
                throw new RuntimeException("参数Map为空，无法保存");
            }
            
            return firstParameter;
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
        mapperParameterRepository.deleteAllByMapperId(mapperId);
    }

    /**
     * 获取所有不重复的Mapper命名空间
     * 
     * @return 所有不重复的namespace列表
     */
    public List<String> getAllNamespaces() {
        return parsedSqlQueryRepository.findAllDistinctNamespaces();
    }

    /**
     * 根据命名空间获取所有查询
     * 
     * @param namespace Mapper命名空间
     * @return 查询列表
     */
    public List<Map<String, Object>> getQueriesByNamespace(String namespace) {
        List<ParsedSqlQuery> queries = parsedSqlQueryRepository.findByMapperNamespace(namespace);
        
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
            map.put("createdAt", query.getCreatedAt());
            result.add(map);
        }
        
        return result;
    }

    /**
     * 根据命名空间获取所有参数
     * 
     * @param namespace Mapper命名空间
     * @return 参数列表
     */
    public List<MapperParameter> getParametersByNamespace(String namespace) {
        List<MapperParameter> allParameters = mapperParameterRepository.findAll();
        
        // 过滤出以指定namespace开头的参数（mapperId格式：namespace.statementId）
        List<MapperParameter> result = new ArrayList<>();
        String namespacePrefix = namespace + ".";
        for (MapperParameter param : allParameters) {
            if (param.getMapperId().startsWith(namespacePrefix) || param.getMapperId().equals(namespace)) {
                result.add(param);
            }
        }
        
        return result;
    }

    /**
     * 更新SQL查询
     * 
     * @param id 查询ID
     * @param queryData 查询数据
     * @return 更新后的查询
     */
    @Transactional
    public ParsedSqlQuery updateQuery(Long id, Map<String, Object> queryData) {
        ParsedSqlQuery query = parsedSqlQueryRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("查询不存在: id=" + id));
        
        // 更新可修改的字段（排除id和createdAt）
        if (queryData.containsKey("mapperNamespace")) {
            query.setMapperNamespace((String) queryData.get("mapperNamespace"));
        }
        if (queryData.containsKey("statementId")) {
            query.setStatementId((String) queryData.get("statementId"));
        }
        if (queryData.containsKey("queryType")) {
            query.setQueryType((String) queryData.get("queryType"));
        }
        if (queryData.containsKey("sql")) {
            query.setSql((String) queryData.get("sql"));
        }
        if (queryData.containsKey("originalSqlFragment")) {
            query.setOriginalSqlFragment((String) queryData.get("originalSqlFragment"));
        }
        if (queryData.containsKey("tableName")) {
            query.setTableName((String) queryData.get("tableName"));
        }
        if (queryData.containsKey("dynamicConditions")) {
            query.setDynamicConditions((String) queryData.get("dynamicConditions"));
        }
        
        return parsedSqlQueryRepository.save(query);
    }

    /**
     * 删除SQL查询
     * 
     * @param id 查询ID
     */
    @Transactional
    public void deleteQuery(Long id) {
        if (!parsedSqlQueryRepository.existsById(id)) {
            throw new RuntimeException("查询不存在: id=" + id);
        }
        parsedSqlQueryRepository.deleteById(id);
    }

    /**
     * 批量删除SQL查询
     * 
     * @param ids 查询ID列表
     */
    @Transactional
    public void deleteQueries(List<Long> ids) {
        parsedSqlQueryRepository.deleteAllById(ids);
    }

    /**
     * 更新Mapper参数
     * 
     * @param id 参数ID
     * @param parameterData 参数数据
     * @return 更新后的参数
     */
    @Transactional
    public MapperParameter updateParameter(Long id, Map<String, Object> parameterData) {
        MapperParameter parameter = mapperParameterRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("参数不存在: id=" + id));
        
        // 更新可修改的字段（排除id和createdAt）
        if (parameterData.containsKey("mapperId")) {
            parameter.setMapperId((String) parameterData.get("mapperId"));
        }
        if (parameterData.containsKey("parameterName")) {
            parameter.setParameterName((String) parameterData.get("parameterName"));
        }
        if (parameterData.containsKey("parameterValue")) {
            parameter.setParameterValue((String) parameterData.get("parameterValue"));
        }
        if (parameterData.containsKey("testExpression")) {
            parameter.setTestExpression((String) parameterData.get("testExpression"));
        }
        
        return mapperParameterRepository.save(parameter);
    }

    /**
     * 删除Mapper参数
     * 
     * @param id 参数ID
     */
    @Transactional
    public void deleteParameterById(Long id) {
        if (!mapperParameterRepository.existsById(id)) {
            throw new RuntimeException("参数不存在: id=" + id);
        }
        mapperParameterRepository.deleteById(id);
    }

    /**
     * 批量删除Mapper参数
     * 
     * @param ids 参数ID列表
     */
    @Transactional
    public void deleteParameters(List<Long> ids) {
        mapperParameterRepository.deleteAllById(ids);
    }

    /**
     * 从应用上下文获取MyBatis Configuration
     * 
     * @return Configuration，如果不存在则返回null
     */
    private Configuration getConfigurationFromApplicationContext() {
        if (applicationContext == null) {
            logger.warn("ApplicationContext未注入，无法获取MyBatis Configuration");
            return null;
        }

        try {
            // 尝试获取SqlSessionFactory Bean
            Map<String, SqlSessionFactory> sqlSessionFactoryMap = 
                applicationContext.getBeansOfType(SqlSessionFactory.class);
            
            if (sqlSessionFactoryMap.isEmpty()) {
                logger.warn("未找到SqlSessionFactory Bean");
                return null;
            }

            // 如果有多个，返回第一个（通常只有一个）
            SqlSessionFactory sqlSessionFactory = sqlSessionFactoryMap.values().iterator().next();
            return sqlSessionFactory.getConfiguration();
        } catch (Exception e) {
            logger.error("获取MyBatis Configuration失败", e);
            return null;
        }
    }

    /**
     * 从应用上下文中查找包含指定namespace的Configuration
     * 
     * @param namespace 命名空间
     * @return Configuration，如果不存在则返回null
     */
    private Configuration findConfigurationByNamespace(String namespace) {
        Configuration config = getConfigurationFromApplicationContext();
        if (config == null) {
            return null;
        }

        // 检查Configuration中是否包含该namespace的MappedStatement
        Collection<String> statementIds = config.getMappedStatementNames();
        for (String statementId : statementIds) {
            if (statementId.startsWith(namespace + ".")) {
                logger.info("找到包含namespace {} 的Configuration", namespace);
                return config;
            }
        }

        logger.warn("Configuration中未找到namespace: {}", namespace);
        return null;
    }

    /**
     * 从Configuration提取Mapper参数（不解析SQL）
     * 
     * @param configuration MyBatis Configuration
     * @param namespace Mapper命名空间
     * @return 提取的参数列表
     */
    @Transactional
    public List<MapperParameter> extractMapperParametersFromConfiguration(Configuration configuration, String namespace) {
        logger.info("开始从Configuration提取参数: namespace={}", namespace);

        List<MapperParameter> extractedParameters = new ArrayList<>();
        Collection<String> statementIds = configuration.getMappedStatementNames();

        for (String statementId : statementIds) {
            // 只处理当前命名空间的语句
            if (!statementId.startsWith(namespace + ".")) {
                continue;
            }

            MappedStatement mappedStatement = configuration.getMappedStatement(statementId);
            
            // 只处理SELECT查询
            if (mappedStatement.getSqlCommandType() != SqlCommandType.SELECT) {
                continue;
            }

            // 提取 test 表达式
            Set<String> testExpressions = extractTestExpressionsFromSqlSource(mappedStatement, statementId);
            
            if (!testExpressions.isEmpty()) {
                // 保存到完整路径（namespace.statementId）
                saveTestExpressionsAsParameters(statementId, testExpressions);
                
                // 保存到命名空间层级（namespace）
                saveTestExpressionsAsParameters(namespace, testExpressions);
                
                // 收集参数用于返回
                List<MapperParameter> statementParams = mapperParameterRepository.findAllByMapperId(statementId);
                extractedParameters.addAll(statementParams);
                
                List<MapperParameter> namespaceParams = mapperParameterRepository.findAllByMapperId(namespace);
                for (MapperParameter param : namespaceParams) {
                    // 避免重复添加
                    if (extractedParameters.stream().noneMatch(p -> 
                        p.getParameterName().equals(param.getParameterName()) && 
                        Objects.equals(p.getTestExpression(), param.getTestExpression()))) {
                        extractedParameters.add(param);
                    }
                }
            }
        }

        logger.info("从Configuration提取完成，共提取 {} 个参数: namespace={}", extractedParameters.size(), namespace);
        return extractedParameters;
    }

    /**
     * 基于namespace解析Mapper（从应用上下文获取Configuration）
     * 
     * @param namespace Mapper命名空间
     * @return 解析结果，包含needEdit标志和参数列表或查询列表
     */
    @Transactional
    public Map<String, Object> parseMapperByNamespace(String namespace) {
        logger.info("开始基于namespace解析: namespace={}", namespace);

        Map<String, Object> result = new HashMap<>();

        // 1. 从应用上下文获取Configuration
        Configuration configuration = findConfigurationByNamespace(namespace);
        if (configuration == null) {
            result.put("success", false);
            result.put("error", "未找到包含该namespace的MyBatis Configuration，请确保应用已正确配置MyBatis");
            return result;
        }

        // 2. 检查MapperParameter是否存在
        List<MapperParameter> existingParameters = mapperParameterRepository.findAllByMapperId(namespace);
        
        // 也检查statement级别的参数
        Collection<String> statementIds = configuration.getMappedStatementNames();
        boolean hasAnyParameters = !existingParameters.isEmpty();
        if (!hasAnyParameters) {
            for (String statementId : statementIds) {
                if (statementId.startsWith(namespace + ".")) {
                    List<MapperParameter> statementParams = mapperParameterRepository.findAllByMapperId(statementId);
                    if (!statementParams.isEmpty()) {
                        hasAnyParameters = true;
                        break;
                    }
                }
            }
        }

        // 3. 如果参数不存在，只提取参数并返回
        if (!hasAnyParameters) {
            logger.info("MapperParameter不存在，提取参数: namespace={}", namespace);
            List<MapperParameter> parameters = extractMapperParametersFromConfiguration(configuration, namespace);
            
            result.put("success", true);
            result.put("needEdit", true);
            result.put("parameters", parameters);
            result.put("message", "参数已提取，请编辑参数值后继续解析SQL");
            return result;
        }

        // 4. 如果参数存在，直接解析SQL
        logger.info("MapperParameter已存在，直接解析SQL: namespace={}", namespace);
        
        // 删除旧数据
        parsedSqlQueryRepository.deleteByMapperNamespace(namespace);

        // 从Configuration中提取所有MappedStatement
        Map<String, Object> extractionResult = extractQueriesFromConfiguration(configuration, namespace);
        @SuppressWarnings("unchecked")
        List<ParsedSqlQuery> queries = (List<ParsedSqlQuery>) extractionResult.get("queries");

        // 保存所有查询
        parsedSqlQueryRepository.saveAll(queries);

        result.put("success", true);
        result.put("needEdit", false);
        result.put("queryCount", queries.size());
        result.put("queries", queries);
        result.put("message", "成功解析 " + queries.size() + " 个SQL查询");
        
        logger.info("解析完成，共解析出 {} 个SQL查询: namespace={}", queries.size(), namespace);
        return result;
    }

    /**
     * 刷新指定namespace的SQL解析结果
     * 
     * @param namespace Mapper命名空间
     * @return 刷新后的解析结果
     */
    @Transactional
    public Map<String, Object> refreshSqlQueriesByNamespace(String namespace) {
        logger.info("开始刷新SQL解析结果: namespace={}", namespace);

        Map<String, Object> result = new HashMap<>();

        // 1. 从应用上下文获取Configuration
        Configuration configuration = findConfigurationByNamespace(namespace);
        if (configuration == null) {
            result.put("success", false);
            result.put("error", "未找到包含该namespace的MyBatis Configuration");
            return result;
        }

        // 2. 删除旧数据
        parsedSqlQueryRepository.deleteByMapperNamespace(namespace);

        // 3. 从Configuration中重新提取SQL（使用最新的MapperParameter）
        Map<String, Object> extractionResult = extractQueriesFromConfiguration(configuration, namespace);
        @SuppressWarnings("unchecked")
        List<ParsedSqlQuery> queries = (List<ParsedSqlQuery>) extractionResult.get("queries");

        // 4. 保存所有查询
        parsedSqlQueryRepository.saveAll(queries);

        result.put("success", true);
        result.put("queryCount", queries.size());
        result.put("queries", queries);
        result.put("message", "成功刷新 " + queries.size() + " 个SQL查询");
        
        logger.info("刷新完成，共解析出 {} 个SQL查询: namespace={}", queries.size(), namespace);
        return result;
    }

}
