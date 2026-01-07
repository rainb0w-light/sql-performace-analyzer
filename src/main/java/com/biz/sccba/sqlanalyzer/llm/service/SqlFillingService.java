package com.biz.sccba.sqlanalyzer.llm.service;

import com.biz.sccba.sqlanalyzer.data.TableStructure;
import com.biz.sccba.sqlanalyzer.domain.stats.ColumnHistogram;
import com.biz.sccba.sqlanalyzer.error.AgentErrorCode;
import com.biz.sccba.sqlanalyzer.error.AgentException;
import com.biz.sccba.sqlanalyzer.llm.context.PromptRenderer;
import com.biz.sccba.sqlanalyzer.llm.context.PromptTemplateEngine;
import com.biz.sccba.sqlanalyzer.model.*;
import com.biz.sccba.sqlanalyzer.response.FillingRecordsResponse;
import com.biz.sccba.sqlanalyzer.repository.SqlFillingRecordRepository;
import com.biz.sccba.sqlanalyzer.service.ExecutionPlanServiceFacade;
import com.biz.sccba.sqlanalyzer.service.LlmManagerService;
import com.biz.sccba.sqlanalyzer.service.PromptTemplateManagerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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
    private PromptTemplateEngine templateEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SqlFillingRecord fillSqlWithDataDistribution(String sql,
                                                        String mapperId,
                                                        String datasourceName,
                                                        String llmName) throws AgentException {
        logger.info("阶段1：数据填充阶段 - mapperId: {}, sql: {}", mapperId, sql);

        // 先查询是否已有填充记录
        Optional<SqlFillingRecord> existingRecord = fillingRecordRepository
                .findByMapperIdAndDatasourceNameAndLlmName(mapperId, datasourceName, llmName);
        
        if (existingRecord.isPresent()) {
            logger.info("找到已存在的填充记录，复用 - mapperId: {}, recordId: {}", 
                    mapperId, existingRecord.get().getId());
            return existingRecord.get();
        }

        logger.info("未找到填充记录，调用 LLM 生成 - mapperId: {}", mapperId);
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

        ChatClient chatClient = llmManagerService.getChatClient(llmName);
        String response =  chatClient.prompt().user(prompt).call().content();

        logger.info("LLM RESPONSE: {}", response);

        try {
            SqlFillingRecord record = new SqlFillingRecord();
            record.setMapperId(mapperId);
            record.setOriginalSql(sql);
            record.setFillingResultJson(response);
            record.setHistogramDataJson(objectMapper.writeValueAsString(histograms));
            record.setDatasourceName(datasourceName);
            record.setLlmName(llmName);

            SqlFillingRecord savedRecord = fillingRecordRepository.save(record);
            return savedRecord;
        } catch (Exception e) {
            throw new AgentException(AgentErrorCode.JSON_PARSE_FAILED, "保存填充记录失败", e);
        }
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

    /**
     * 批量查询填充记录
     */
    public FillingRecordsResponse getFillingRecords(List<String> mapperIds, 
                                                     String datasourceName, 
                                                     String llmName) {
        logger.info("批量查询填充记录 - mapperIds数量: {}, datasourceName: {}, llmName: {}", 
                mapperIds != null ? mapperIds.size() : 0, datasourceName, llmName);
        
        FillingRecordsResponse response = new FillingRecordsResponse();
        
        for (String mapperId : mapperIds) {
            Optional<SqlFillingRecord> recordOpt = fillingRecordRepository
                    .findByMapperIdAndDatasourceNameAndLlmName(mapperId, datasourceName, llmName);
            if (recordOpt.isPresent()) {
                SqlFillingRecord record = recordOpt.get();
                response.getSqlFillingRecords().add(record);
            }
        }
        
        return response;
    }
}

