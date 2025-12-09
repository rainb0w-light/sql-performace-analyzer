package com.example.sqlanalyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AiClientService {

    private static final Logger logger = LoggerFactory.getLogger(AiClientService.class);

    @Autowired
    private LlmManagerService llmManagerService;

    @Autowired
    private PromptTemplateManagerService promptTemplateManagerService;

    /**
     * 分析SQL性能（MySQL InnoDB）
     * @param sql SQL语句
     * @param executionPlan 执行计划
     * @param tableStructures 表结构信息
     * @param llmName 大模型名称（可选，如果不指定则使用默认模型）
     */
    public String analyzeSqlPerformance(String sql, String executionPlan, String tableStructures, String llmName) {
        try {
            logger.debug("构建MySQL InnoDB提示词模板...");
            
            // 获取动态模板内容
            String templateContent = promptTemplateManagerService.getTemplateContent(PromptTemplateManagerService.TYPE_MYSQL);
            PromptTemplate promptTemplate = new PromptTemplate(templateContent);
            
            // 使用PromptTemplate构建提示词
            Map<String, Object> variables = Map.of(
                "sql", sql,
                "execution_plan", executionPlan,
                "table_structures", tableStructures
            );
            String prompt = promptTemplate.render(variables);

            logger.debug("调用AI模型进行MySQL分析...");
            
            // 获取ChatClient
            ChatClient chatClient = llmManagerService.getChatClient(llmName);
            
            // 调用AI模型
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            logger.debug("MySQL AI分析完成");
            return result;
            
        } catch (Exception e) {
            logger.error("MySQL AI分析失败", e);
            throw new RuntimeException("MySQL AI分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分析SQL性能（GoldenDB分布式）
     * @param sql SQL语句
     * @param executionPlan 执行计划
     * @param tableStructures 表结构信息
     * @param llmName 大模型名称（可选，如果不指定则使用默认模型）
     */
    public String analyzeSqlPerformanceWithGoldenDb(String sql, String executionPlan, String tableStructures, String llmName) {
        try {
            logger.debug("构建GoldenDB提示词模板...");
            
            // 获取动态模板内容
            String templateContent = promptTemplateManagerService.getTemplateContent(PromptTemplateManagerService.TYPE_GOLDENDB);
            PromptTemplate promptTemplate = new PromptTemplate(templateContent);
            
            // 使用PromptTemplate构建提示词
            Map<String, Object> variables = Map.of(
                "sql", sql,
                "execution_plan", executionPlan,
                "table_structures", tableStructures
            );
            String prompt = promptTemplate.render(variables);

            logger.debug("调用AI模型进行GoldenDB分析...");
            
            // 获取ChatClient
            ChatClient chatClient = llmManagerService.getChatClient(llmName);
            
            // 调用AI模型
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            logger.debug("GoldenDB AI分析完成");
            return result;
            
        } catch (Exception e) {
            logger.error("GoldenDB AI分析失败", e);
            throw new RuntimeException("GoldenDB AI分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步分析SQL性能（MySQL InnoDB）
     */
    @Async
    public CompletableFuture<String> analyzeSqlPerformanceAsync(String sql, String executionPlan, String tableStructures, String llmName) {
        try {
            String result = analyzeSqlPerformance(sql, executionPlan, tableStructures, llmName);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            logger.error("MySQL异步分析失败", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 异步分析SQL性能（GoldenDB分布式）
     */
    @Async
    public CompletableFuture<String> analyzeSqlPerformanceWithGoldenDbAsync(String sql, String executionPlan, String tableStructures, String llmName) {
        try {
            String result = analyzeSqlPerformanceWithGoldenDb(sql, executionPlan, tableStructures, llmName);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            logger.error("GoldenDB异步分析失败", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 同时分析SQL性能（MySQL InnoDB + GoldenDB），并行执行
     * @param sql SQL语句
     * @param executionPlan 执行计划
     * @param tableStructures 表结构信息
     * @param llmName 大模型名称（可选，如果不指定则使用默认模型）
     * @return 包含两个分析结果的数组，[0]为MySQL结果，[1]为GoldenDB结果
     */
    public AnalysisResultPair analyzeSqlPerformanceBoth(String sql, String executionPlan, String tableStructures, String llmName) {
        try {
            logger.info("开始并行执行MySQL和GoldenDB分析...");
            
            // 并行执行两个分析
            CompletableFuture<String> mysqlFuture = analyzeSqlPerformanceAsync(sql, executionPlan, tableStructures, llmName);
            CompletableFuture<String> goldenDbFuture = analyzeSqlPerformanceWithGoldenDbAsync(sql, executionPlan, tableStructures, llmName);
            
            // 等待两个分析完成
            CompletableFuture.allOf(mysqlFuture, goldenDbFuture).join();
            
            String mysqlResult = null;
            String goldenDbResult = null;
            Exception mysqlException = null;
            Exception goldenDbException = null;
            
            // 获取MySQL结果
            try {
                mysqlResult = mysqlFuture.get();
            } catch (Exception e) {
                logger.error("获取MySQL分析结果失败", e);
                mysqlException = e instanceof Exception ? (Exception) e : new RuntimeException(e);
            }
            
            // 获取GoldenDB结果
            try {
                goldenDbResult = goldenDbFuture.get();
            } catch (Exception e) {
                logger.error("获取GoldenDB分析结果失败", e);
                goldenDbException = e instanceof Exception ? (Exception) e : new RuntimeException(e);
            }
            
            logger.info("并行分析完成 - MySQL: {}, GoldenDB: {}", 
                mysqlResult != null ? "成功" : "失败",
                goldenDbResult != null ? "成功" : "失败");
            
            return new AnalysisResultPair(mysqlResult, goldenDbResult, mysqlException, goldenDbException);
            
        } catch (Exception e) {
            logger.error("并行分析失败", e);
            throw new RuntimeException("并行分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分析结果对，包含MySQL和GoldenDB的分析结果
     */
    public static class AnalysisResultPair {
        private final String mysqlResult;
        private final String goldenDbResult;
        private final Exception mysqlException;
        private final Exception goldenDbException;

        public AnalysisResultPair(String mysqlResult, String goldenDbResult, 
                                 Exception mysqlException, Exception goldenDbException) {
            this.mysqlResult = mysqlResult;
            this.goldenDbResult = goldenDbResult;
            this.mysqlException = mysqlException;
            this.goldenDbException = goldenDbException;
        }

        public String getMysqlResult() {
            return mysqlResult;
        }

        public String getGoldenDbResult() {
            return goldenDbResult;
        }

        public Exception getMysqlException() {
            return mysqlException;
        }

        public Exception getGoldenDbException() {
            return goldenDbException;
        }

        public boolean hasMysqlResult() {
            return mysqlResult != null;
        }

        public boolean hasGoldenDbResult() {
            return goldenDbResult != null;
        }
    }
}
