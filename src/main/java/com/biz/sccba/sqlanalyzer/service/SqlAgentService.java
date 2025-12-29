package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.*;
import com.biz.sccba.sqlanalyzer.model.dto.ColumnStatisticsDTO;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Comparator;

/**
 * SQL Agent 服务 - 双阶段验证流程
 * Stage 1: Predictor (LLM 预测)
 * Stage 2: Verifier (实际 EXPLAIN 验证) - 支持多场景测试
 */
@Service
public class SqlAgentService {

    private static final Logger logger = LoggerFactory.getLogger(SqlAgentService.class);

    @Autowired
    private SqlExecutionPlanService executionPlanService;

    @Autowired
    private LlmManagerService llmManagerService;

    @Autowired
    private PromptTemplateManagerService promptTemplateManagerService;

    private final ObjectMapper objectMapper;
    
    /**
     * 构造函数 - 配置宽松的 ObjectMapper
     */
    public SqlAgentService() {
        this.objectMapper = new ObjectMapper();
        configureObjectMapper(this.objectMapper);
    }
    
    /**
     * 配置 ObjectMapper 以提高容错能力
     */
    @SuppressWarnings("deprecation")
    private void configureObjectMapper(ObjectMapper mapper) {
        // 允许未转义的控制字符（如 \n \t）
        // 注意：这个 feature 在新版本中已 deprecated，但为了兼容性仍然保留
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        
        // 允许反斜杠转义任何字符（即使不是标准的转义字符）
        // 注意：这个 feature 在新版本中已 deprecated，但为了兼容性仍然保留
        mapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        
        // 允许 JSON 中的注释
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        
        // 忽略未知属性（LLM 可能返回额外字段）
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        logger.info("ObjectMapper 已配置为宽松模式，提高 LLM 响应解析容错能力");
    }

    /**
     * 场景验证结果
     */
    @lombok.Data
    public static class ScenarioVerification {
        private String scenarioName;
        private String filledSql;
        private ExecutionPlan executionPlan;
        private Map<String, Object> parameters;
        private String description;
    }

    /**
     * 执行 SQL 风险评估双阶段验证
     * @param request 包含 SQL、数据源名称、LLM 名称的请求
     * @return 风险评估响应
     */
    public SqlRiskAssessmentResponse analyze(SqlAgentRequest request) {
        logger.info("开始 SQL 风险评估双阶段验证流程...");
        long startTime = System.currentTimeMillis();

        try {
            // === Stage 1: Predictor ===
            logger.info("Stage 1: Predictor - 使用 LLM 预测 SQL 风险");
            
            // 1.1 获取直方图数据
            List<ColumnStatisticsDTO> histograms = fetchHistogramData(request.getSql(), request.getDatasourceName());
            logger.info("获取到 {} 个列的直方图数据", histograms.size());
            
            // 1.2 调用 LLM 进行预测（包含表结构和索引信息）
            SqlRiskPrediction prediction = callPredictorLLM(request.getSql(), histograms, 
                    request.getLlmName(), request.getDatasourceName());
            logger.info("LLM 预测完成，风险等级: {}", prediction.getRiskLevel());
            
            // === Stage 2: Verifier - 多场景测试 ===
            logger.info("Stage 2: Verifier - 使用 LLM 生成多场景测试 SQL");
            
            // 2.1 调用 LLM 生成多个测试场景的 SQL（包含表结构和索引信息）
            SqlFillingResult fillingResult = null;
            try {
                fillingResult = callSqlFillerLLM(request.getSql(), histograms, 
                        request.getLlmName(), request.getDatasourceName());
                logger.info("LLM 生成了 {} 个测试场景", fillingResult.getScenarios().size());
            } catch (Exception e) {
                logger.warn("LLM 参数填充失败，使用预测结果的参数进行降级: {}", e.getMessage());
                // 降级：使用简单填充
                String filledSql = fillSqlWithSuggestedParams(request.getSql(), prediction.getSuggestedParameters());
                fillingResult = createFallbackFillingResult(request.getSql(), filledSql, prediction.getSuggestedParameters());
            }
            
            // 2.2 为所有场景执行 EXPLAIN
            List<ScenarioVerification> verifications = verifyAllScenarios(
                    fillingResult.getScenarios(), request.getDatasourceName());
            logger.info("完成 {} 个场景的 EXPLAIN 验证", verifications.size());
            
            if (verifications.isEmpty()) {
                logger.warn("所有场景的 EXPLAIN 都失败，将降级返回预测结果");
                return buildDegradedResponse(request.getSql(), fillingResult, histograms, prediction,
                        System.currentTimeMillis() - startTime);
            }
            
            // === Intelligent Comparison - 多场景对比 ===
            logger.info("执行智能对比分析（多场景）...");
            
            boolean needsRefinement = callComparisonLLMMultiScenario(prediction, verifications, request.getLlmName());
            logger.info("对比分析完成，需要修正: {}", needsRefinement);
            
            SqlRiskPrediction finalResult = prediction;
            boolean refinementApplied = false;
            
            if (needsRefinement) {
                logger.info("执行 LLM 修正...");
                try {
                    finalResult = callRefinementLLMMultiScenario(prediction, verifications, histograms, request.getLlmName());
                    refinementApplied = true;
                    logger.info("LLM 修正完成，新的风险等级: {}", finalResult.getRiskLevel());
                } catch (Exception e) {
                    logger.warn("LLM 修正失败，使用原始预测结果: {}", e.getMessage());
                }
            }
            
            // === Build Response ===
            return buildResponseMultiScenario(request.getSql(), fillingResult, histograms, prediction, 
                    verifications, finalResult, refinementApplied, System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            logger.error("SQL 风险评估失败", e);
            throw new RuntimeException("SQL 风险评估失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取 SQL 中 WHERE 条件涉及的列的直方图数据
     */
    private List<ColumnStatisticsDTO> fetchHistogramData(String sql, String datasourceName) {
        try {
            return executionPlanService.getHistogramDataForSql(sql, datasourceName);
        } catch (Exception e) {
            logger.warn("获取直方图数据失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Stage 1: 调用 LLM Predictor 进行风险预测
     */
    private SqlRiskPrediction callPredictorLLM(String sql, List<ColumnStatisticsDTO> histograms, String llmName) {
        return callPredictorLLM(sql, histograms, llmName, null);
    }
    
    /**
     * Stage 1: 调用 LLM Predictor 进行风险预测（带数据源信息）
     */
    private SqlRiskPrediction callPredictorLLM(String sql, List<ColumnStatisticsDTO> histograms, 
                                                String llmName, String datasourceName) {
        try {
            // 获取 ChatClient
            ChatClient chatClient = llmManagerService.getChatClient(llmName);
            
            // 获取 Prompt 模板
            String templateContent = promptTemplateManagerService.getTemplateContent(
                    PromptTemplateManagerService.TYPE_SQL_RISK_ASSESSMENT);
            
            // 准备直方图数据摘要
            String histogramSummary = formatHistogramData(histograms);
            
            // 获取表结构和索引信息
            String tableStructureInfo = "";
            if (datasourceName != null) {
                try {
                    List<TableStructure> tableStructures = executionPlanService.getTableStructures(sql, datasourceName);
                    tableStructureInfo = formatTableStructures(tableStructures);
                    logger.info("获取到 {} 个表的结构和索引信息", tableStructures.size());
                } catch (Exception e) {
                    logger.warn("获取表结构和索引信息失败: {}", e.getMessage());
                    tableStructureInfo = "无法获取表结构信息";
                }
            } else {
                tableStructureInfo = "未提供数据源信息";
            }
            
            // 直接进行字符串替换（避免 StringTemplate 解析问题）
            String prompt = templateContent
                    .replace("{sql}", sql)
                    .replace("{histogram_data}", histogramSummary)
                    .replace("{table_structure}", tableStructureInfo);
            
            // 调用 LLM
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            logger.debug("LLM Predictor 原始响应: {}", response);
            
            // 解析 JSON 响应
            return parseJsonResponse(response, SqlRiskPrediction.class);
            
        } catch (Exception e) {
            logger.error("LLM Predictor 调用失败", e);
            throw new RuntimeException("LLM 预测失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 调用 LLM 生成多个测试场景的填充 SQL
     */
    private SqlFillingResult callSqlFillerLLM(String sql, List<ColumnStatisticsDTO> histograms, String llmName) {
        return callSqlFillerLLM(sql, histograms, llmName, null);
    }
    
    /**
     * 调用 LLM 生成多个测试场景的填充 SQL（带数据源信息）
     */
    private SqlFillingResult callSqlFillerLLM(String sql, List<ColumnStatisticsDTO> histograms, 
                                               String llmName, String datasourceName) {
        try {
            ChatClient chatClient = llmManagerService.getChatClient(llmName);
            
            String templateContent = promptTemplateManagerService.getTemplateContent(
                    PromptTemplateManagerService.TYPE_SQL_PARAMETER_FILLING);
            
            String histogramSummary = formatHistogramData(histograms);
            
            // 获取表结构和索引信息
            String tableStructureInfo = "";
            if (datasourceName != null) {
                try {
                    List<TableStructure> tableStructures = executionPlanService.getTableStructures(sql, datasourceName);
                    tableStructureInfo = formatTableStructures(tableStructures);
                } catch (Exception e) {
                    logger.warn("获取表结构和索引信息失败: {}", e.getMessage());
                    tableStructureInfo = "无法获取表结构信息";
                }
            } else {
                tableStructureInfo = "未提供数据源信息";
            }
            
            // 直接进行字符串替换（避免 StringTemplate 解析问题）
            String prompt = templateContent
                    .replace("{sql}", sql)
                    .replace("{histogram_data}", histogramSummary)
                    .replace("{table_structure}", tableStructureInfo);
            
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            logger.debug("LLM SQL Filler 原始响应: {}", response);
            
            return parseJsonResponse(response, SqlFillingResult.class);
            
        } catch (Exception e) {
            logger.error("LLM SQL 参数填充失败", e);
            throw new RuntimeException("LLM SQL 参数填充失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量验证所有场景
     */
    private List<ScenarioVerification> verifyAllScenarios(List<FilledSqlScenario> scenarios, String datasourceName) {
        List<ScenarioVerification> verifications = new ArrayList<>();
        
        for (FilledSqlScenario scenario : scenarios) {
            try {
                logger.info("验证场景: {}", scenario.getScenarioName());
                
                ExecutionPlan plan = executionPlanService.getExecutionPlan(
                        scenario.getFilledSql(), datasourceName);
                
                ScenarioVerification verification = new ScenarioVerification();
                verification.setScenarioName(scenario.getScenarioName());
                verification.setFilledSql(scenario.getFilledSql());
                verification.setExecutionPlan(plan);
                verification.setParameters(scenario.getParameters());
                verification.setDescription(scenario.getDescription());
                
                verifications.add(verification);
                
            } catch (Exception e) {
                logger.warn("场景 {} 的 EXPLAIN 失败: {}", scenario.getScenarioName(), e.getMessage());
                // 继续处理其他场景
            }
        }
        
        return verifications;
    }
    
    /**
     * 创建降级的 FillingResult（当 LLM 填充失败时）
     */
    private SqlFillingResult createFallbackFillingResult(String originalSql, String filledSql, 
                                                          Map<String, Object> parameters) {
        SqlFillingResult result = new SqlFillingResult();
        result.setOriginalSql(originalSql);
        result.setReasoning("LLM 参数填充失败，使用预测结果的建议参数进行简单填充");
        
        FilledSqlScenario scenario = new FilledSqlScenario();
        scenario.setScenarioName("降级场景");
        scenario.setFilledSql(filledSql);
        scenario.setParameters(parameters);
        scenario.setDescription("使用预测参数的简单填充");
        
        result.setScenarios(Collections.singletonList(scenario));
        
        return result;
    }
    
    /**
     * 将预测的参数代入 SQL（保留作为降级方案）
     */
    private String fillSqlWithSuggestedParams(String sql, Map<String, Object> suggestedParams) {
        if (suggestedParams == null || suggestedParams.isEmpty()) {
            return sql;
        }
        
        String filledSql = sql;
        
        // 替换 ? 占位符
        for (Map.Entry<String, Object> entry : suggestedParams.entrySet()) {
            Object value = entry.getValue();
            String valueStr;
            
            if (value instanceof String) {
                valueStr = "'" + value + "'";
            } else if (value == null) {
                valueStr = "NULL";
            } else {
                valueStr = value.toString();
            }
            
            // 替换第一个 ? 占位符
            filledSql = filledSql.replaceFirst("\\?", valueStr);
        }
        
        // 如果没有 ? 占位符，尝试替换命名参数（如 :param 或 #{param}）
        for (Map.Entry<String, Object> entry : suggestedParams.entrySet()) {
            String paramName = entry.getKey();
            Object value = entry.getValue();
            String valueStr;
            
            if (value instanceof String) {
                valueStr = "'" + value + "'";
            } else if (value == null) {
                valueStr = "NULL";
            } else {
                valueStr = value.toString();
            }
            
            // 替换 :paramName 格式
            filledSql = filledSql.replaceAll(":" + paramName + "\\b", valueStr);
            // 替换 #{paramName} 格式
            filledSql = filledSql.replaceAll("#\\{" + paramName + "\\}", valueStr);
        }
        
        return filledSql;
    }
    
    /**
     * 智能对比（多场景）：调用 LLM 判断预测与实际是否需要修正
     */
    private boolean callComparisonLLMMultiScenario(SqlRiskPrediction prediction, 
                                                     List<ScenarioVerification> verifications, 
                                                     String llmName) {
        try {
            ChatClient chatClient = llmManagerService.getChatClient(llmName);
            
            String templateContent = promptTemplateManagerService.getTemplateContent(
                    PromptTemplateManagerService.TYPE_SQL_RISK_COMPARISON);
            
            String predictionJson = objectMapper.writeValueAsString(prediction);
            String verificationsSummary = formatVerifications(verifications);
            
            // 直接进行字符串替换（避免 StringTemplate 解析问题）
            String prompt = templateContent
                    .replace("{prediction}", predictionJson)
                    .replace("{actual_explain}", verificationsSummary);
            
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            logger.debug("LLM Comparison (Multi-Scenario) 原始响应: {}", response);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> comparisonResult = parseJsonResponse(response, Map.class);
            return (Boolean) comparisonResult.getOrDefault("needsRefinement", false);
            
        } catch (Exception e) {
            logger.warn("LLM 对比分析失败，使用规则判断: {}", e.getMessage());
            return needsRefinementByRulesMultiScenario(prediction, verifications);
        }
    }
    
    /**
     * 规则判断是否需要修正（多场景降级方案）
     */
    private boolean needsRefinementByRulesMultiScenario(SqlRiskPrediction prediction, 
                                                          List<ScenarioVerification> verifications) {
        // 检查最坏情况的场景
        for (ScenarioVerification verification : verifications) {
            if (needsRefinementByRules(prediction, verification.getExecutionPlan())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * LLM 修正（多场景）：基于实际 EXPLAIN 结果修正预测
     */
    private SqlRiskPrediction callRefinementLLMMultiScenario(SqlRiskPrediction originalPrediction,
                                                              List<ScenarioVerification> verifications,
                                                              List<ColumnStatisticsDTO> histograms,
                                                              String llmName) {
        try {
            ChatClient chatClient = llmManagerService.getChatClient(llmName);
            
            String templateContent = promptTemplateManagerService.getTemplateContent(
                    PromptTemplateManagerService.TYPE_SQL_RISK_REFINEMENT);
            
            String originalPredictionJson = objectMapper.writeValueAsString(originalPrediction);
            String verificationsSummary = formatVerifications(verifications);
            String histogramSummary = formatHistogramData(histograms);
            String deviationDetails = buildDeviationDetailsMultiScenario(originalPrediction, verifications);
            
            // 直接进行字符串替换（避免 StringTemplate 解析问题）
            String prompt = templateContent
                    .replace("{original_prediction}", originalPredictionJson)
                    .replace("{actual_explain}", verificationsSummary)
                    .replace("{histogram_data}", histogramSummary)
                    .replace("{deviation_details}", deviationDetails);
            
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            logger.debug("LLM Refinement (Multi-Scenario) 原始响应: {}", response);
            
            return parseJsonResponse(response, SqlRiskPrediction.class);
            
        } catch (Exception e) {
            logger.error("LLM 修正失败", e);
            throw new RuntimeException("LLM 修正失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 格式化多个验证结果
     */
    private String formatVerifications(List<ScenarioVerification> verifications) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("共 %d 个测试场景：\n\n", verifications.size()));
        
        for (int i = 0; i < verifications.size(); i++) {
            ScenarioVerification v = verifications.get(i);
            sb.append(String.format("=== 场景 %d: %s ===\n", i + 1, v.getScenarioName()));
            sb.append(String.format("SQL: %s\n", v.getFilledSql()));
            sb.append(String.format("参数: %s\n", v.getParameters()));
            sb.append(formatExecutionPlan(v.getExecutionPlan()));
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 构建多场景偏差详情
     */
    private String buildDeviationDetailsMultiScenario(SqlRiskPrediction prediction, 
                                                       List<ScenarioVerification> verifications) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("预测结果: 风险等级=%s, 预估扫描行数=%d\n\n", 
                prediction.getRiskLevel(), prediction.getEstimatedRowsExamined()));
        
        for (int i = 0; i < verifications.size(); i++) {
            ScenarioVerification v = verifications.get(i);
            sb.append(String.format("场景 %d (%s):\n", i + 1, v.getScenarioName()));
            sb.append(buildDeviationDetails(prediction, v.getExecutionPlan()));
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 智能对比：调用 LLM 判断预测与实际是否需要修正（单场景，保留用于兼容）
     */
    private boolean callComparisonLLM(SqlRiskPrediction prediction, ExecutionPlan actualPlan, String llmName) {
        try {
            ChatClient chatClient = llmManagerService.getChatClient(llmName);
            
            String templateContent = promptTemplateManagerService.getTemplateContent(
                    PromptTemplateManagerService.TYPE_SQL_RISK_COMPARISON);
            
            // 准备对比数据
            String predictionJson = objectMapper.writeValueAsString(prediction);
            String actualPlanSummary = formatExecutionPlan(actualPlan);
            
            // 直接进行字符串替换（避免 StringTemplate 解析问题）
            String prompt = templateContent
                    .replace("{prediction}", predictionJson)
                    .replace("{actual_explain}", actualPlanSummary);
            
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            logger.debug("LLM Comparison 原始响应: {}", response);
            
            // 解析响应
            @SuppressWarnings("unchecked")
            Map<String, Object> comparisonResult = parseJsonResponse(response, Map.class);
            return (Boolean) comparisonResult.getOrDefault("needsRefinement", false);
            
        } catch (Exception e) {
            logger.warn("LLM 对比分析失败，使用规则判断: {}", e.getMessage());
            // 降级：使用规则判断
            return needsRefinementByRules(prediction, actualPlan);
        }
    }
    
    /**
     * 规则判断是否需要修正（降级方案）
     */
    private boolean needsRefinementByRules(SqlRiskPrediction prediction, ExecutionPlan actualPlan) {
        if (actualPlan == null || actualPlan.getQueryBlock() == null) {
            return false;
        }
        
        ExecutionPlan.TableInfo tableInfo = actualPlan.getQueryBlock().getTable();
        if (tableInfo == null) {
            return false;
        }
        
        // 规则1: 扫描行数偏差超过50%或1000行
        Long actualRows = tableInfo.getRowsExaminedPerScan();
        Long predictedRows = prediction.getEstimatedRowsExamined();
        if (actualRows != null && predictedRows != null && predictedRows > 0) {
            long deviation = Math.abs(actualRows - predictedRows);
            double deviationPercent = (double) deviation / predictedRows;
            if (deviationPercent > 0.5 || deviation > 1000) {
                logger.info("规则判断：扫描行数偏差过大，需要修正");
                return true;
            }
        }
        
        // 规则2: 索引使用不一致
        boolean actualUsesIndex = tableInfo.getKey() != null && !tableInfo.getKey().isEmpty();
        Boolean predictedUsesIndex = prediction.getExpectedIndexUsage();
        if (predictedUsesIndex != null && actualUsesIndex != predictedUsesIndex) {
            logger.info("规则判断：索引使用不一致，需要修正");
            return true;
        }
        
        // 规则3: 访问类型不一致（预测高效但实际低效）
        String actualAccessType = tableInfo.getAccessType();
        String predictedAccessType = prediction.getExpectedAccessType();
        if (actualAccessType != null && predictedAccessType != null) {
            boolean predictedEfficient = isPredictedEfficient(predictedAccessType);
            boolean actualEfficient = isActualEfficient(actualAccessType);
            if (predictedEfficient && !actualEfficient) {
                logger.info("规则判断：访问类型不一致（预测高效但实际低效），需要修正");
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isPredictedEfficient(String accessType) {
        return "ref".equalsIgnoreCase(accessType) || 
               "eq_ref".equalsIgnoreCase(accessType) || 
               "const".equalsIgnoreCase(accessType);
    }
    
    private boolean isActualEfficient(String accessType) {
        return !"ALL".equalsIgnoreCase(accessType) && !"index".equalsIgnoreCase(accessType);
    }
    
    /**
     * LLM 修正：基于实际 EXPLAIN 结果修正预测
     */
    private SqlRiskPrediction callRefinementLLM(SqlRiskPrediction originalPrediction, 
                                                 ExecutionPlan actualPlan,
                                                 List<ColumnStatisticsDTO> histograms,
                                                 String llmName) {
        try {
            ChatClient chatClient = llmManagerService.getChatClient(llmName);
            
            String templateContent = promptTemplateManagerService.getTemplateContent(
                    PromptTemplateManagerService.TYPE_SQL_RISK_REFINEMENT);
            
            // 准备数据
            String originalPredictionJson = objectMapper.writeValueAsString(originalPrediction);
            String actualPlanSummary = formatExecutionPlan(actualPlan);
            String histogramSummary = formatHistogramData(histograms);
            String deviationDetails = buildDeviationDetails(originalPrediction, actualPlan);
            
            // 直接进行字符串替换（避免 StringTemplate 解析问题）
            String prompt = templateContent
                    .replace("{original_prediction}", originalPredictionJson)
                    .replace("{actual_explain}", actualPlanSummary)
                    .replace("{histogram_data}", histogramSummary)
                    .replace("{deviation_details}", deviationDetails);
            
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            logger.debug("LLM Refinement 原始响应: {}", response);
            
            return parseJsonResponse(response, SqlRiskPrediction.class);
            
        } catch (Exception e) {
            logger.error("LLM 修正失败", e);
            throw new RuntimeException("LLM 修正失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 格式化直方图数据为可读文本
     */
    private String formatHistogramData(List<ColumnStatisticsDTO> histograms) {
        if (histograms == null || histograms.isEmpty()) {
            return "无直方图数据";
        }
        
        StringBuilder sb = new StringBuilder();
        for (ColumnStatisticsDTO hist : histograms) {
            sb.append(String.format("表: %s, 列: %s\n", hist.getTableName(), hist.getColumnName()));
            sb.append(String.format("  类型: %s, 桶数: %d\n", hist.getHistogramType(), hist.getBucketCount()));
            sb.append(String.format("  范围: [%s, %s]\n", hist.getMinValue(), hist.getMaxValue()));
            if (hist.getSampleValues() != null && !hist.getSampleValues().isEmpty()) {
                sb.append(String.format("  采样值数量: %d\n", hist.getSampleValues().size()));
                // 只显示前5个采样值
                List<Object> samples = hist.getSampleValues().subList(0, 
                        Math.min(5, hist.getSampleValues().size()));
                sb.append(String.format("  示例值: %s\n", samples));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 格式化表结构和索引信息为可读文本
     */
    private String formatTableStructures(List<TableStructure> tableStructures) {
        if (tableStructures == null || tableStructures.isEmpty()) {
            return "无表结构信息";
        }
        
        StringBuilder sb = new StringBuilder();
        for (TableStructure structure : tableStructures) {
            sb.append(String.format("=== 表: %s ===\n", structure.getTableName()));
            
            // 表统计信息
            if (structure.getStatistics() != null) {
                TableStructure.TableStatistics stats = structure.getStatistics();
                sb.append(String.format("总行数: %d, 数据大小: %d bytes, 索引大小: %d bytes, 引擎: %s\n",
                        stats.getRows(), stats.getDataLength(), stats.getIndexLength(), stats.getEngine()));
            }
            
            // 列信息
            sb.append("\n列信息:\n");
            if (structure.getColumns() != null && !structure.getColumns().isEmpty()) {
                for (TableStructure.ColumnInfo column : structure.getColumns()) {
                    sb.append(String.format("  - %s: %s%s%s%s\n",
                            column.getColumnName(),
                            column.getDataType(),
                            "NO".equals(column.getIsNullable()) ? " NOT NULL" : "",
                            "PRI".equals(column.getColumnKey()) ? " PRIMARY KEY" : 
                                "UNI".equals(column.getColumnKey()) ? " UNIQUE" : "",
                            column.getExtra() != null && !column.getExtra().isEmpty() ? 
                                " " + column.getExtra() : ""));
                }
            } else {
                sb.append("  无列信息\n");
            }
            
            // 索引信息
            sb.append("\n索引信息:\n");
            if (structure.getIndexes() != null && !structure.getIndexes().isEmpty()) {
                Map<String, List<TableStructure.IndexInfo>> indexGroups = new java.util.LinkedHashMap<>();
                for (TableStructure.IndexInfo index : structure.getIndexes()) {
                    indexGroups.computeIfAbsent(index.getIndexName(), k -> new ArrayList<>()).add(index);
                }
                
                for (Map.Entry<String, List<TableStructure.IndexInfo>> entry : indexGroups.entrySet()) {
                    String indexName = entry.getKey();
                    List<TableStructure.IndexInfo> indexColumns = entry.getValue();
                    
                    // 获取索引列名（按序号排序）
                    List<String> columnNames = indexColumns.stream()
                            .sorted(Comparator.comparingInt(TableStructure.IndexInfo::getSeqInIndex))
                            .map(TableStructure.IndexInfo::getColumnName)
                            .collect(Collectors.toList());
                    
                    boolean isUnique = indexColumns.get(0).getNonUnique() == 0;
                    String indexType = indexColumns.get(0).getIndexType();
                    
                    sb.append(String.format("  - %s (%s): [%s]%s\n",
                            indexName,
                            indexType,
                            String.join(", ", columnNames),
                            isUnique ? " UNIQUE" : ""));
                }
            } else {
                sb.append("  无索引信息\n");
            }
            
            sb.append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 格式化执行计划为可读文本
     */
    private String formatExecutionPlan(ExecutionPlan plan) {
        if (plan == null) {
            return "无执行计划数据";
        }
        
        StringBuilder sb = new StringBuilder();
        
        if (plan.getQueryBlock() != null) {
            ExecutionPlan.QueryBlock qb = plan.getQueryBlock();
            
            if (qb.getCostInfo() != null) {
                sb.append(String.format("查询成本: %s\n", qb.getCostInfo().getQueryCost()));
            }
            
            if (qb.getTable() != null) {
                ExecutionPlan.TableInfo table = qb.getTable();
                sb.append(String.format("表名: %s\n", table.getTableName()));
                sb.append(String.format("访问类型: %s\n", table.getAccessType()));
                sb.append(String.format("使用索引: %s\n", table.getKey() != null ? table.getKey() : "无"));
                sb.append(String.format("扫描行数: %d\n", table.getRowsExaminedPerScan() != null ? 
                        table.getRowsExaminedPerScan() : 0));
                sb.append(String.format("产出行数: %d\n", table.getRowsProducedPerJoin() != null ? 
                        table.getRowsProducedPerJoin() : 0));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 构建偏差详情
     */
    private String buildDeviationDetails(SqlRiskPrediction prediction, ExecutionPlan actualPlan) {
        StringBuilder sb = new StringBuilder();
        
        if (actualPlan != null && actualPlan.getQueryBlock() != null && 
            actualPlan.getQueryBlock().getTable() != null) {
            
            ExecutionPlan.TableInfo table = actualPlan.getQueryBlock().getTable();
            
            // 扫描行数偏差
            if (prediction.getEstimatedRowsExamined() != null && table.getRowsExaminedPerScan() != null) {
                long predicted = prediction.getEstimatedRowsExamined();
                long actual = table.getRowsExaminedPerScan();
                long deviation = actual - predicted;
                sb.append(String.format("扫描行数偏差: 预测 %d, 实际 %d, 偏差 %d (%.1f%%)\n", 
                        predicted, actual, deviation, 
                        predicted > 0 ? (double)Math.abs(deviation) / predicted * 100 : 0));
            }
            
            // 索引使用偏差
            boolean actualUsesIndex = table.getKey() != null && !table.getKey().isEmpty();
            if (prediction.getExpectedIndexUsage() != null) {
                sb.append(String.format("索引使用: 预测 %s, 实际 %s\n", 
                        prediction.getExpectedIndexUsage() ? "使用" : "不使用",
                        actualUsesIndex ? "使用" : "不使用"));
            }
            
            // 访问类型偏差
            if (prediction.getExpectedAccessType() != null && table.getAccessType() != null) {
                sb.append(String.format("访问类型: 预测 %s, 实际 %s\n", 
                        prediction.getExpectedAccessType(), table.getAccessType()));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 构建完整响应（多场景）
     */
    private SqlRiskAssessmentResponse buildResponseMultiScenario(String originalSql,
                                                                  SqlFillingResult fillingResult,
                                                                  List<ColumnStatisticsDTO> histograms,
                                                                  SqlRiskPrediction prediction,
                                                                  List<ScenarioVerification> verifications,
                                                                  SqlRiskPrediction finalResult,
                                                                  boolean refinementApplied,
                                                                  long processingTimeMs) {
        SqlRiskAssessmentResponse response = new SqlRiskAssessmentResponse();
        response.setOriginalSql(originalSql);
        
        // 新增字段
        response.setFillingResult(fillingResult);
        response.setScenarioVerifications(verifications);
        
        // 向后兼容字段
        if (!fillingResult.getScenarios().isEmpty()) {
            response.setFilledSql(fillingResult.getScenarios().get(0).getFilledSql());
        }
        if (!verifications.isEmpty()) {
            response.setActualExplainPlan(verifications.get(0).getExecutionPlan());
        }
        
        response.setHistogramData(convertHistogramSummary(histograms));
        response.setPredictorResult(prediction);
        response.setVerificationComparison(buildComparisonMultiScenario(prediction, verifications));
        response.setRefinementApplied(refinementApplied);
        
        if (refinementApplied) {
            response.setRefinedResult(finalResult);
        }
        
        response.setFinalRiskLevel(finalResult.getRiskLevel());
        response.setRecommendations(finalResult.getRecommendations());
        response.setProcessingTimeMs(processingTimeMs);
        
        return response;
    }
    
    /**
     * 构建降级响应（多场景，EXPLAIN 失败时）
     */
    private SqlRiskAssessmentResponse buildDegradedResponse(String originalSql,
                                                             SqlFillingResult fillingResult,
                                                             List<ColumnStatisticsDTO> histograms,
                                                             SqlRiskPrediction prediction,
                                                             long processingTimeMs) {
        SqlRiskAssessmentResponse response = new SqlRiskAssessmentResponse();
        response.setOriginalSql(originalSql);
        response.setFillingResult(fillingResult);
        
        // 向后兼容字段
        if (fillingResult != null && !fillingResult.getScenarios().isEmpty()) {
            response.setFilledSql(fillingResult.getScenarios().get(0).getFilledSql());
        }
        
        response.setHistogramData(convertHistogramSummary(histograms));
        response.setPredictorResult(prediction);
        response.setScenarioVerifications(new ArrayList<>());
        response.setActualExplainPlan(null);
        response.setVerificationComparison(null);
        response.setRefinementApplied(false);
        response.setFinalRiskLevel(prediction.getRiskLevel());
        response.setRecommendations(prediction.getRecommendations());
        response.setProcessingTimeMs(processingTimeMs);
        
        return response;
    }
    
    /**
     * 构建对比结果（多场景）
     */
    private SqlRiskAssessmentResponse.VerificationComparison buildComparisonMultiScenario(
            SqlRiskPrediction prediction, List<ScenarioVerification> verifications) {
        
        SqlRiskAssessmentResponse.VerificationComparison comparison = 
                new SqlRiskAssessmentResponse.VerificationComparison();
        
        Map<String, SqlRiskAssessmentResponse.ComparisonDetail> allDetails = new HashMap<>();
        boolean anyMismatch = false;
        String maxDeviationSeverity = "NONE";
        
        // 对每个场景进行对比
        for (int i = 0; i < verifications.size(); i++) {
            ScenarioVerification verification = verifications.get(i);
            ExecutionPlan plan = verification.getExecutionPlan();
            
            if (plan == null || plan.getQueryBlock() == null || plan.getQueryBlock().getTable() == null) {
                continue;
            }
            
            ExecutionPlan.TableInfo table = plan.getQueryBlock().getTable();
            String scenarioPrefix = "scenario_" + i + "_";
            
            // 扫描行数对比
            if (prediction.getEstimatedRowsExamined() != null && table.getRowsExaminedPerScan() != null) {
                SqlRiskAssessmentResponse.ComparisonDetail detail = 
                        new SqlRiskAssessmentResponse.ComparisonDetail();
                detail.setMetric("扫描行数-" + verification.getScenarioName());
                detail.setPredictedValue(prediction.getEstimatedRowsExamined());
                detail.setActualValue(table.getRowsExaminedPerScan());
                
                long deviation = Math.abs(table.getRowsExaminedPerScan() - 
                        prediction.getEstimatedRowsExamined());
                double deviationPercent = prediction.getEstimatedRowsExamined() > 0 ? 
                        (double) deviation / prediction.getEstimatedRowsExamined() * 100 : 0;
                
                boolean matched = deviationPercent <= 50 && deviation <= 1000;
                detail.setMatched(matched);
                detail.setDeviation(String.format("偏差 %d 行 (%.1f%%)", deviation, deviationPercent));
                allDetails.put(scenarioPrefix + "rows", detail);
                
                if (!matched) {
                    anyMismatch = true;
                    String severity = determineSingleDeviationSeverity(deviation);
                    if (isMoreSevere(severity, maxDeviationSeverity)) {
                        maxDeviationSeverity = severity;
                    }
                }
            }
            
            // 索引使用对比
            boolean actualUsesIndex = table.getKey() != null && !table.getKey().isEmpty();
            if (prediction.getExpectedIndexUsage() != null) {
                SqlRiskAssessmentResponse.ComparisonDetail detail = 
                        new SqlRiskAssessmentResponse.ComparisonDetail();
                detail.setMetric("索引使用-" + verification.getScenarioName());
                detail.setPredictedValue(prediction.getExpectedIndexUsage());
                detail.setActualValue(actualUsesIndex);
                boolean matched = prediction.getExpectedIndexUsage() == actualUsesIndex;
                detail.setMatched(matched);
                detail.setDeviation(matched ? "一致" : "不一致");
                allDetails.put(scenarioPrefix + "index", detail);
                
                if (!matched) {
                    anyMismatch = true;
                    if (isMoreSevere("MODERATE", maxDeviationSeverity)) {
                        maxDeviationSeverity = "MODERATE";
                    }
                }
            }
        }
        
        comparison.setMatched(!anyMismatch);
        comparison.setDetails(allDetails);
        comparison.setSummary(anyMismatch ? 
                String.format("在 %d 个场景中发现预测与实际的偏差", verifications.size()) : 
                "所有场景的预测与实际执行计划一致");
        comparison.setDeviationSeverity(maxDeviationSeverity);
        
        return comparison;
    }
    
    /**
     * 判断单个偏差的严重程度
     */
    private String determineSingleDeviationSeverity(long deviation) {
        if (deviation > 10000) {
            return "SEVERE";
        } else if (deviation > 5000) {
            return "MODERATE";
        } else if (deviation > 1000) {
            return "MINOR";
        }
        return "NONE";
    }
    
    /**
     * 比较严重程度
     */
    private boolean isMoreSevere(String severity1, String severity2) {
        String[] severityOrder = {"NONE", "MINOR", "MODERATE", "SEVERE"};
        int index1 = Arrays.asList(severityOrder).indexOf(severity1);
        int index2 = Arrays.asList(severityOrder).indexOf(severity2);
        return index1 > index2;
    }
    
    /**
     * 构建完整响应（单场景，保留用于兼容）
     */
    private SqlRiskAssessmentResponse buildResponse(String originalSql, String filledSql,
                                                      List<ColumnStatisticsDTO> histograms,
                                                      SqlRiskPrediction prediction,
                                                      ExecutionPlan actualPlan,
                                                      SqlRiskPrediction finalResult,
                                                      boolean refinementApplied,
                                                      long processingTimeMs) {
        SqlRiskAssessmentResponse response = new SqlRiskAssessmentResponse();
        response.setOriginalSql(originalSql);
        response.setFilledSql(filledSql);
        response.setHistogramData(convertHistogramSummary(histograms));
        response.setPredictorResult(prediction);
        response.setActualExplainPlan(actualPlan);
        response.setVerificationComparison(buildComparison(prediction, actualPlan));
        response.setRefinementApplied(refinementApplied);
        
        if (refinementApplied) {
            response.setRefinedResult(finalResult);
        }
        
        response.setFinalRiskLevel(finalResult.getRiskLevel());
        response.setRecommendations(finalResult.getRecommendations());
        response.setProcessingTimeMs(processingTimeMs);
        
        return response;
    }
    
    /**
     * 构建降级响应（EXPLAIN 失败时）
     */
    private SqlRiskAssessmentResponse buildDegradedResponse(String originalSql, String filledSql,
                                                             List<ColumnStatisticsDTO> histograms,
                                                             SqlRiskPrediction prediction,
                                                             long processingTimeMs) {
        SqlRiskAssessmentResponse response = new SqlRiskAssessmentResponse();
        response.setOriginalSql(originalSql);
        response.setFilledSql(filledSql);
        response.setHistogramData(convertHistogramSummary(histograms));
        response.setPredictorResult(prediction);
        response.setActualExplainPlan(null);
        response.setVerificationComparison(null);
        response.setRefinementApplied(false);
        response.setFinalRiskLevel(prediction.getRiskLevel());
        response.setRecommendations(prediction.getRecommendations());
        response.setProcessingTimeMs(processingTimeMs);

            return response;
    }
    
    /**
     * 转换直方图数据为摘要格式
     */
    private List<SqlRiskAssessmentResponse.HistogramSummary> convertHistogramSummary(
            List<ColumnStatisticsDTO> histograms) {
        if (histograms == null) {
            return new ArrayList<>();
        }
        
        return histograms.stream().map(hist -> {
            SqlRiskAssessmentResponse.HistogramSummary summary = 
                    new SqlRiskAssessmentResponse.HistogramSummary();
            summary.setTableName(hist.getTableName());
            summary.setColumnName(hist.getColumnName());
            summary.setHistogramType(hist.getHistogramType());
            summary.setBucketCount(hist.getBucketCount());
            summary.setMinValue(hist.getMinValue());
            summary.setMaxValue(hist.getMaxValue());
            summary.setSampleCount(hist.getSampleValues() != null ? 
                    hist.getSampleValues().size() : 0);
            return summary;
        }).collect(Collectors.toList());
    }
    
    /**
     * 构建对比结果
     */
    private SqlRiskAssessmentResponse.VerificationComparison buildComparison(
            SqlRiskPrediction prediction, ExecutionPlan actualPlan) {
        
        SqlRiskAssessmentResponse.VerificationComparison comparison = 
                new SqlRiskAssessmentResponse.VerificationComparison();
        
        Map<String, SqlRiskAssessmentResponse.ComparisonDetail> details = new HashMap<>();
        boolean allMatched = true;
        
        if (actualPlan != null && actualPlan.getQueryBlock() != null && 
            actualPlan.getQueryBlock().getTable() != null) {
            
            ExecutionPlan.TableInfo table = actualPlan.getQueryBlock().getTable();
            
            // 扫描行数对比
            if (prediction.getEstimatedRowsExamined() != null && table.getRowsExaminedPerScan() != null) {
                SqlRiskAssessmentResponse.ComparisonDetail detail = 
                        new SqlRiskAssessmentResponse.ComparisonDetail();
                detail.setMetric("扫描行数");
                detail.setPredictedValue(prediction.getEstimatedRowsExamined());
                detail.setActualValue(table.getRowsExaminedPerScan());
                
                long deviation = Math.abs(table.getRowsExaminedPerScan() - 
                        prediction.getEstimatedRowsExamined());
                double deviationPercent = prediction.getEstimatedRowsExamined() > 0 ? 
                        (double) deviation / prediction.getEstimatedRowsExamined() * 100 : 0;
                
                boolean matched = deviationPercent <= 50 && deviation <= 1000;
                detail.setMatched(matched);
                detail.setDeviation(String.format("偏差 %d 行 (%.1f%%)", deviation, deviationPercent));
                details.put("rows_examined", detail);
                
                if (!matched) allMatched = false;
            }
            
            // 索引使用对比
            boolean actualUsesIndex = table.getKey() != null && !table.getKey().isEmpty();
            if (prediction.getExpectedIndexUsage() != null) {
                SqlRiskAssessmentResponse.ComparisonDetail detail = 
                        new SqlRiskAssessmentResponse.ComparisonDetail();
                detail.setMetric("索引使用");
                detail.setPredictedValue(prediction.getExpectedIndexUsage());
                detail.setActualValue(actualUsesIndex);
                boolean matched = prediction.getExpectedIndexUsage() == actualUsesIndex;
                detail.setMatched(matched);
                detail.setDeviation(matched ? "一致" : "不一致");
                details.put("index_usage", detail);
                
                if (!matched) allMatched = false;
            }
            
            // 访问类型对比
            if (prediction.getExpectedAccessType() != null && table.getAccessType() != null) {
                SqlRiskAssessmentResponse.ComparisonDetail detail = 
                        new SqlRiskAssessmentResponse.ComparisonDetail();
                detail.setMetric("访问类型");
                detail.setPredictedValue(prediction.getExpectedAccessType());
                detail.setActualValue(table.getAccessType());
                boolean matched = prediction.getExpectedAccessType()
                        .equalsIgnoreCase(table.getAccessType());
                detail.setMatched(matched);
                detail.setDeviation(matched ? "一致" : "不一致");
                details.put("access_type", detail);
                
                if (!matched) allMatched = false;
            }
        }
        
        comparison.setMatched(allMatched);
        comparison.setDetails(details);
        comparison.setSummary(allMatched ? "预测与实际执行计划一致" : "预测与实际执行计划存在偏差");
        comparison.setDeviationSeverity(determineDeviationSeverity(details));
        
        return comparison;
    }
    
    /**
     * 判断偏差严重程度
     */
    private String determineDeviationSeverity(Map<String, SqlRiskAssessmentResponse.ComparisonDetail> details) {
        if (details.isEmpty()) {
            return "NONE";
        }
        
        boolean hasUnmatched = details.values().stream().anyMatch(d -> !d.getMatched());
        if (!hasUnmatched) {
            return "NONE";
        }
        
        // 检查扫描行数偏差
        SqlRiskAssessmentResponse.ComparisonDetail rowsDetail = details.get("rows_examined");
        if (rowsDetail != null && !rowsDetail.getMatched()) {
            if (rowsDetail.getActualValue() instanceof Long && 
                rowsDetail.getPredictedValue() instanceof Long) {
                long actual = (Long) rowsDetail.getActualValue();
                long predicted = (Long) rowsDetail.getPredictedValue();
                long deviation = Math.abs(actual - predicted);
                
                if (deviation > 10000) {
                    return "SEVERE";
                } else if (deviation > 5000) {
                    return "MODERATE";
                }
            }
        }
        
        // 检查索引使用不一致
        SqlRiskAssessmentResponse.ComparisonDetail indexDetail = details.get("index_usage");
        if (indexDetail != null && !indexDetail.getMatched()) {
            return "MODERATE";
        }
        
        return "MINOR";
    }
    
    /**
     * 解析 JSON 响应（增强容错处理）
     */
    private <T> T parseJsonResponse(String response, Class<T> clazz) throws JsonProcessingException {
        // 提取 JSON 内容（移除 markdown 代码块标记）
        String jsonContent = response.trim();
        
        logger.debug("原始 LLM 响应长度: {} 字符", jsonContent.length());
        
        // 移除可能的 markdown 代码块标记
        if (jsonContent.startsWith("```json")) {
            jsonContent = jsonContent.substring(7);
        }
        if (jsonContent.startsWith("```")) {
            jsonContent = jsonContent.substring(3);
        }
        if (jsonContent.endsWith("```")) {
            jsonContent = jsonContent.substring(0, jsonContent.length() - 3);
        }
        
        jsonContent = jsonContent.trim();
        
        // 尝试多种解析策略
        try {
            // 策略 1: 直接解析
            return objectMapper.readValue(jsonContent, clazz);
        } catch (JsonProcessingException e) {
            logger.warn("JSON 直接解析失败，尝试清理后重试: {}", e.getMessage());
            
            try {
                // 策略 2: 清理常见的格式问题
                String cleanedContent = cleanJsonContent(jsonContent);
                logger.debug("清理后的 JSON 长度: {} 字符", cleanedContent.length());
                return objectMapper.readValue(cleanedContent, clazz);
            } catch (JsonProcessingException e2) {
                logger.error("JSON 清理后仍然解析失败");
                logger.error("问题 JSON 内容（前 500 字符）: {}", 
                        jsonContent.substring(0, Math.min(500, jsonContent.length())));
                
                // 尝试找到 JSON 的实际起始位置
                try {
                    String extractedJson = extractJsonFromText(jsonContent);
                    if (!extractedJson.equals(jsonContent)) {
                        logger.info("尝试从文本中提取 JSON");
                        return objectMapper.readValue(extractedJson, clazz);
                    }
                } catch (Exception e3) {
                    logger.error("提取 JSON 失败: {}", e3.getMessage());
                }
                
                // 所有策略都失败，抛出原始异常
                throw new JsonProcessingException("JSON 解析失败，已尝试多种策略。原始错误: " + e.getMessage()) {
                    @Override
                    public String getOriginalMessage() {
                        return e.getMessage();
                    }
                };
            }
        }
    }
    
    /**
     * 清理 JSON 内容中的常见问题
     */
    private String cleanJsonContent(String jsonContent) {
        String cleaned = jsonContent;
        
        // 1. 移除 JSON 前后的非 JSON 文本
        int firstBrace = cleaned.indexOf('{');
        int firstBracket = cleaned.indexOf('[');
        int startPos = -1;
        
        if (firstBrace >= 0 && firstBracket >= 0) {
            startPos = Math.min(firstBrace, firstBracket);
        } else if (firstBrace >= 0) {
            startPos = firstBrace;
        } else if (firstBracket >= 0) {
            startPos = firstBracket;
        }
        
        if (startPos > 0) {
            cleaned = cleaned.substring(startPos);
            logger.debug("移除了 JSON 前的 {} 个字符", startPos);
        }
        
        // 2. 移除尾部的非 JSON 文本
        int lastBrace = cleaned.lastIndexOf('}');
        int lastBracket = cleaned.lastIndexOf(']');
        int endPos = Math.max(lastBrace, lastBracket);
        
        if (endPos > 0 && endPos < cleaned.length() - 1) {
            cleaned = cleaned.substring(0, endPos + 1);
            logger.debug("移除了 JSON 后的 {} 个字符", cleaned.length() - endPos - 1);
        }
        
        // 3. 修复常见的转义问题
        // 将双重转义的反斜杠还原：\\\\ -> \\
        cleaned = cleaned.replaceAll("\\\\\\\\", "\\\\");
        
        // 4. 修复可能的错误引号转义
        // LLM 有时会错误地转义引号，如 \\\" 应该是 \"
        cleaned = cleaned.replaceAll("\\\\\\\\\"", "\\\\\"");
        
        // 5. 移除 JSON 中的控制字符（除了必要的换行符和制表符）
        cleaned = cleaned.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        
        // 6. 修复可能的换行符问题
        // 将未转义的换行符替换为转义的换行符（但不在字符串值中）
        // 这个比较复杂，暂时跳过，因为可能会破坏正确的 JSON
        
        return cleaned;
    }
    
    /**
     * 从可能包含其他文本的响应中提取 JSON
     */
    private String extractJsonFromText(String text) {
        // 尝试找到最外层的 JSON 对象或数组
        int depth = 0;
        int startPos = -1;
        int endPos = -1;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (c == '{' || c == '[') {
                if (depth == 0) {
                    startPos = i;
                }
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0 && startPos >= 0) {
                    endPos = i;
                    break;
                }
            }
        }
        
        if (startPos >= 0 && endPos > startPos) {
            String extracted = text.substring(startPos, endPos + 1);
            logger.debug("从文本中提取了 JSON，起始位置: {}, 结束位置: {}", startPos, endPos);
            return extracted;
        }
        
        return text;
    }
}
