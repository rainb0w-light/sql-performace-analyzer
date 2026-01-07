package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.domain.stats.ColumnHistogram;
import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.model.SqlFillingRecord;
import com.biz.sccba.sqlanalyzer.data.TableStructure;
import com.biz.sccba.sqlanalyzer.repository.ExecutionPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行计划与表统计的门面，负责缓存与存储记录。
 */
@Service
public class ExecutionPlanServiceFacade {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionPlanServiceFacade.class);

    @Autowired
    private SqlExecutionPlanService executionPlanService;

    @Autowired
    private ExecutionPlanRepository executionPlanRepository;

    private final Map<String, TableStructure> tableStructureCache = new ConcurrentHashMap<>();
    private final Map<String, List<ColumnHistogram>> histogramCache = new ConcurrentHashMap<>();

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
    public List<ExecutionPlan> generateExecutionPlans(SqlFillingRecord fillingRecord,
                                                       List<String> scenarioNames,
                                                       List<String> filledSqls,
                                                       String datasourceName) {
        List<ExecutionPlan> records = new ArrayList<>();
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

                // 设置业务字段
                plan.setOriginalSql(fillingRecord.getOriginalSql());
                plan.setFilledSql(filledSql);
                // rawJson 已经在 getExecutionPlan 中设置
                // 字段已经通过 parseFromJsonNode 解析

                ExecutionPlan saved = executionPlanRepository.save(plan);
                records.add(saved);
            } catch (Exception e) {
                logger.warn("场景 {} 的执行计划生成失败: {}", scenarioName, e.getMessage());
            }
        }
        logger.info("完成 {} 个场景的执行计划生成", records.size());
        return records;
    }

}

