package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.domain.stats.ColumnHistogram;
import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.model.SqlExecutionPlanRecord;
import com.biz.sccba.sqlanalyzer.model.SqlFillingRecord;
import com.biz.sccba.sqlanalyzer.model.TableStructure;
import com.biz.sccba.sqlanalyzer.repository.SqlExecutionPlanRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 执行计划与表统计的门面，负责缓存与存储记录。
 */
@Service
public class ExecutionPlanServiceFacade {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionPlanServiceFacade.class);

    @Autowired
    private SqlExecutionPlanService executionPlanService;

    @Autowired
    private SqlExecutionPlanRecordRepository executionPlanRecordRepository;

    private final Map<String, TableStructure> tableStructureCache = new ConcurrentHashMap<>();
    private final Map<String, List<ColumnHistogram>> histogramCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ColumnHistogram> getHistogramData(String sql, String datasourceName) {
        List<String> tables = executionPlanService.parseTableNames(sql);
        String cacheKey = datasourceName + "::" + String.join(",", tables);
        return histogramCache.computeIfAbsent(cacheKey, k -> {
            try {
                return executionPlanService.getHistogramDataForSql(sql, datasourceName);
            } catch (Exception e) {
                logger.warn("获取直方图数据失败: {}", e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    public List<TableStructure> getTableStructures(String sql, String datasourceName) {
        List<String> tableNames = executionPlanService.parseTableNames(sql);
        List<TableStructure> result = new ArrayList<>();
        for (String tableName : tableNames) {
            String cacheKey = datasourceName + "::" + tableName;
            TableStructure structure = tableStructureCache.computeIfAbsent(cacheKey, k -> {
                try {
                    return executionPlanService.getTableStructuresByNames(
                            Collections.singletonList(tableName), datasourceName)
                            .stream().findFirst().orElse(null);
                } catch (Exception e) {
                    logger.warn("获取表结构失败, table={}, msg={}", tableName, e.getMessage());
                    return null;
                }
            });
            if (structure != null) {
                result.add(structure);
            }
        }
        return result;
    }

    /**
     * 生成执行计划记录并持久化。
     */
    public List<SqlExecutionPlanRecord> generateExecutionPlans(SqlFillingRecord fillingRecord,
                                                               List<String> scenarioNames,
                                                               List<String> filledSqls,
                                                               String datasourceName) {
        List<SqlExecutionPlanRecord> records = new ArrayList<>();
        for (int i = 0; i < filledSqls.size(); i++) {
            String scenarioName = scenarioNames.get(i);
            String filledSql = filledSqls.get(i);
            try {
                logger.info("为场景生成执行计划: {}", scenarioName);
                ExecutionPlan plan = executionPlanService.getExecutionPlan(filledSql, datasourceName);
                if (plan == null) {
                    logger.warn("场景 {} 的执行计划获取失败", scenarioName);
                    continue;
                }

                String accessType = null;
                String indexName = null;
                Long rowsExamined = null;
                if (plan.getQueryBlock() != null && plan.getQueryBlock().getTable() != null) {
                    ExecutionPlan.TableInfo table = plan.getQueryBlock().getTable();
                    accessType = table.getAccessType();
                    indexName = table.getKey();
                    rowsExamined = table.getRowsExaminedPerScan();
                }

                SqlExecutionPlanRecord record = new SqlExecutionPlanRecord();
                record.setMapperId(fillingRecord.getMapperId());
                record.setFillingRecordId(fillingRecord.getId());
                record.setScenarioName(scenarioName);
                record.setOriginalSql(fillingRecord.getOriginalSql());
                record.setFilledSql(filledSql);
                record.setExecutionPlanJson(objectMapper.writeValueAsString(plan));
                record.setAccessType(accessType);
                record.setIndexName(indexName);
                record.setRowsExamined(rowsExamined);

                SqlExecutionPlanRecord saved = executionPlanRecordRepository.save(record);
                records.add(saved);
            } catch (Exception e) {
                logger.warn("场景 {} 的执行计划生成失败: {}", scenarioName, e.getMessage());
            }
        }
        logger.info("完成 {} 个场景的执行计划生成", records.size());
        return records;
    }

    /**
     * 提取执行计划使用的索引集合，供分析时使用。
     */
    public Set<String> extractIndexes(List<SqlExecutionPlanRecord> records) {
        return records.stream()
                .map(r -> r.getIndexName() != null ? r.getIndexName() : "FULL_TABLE_SCAN")
                .collect(Collectors.toSet());
    }
}

