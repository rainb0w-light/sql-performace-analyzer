package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
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
                                                             List<ExecutionPlan> executionPlanRecords) {
        logger.info("阶段3：执行计划解析阶段 - mapperId: {}", mapperId);

        List<SqlPlanAnalysisResult> results = new ArrayList<>();
        Map<String, List<ExecutionPlan>> sqlGroups = executionPlanRecords.stream()
                .collect(Collectors.groupingBy(ExecutionPlan::getOriginalSql));

        for (Map.Entry<String, List<ExecutionPlan>> entry : sqlGroups.entrySet()) {
            String sql = entry.getKey();
            List<ExecutionPlan> records = entry.getValue();
            if (records.isEmpty()) {
                continue;
            }

            // 确保字段已解析
            for (ExecutionPlan plan : records) {
                if (plan.getKey() == null && plan.getRawJson() != null && !plan.getRawJson().isEmpty()) {
                    plan.parseFromRawJson();
                }
            }

            boolean allNoIndex = records.stream()
                    .allMatch(r -> r.getKey() == null || r.getKey().isEmpty());
            if (allNoIndex) {
                SqlPlanAnalysisResult result = new SqlPlanAnalysisResult();
                result.setMapperId(mapperId);
                result.setOriginalSql(sql);
                result.setCategory(SqlPlanAnalysisResult.CATEGORY_NO_INDEX);
                result.setPlanShiftDetailsJson(null);

                List<Long> recordIds = records.stream()
                        .map(ExecutionPlan::getId)
                        .collect(Collectors.toList());
                result.setExecutionPlanRecordIds(writeIds(recordIds));

                results.add(planAnalysisResultRepository.save(result));
                logger.info("SQL 分类为 NO_INDEX: {}", sql);
                continue;
            }

            Set<String> indexNames = records.stream()
                    .map(r -> r.getKey() != null && !r.getKey().isEmpty() ? r.getKey() : "FULL_TABLE_SCAN")
                    .collect(Collectors.toSet());
            boolean hasPlanShift = indexNames.size() > 1;
            if (hasPlanShift) {
                Map<String, Object> shiftDetails = new HashMap<>();
                shiftDetails.put("differentIndexes", new ArrayList<>(indexNames));
                shiftDetails.put("scenarioCount", records.size());

                List<Map<String, Object>> scenarioIndexes = new ArrayList<>();
                for (ExecutionPlan record : records) {
                    Map<String, Object> scenarioInfo = new HashMap<>();
                    scenarioInfo.put("indexName", record.getKey() != null && !record.getKey().isEmpty() ?
                            record.getKey() : "FULL_TABLE_SCAN");
                    scenarioInfo.put("accessType", record.getAccessType());
                    scenarioInfo.put("rowsExamined", record.getRowsExaminedPerScan());
                    scenarioIndexes.add(scenarioInfo);
                }
                shiftDetails.put("scenarioIndexes", scenarioIndexes);

                SqlPlanAnalysisResult result = new SqlPlanAnalysisResult();
                result.setMapperId(mapperId);
                result.setOriginalSql(sql);
                result.setCategory(SqlPlanAnalysisResult.CATEGORY_PLAN_SHIFT);
                result.setPlanShiftDetailsJson(writeValue(shiftDetails));

                List<Long> recordIds = records.stream()
                        .map(ExecutionPlan::getId)
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

