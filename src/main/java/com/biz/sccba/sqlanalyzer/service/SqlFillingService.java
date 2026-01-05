package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.domain.stats.ColumnHistogram;
import com.biz.sccba.sqlanalyzer.llm.context.PromptRenderer;
import com.biz.sccba.sqlanalyzer.llm.context.PromptTemplateEngine;
import com.biz.sccba.sqlanalyzer.model.*;
import com.biz.sccba.sqlanalyzer.repository.SqlFillingRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 阶段1：数据填充。
 */
@Service
public class SqlFillingService {

    private static final Logger logger = LoggerFactory.getLogger(SqlFillingService.class);

    @Autowired
    private ExecutionPlanServiceFacade executionPlanFacade;

    @Autowired
    private LlmManagerService llmManagerService;

    @Autowired
    private PromptTemplateManagerService promptTemplateManagerService;

    @Autowired
    private SqlFillingRecordRepository fillingRecordRepository;

    @Autowired
    private JsonResponseParser jsonResponseParser;

    @Autowired
    private PromptTemplateEngine templateEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SqlFillingRecord fillSqlWithDataDistribution(String sql,
                                                        String mapperId,
                                                        String datasourceName,
                                                        String llmName) throws AgentException {
        logger.info("阶段1：数据填充阶段 - mapperId: {}, sql: {}", mapperId, sql);

        List<ColumnHistogram> histograms = executionPlanFacade.getHistogramData(sql, datasourceName);
        List<TableStructure> tableStructures = executionPlanFacade.getTableStructures(sql, datasourceName);

        String templateContent = promptTemplateManagerService.getTemplateContent(
                PromptTemplateManagerService.TYPE_SQL_PARAMETER_FILLING);
        String histogramSummary = PromptRenderer.renderHistogramAsJson(histograms, tableStructures);
        String tableStructureInfo = formatTableStructures(tableStructures);

        String prompt = templateEngine.render(templateContent, Map.of(
                "sql", sql,
                "histogram_data", histogramSummary,
                "table_structure", tableStructureInfo
        ));

        String response = callLlm(llmName, prompt);
        SqlFillingResult fillingResult = jsonResponseParser.parse(response, SqlFillingResult.class, "SQL_FILL");

        try {
            SqlFillingRecord record = new SqlFillingRecord();
            record.setMapperId(mapperId);
            record.setOriginalSql(sql);
            record.setFillingResultJson(objectMapper.writeValueAsString(fillingResult));
            record.setHistogramDataJson(objectMapper.writeValueAsString(histograms));
            record.setDatasourceName(datasourceName);
            record.setLlmName(llmName);

            SqlFillingRecord savedRecord = fillingRecordRepository.save(record);
            logger.info("数据填充记录已保存，ID: {}, 场景数: {}",
                    savedRecord.getId(),
                    fillingResult.getScenarios() != null ? fillingResult.getScenarios().size() : 0);
            return savedRecord;
        } catch (Exception e) {
            throw new AgentException(AgentErrorCode.JSON_PARSE_FAILED, "保存填充记录失败", e);
        }
    }

    private String callLlm(String llmName, String prompt) throws AgentException {
        ChatClient chatClient = llmManagerService.getChatClient(llmName);
        int maxRetries = 3;
        long retryDelayMs = 1000; // 初始重试延迟1秒
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.debug("LLM 调用尝试 {}/{} - llmName: {}", attempt, maxRetries, llmName);
                return CompletableFuture.supplyAsync(() ->
                        chatClient.prompt().user(prompt).call().content()).join();
            } catch (Exception e) {
                boolean isRetryable = isRetryableException(e);
                String errorMsg = String.format("LLM 调用失败 (尝试 %d/%d): %s", attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries && isRetryable) {
                    logger.warn("{}，将在 {}ms 后重试", errorMsg, retryDelayMs);
                    try {
                        Thread.sleep(retryDelayMs);
                        retryDelayMs *= 2; // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AgentException(AgentErrorCode.LLM_CALL_FAILED, "LLM 调用被中断", ie);
                    }
                } else {
                    logger.error("{}", errorMsg, e);
                    String userMessage = isRetryable 
                        ? String.format("LLM 调用失败，已重试 %d 次", maxRetries)
                        : "LLM 调用失败: " + getRootCauseMessage(e);
                    throw new AgentException(AgentErrorCode.LLM_CALL_FAILED, userMessage, e);
                }
            }
        }
        
        // 理论上不会到达这里
        throw new AgentException(AgentErrorCode.LLM_CALL_FAILED, "LLM 调用失败");
    }
    
    private boolean isRetryableException(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            String message = cause.getMessage();
            
            // SSL 握手失败、网络连接问题等可重试
            if (cause instanceof javax.net.ssl.SSLHandshakeException ||
                cause instanceof javax.net.ssl.SSLException ||
                cause instanceof java.net.ConnectException ||
                cause instanceof java.net.SocketTimeoutException ||
                cause instanceof java.io.IOException ||
                (cause instanceof org.springframework.web.client.ResourceAccessException &&
                 (message != null && (message.contains("handshake") || 
                                      message.contains("connection") ||
                                      message.contains("timeout"))))) {
                return true;
            }
            
            // CompletionException 需要检查内部原因
            if (cause instanceof java.util.concurrent.CompletionException) {
                cause = cause.getCause();
                continue;
            }
            
            cause = cause.getCause();
        }
        return false;
    }
    
    private String getRootCauseMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    /**
     * 复用老逻辑：格式化表结构信息。
     */
    private String formatTableStructures(List<TableStructure> tableStructures) {
        if (tableStructures == null || tableStructures.isEmpty()) {
            return "无表结构信息";
        }

        StringBuilder sb = new StringBuilder();
        for (TableStructure structure : tableStructures) {
            sb.append(String.format("=== 表: %s ===\n", structure.getTableName()));

            if (structure.getStatistics() != null) {
                TableStructure.TableStatistics stats = structure.getStatistics();
                sb.append(String.format("总行数: %d, 数据大小: %d bytes, 索引大小: %d bytes, 引擎: %s\n",
                        stats.getRows(), stats.getDataLength(), stats.getIndexLength(), stats.getEngine()));
            }

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

            sb.append("\n索引信息:\n");
            if (structure.getIndexes() != null && !structure.getIndexes().isEmpty()) {
                Map<String, List<TableStructure.IndexInfo>> indexGroups = new LinkedHashMap<>();
                for (TableStructure.IndexInfo index : structure.getIndexes()) {
                    indexGroups.computeIfAbsent(index.getIndexName(), k -> new ArrayList<>()).add(index);
                }

                for (Map.Entry<String, List<TableStructure.IndexInfo>> entry : indexGroups.entrySet()) {
                    String indexName = entry.getKey();
                    List<TableStructure.IndexInfo> indexColumns = entry.getValue();

            List<String> columnNames = indexColumns.stream()
                    .sorted(Comparator.comparingInt(TableStructure.IndexInfo::getSeqInIndex))
                    .map(TableStructure.IndexInfo::getColumnName)
                    .collect(java.util.stream.Collectors.toList());

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
}

