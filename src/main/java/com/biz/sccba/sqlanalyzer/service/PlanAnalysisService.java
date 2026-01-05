package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.SqlExecutionPlanRecord;
import com.biz.sccba.sqlanalyzer.model.SqlPlanAnalysisResult;
import com.biz.sccba.sqlanalyzer.repository.SqlPlanAnalysisResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 阶段3：执行计划解析。
 */
@Service
public class PlanAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(PlanAnalysisService.class);

    @Autowired
    private SqlPlanAnalysisResultRepository planAnalysisResultRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SqlPlanAnalysisResult> analyzeExecutionPlans(String mapperId,
                                                             String originalSql,
                                                             List<SqlExecutionPlanRecord> executionPlanRecords) {
        logger.info("阶段3：执行计划解析阶段 - mapperId: {}", mapperId);

        List<SqlPlanAnalysisResult> results = new ArrayList<>();
        Map<String, List<SqlExecutionPlanRecord>> sqlGroups = executionPlanRecords.stream()
                .collect(Collectors.groupingBy(SqlExecutionPlanRecord::getOriginalSql));

        for (Map.Entry<String, List<SqlExecutionPlanRecord>> entry : sqlGroups.entrySet()) {
            String sql = entry.getKey();
            List<SqlExecutionPlanRecord> records = entry.getValue();
            if (records.isEmpty()) {
                continue;
            }

            boolean allNoIndex = records.stream()
                    .allMatch(r -> r.getIndexName() == null || r.getIndexName().isEmpty());
            if (allNoIndex) {
                SqlPlanAnalysisResult result = new SqlPlanAnalysisResult();
                result.setMapperId(mapperId);
                result.setOriginalSql(sql);
                result.setCategory(SqlPlanAnalysisResult.CATEGORY_NO_INDEX);
                result.setPlanShiftDetailsJson(null);

                List<Long> recordIds = records.stream()
                        .map(SqlExecutionPlanRecord::getId)
                        .collect(Collectors.toList());
                result.setExecutionPlanRecordIds(writeIds(recordIds));

                results.add(planAnalysisResultRepository.save(result));
                logger.info("SQL 分类为 NO_INDEX: {}", sql);
                continue;
            }

            Set<String> indexNames = records.stream()
                    .map(r -> r.getIndexName() != null ? r.getIndexName() : "FULL_TABLE_SCAN")
                    .collect(Collectors.toSet());
            boolean hasPlanShift = indexNames.size() > 1;
            if (hasPlanShift) {
                Map<String, Object> shiftDetails = new HashMap<>();
                shiftDetails.put("differentIndexes", new ArrayList<>(indexNames));
                shiftDetails.put("scenarioCount", records.size());

                List<Map<String, Object>> scenarioIndexes = new ArrayList<>();
                for (SqlExecutionPlanRecord record : records) {
                    Map<String, Object> scenarioInfo = new HashMap<>();
                    scenarioInfo.put("scenarioName", record.getScenarioName());
                    scenarioInfo.put("indexName", record.getIndexName() != null ?
                            record.getIndexName() : "FULL_TABLE_SCAN");
                    scenarioInfo.put("accessType", record.getAccessType());
                    scenarioInfo.put("rowsExamined", record.getRowsExamined());
                    scenarioIndexes.add(scenarioInfo);
                }
                shiftDetails.put("scenarioIndexes", scenarioIndexes);

                SqlPlanAnalysisResult result = new SqlPlanAnalysisResult();
                result.setMapperId(mapperId);
                result.setOriginalSql(sql);
                result.setCategory(SqlPlanAnalysisResult.CATEGORY_PLAN_SHIFT);
                result.setPlanShiftDetailsJson(writeValue(shiftDetails));

                List<Long> recordIds = records.stream()
                        .map(SqlExecutionPlanRecord::getId)
                        .collect(Collectors.toList());
                result.setExecutionPlanRecordIds(writeIds(recordIds));

                results.add(planAnalysisResultRepository.save(result));
                logger.info("SQL 分类为 PLAN_SHIFT: {}, 不同索引: {}", sql, indexNames);
            }
        }

        logger.info("完成执行计划解析，共 {} 个结果", results.size());
        return results;
    }

    private String writeIds(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}

