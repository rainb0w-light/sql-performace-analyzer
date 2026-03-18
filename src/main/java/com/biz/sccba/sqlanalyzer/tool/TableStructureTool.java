package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.service.SqlExecutionPlanService;
import com.biz.sccba.sqlanalyzer.data.TableStructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 表结构查询工具 - 用于获取数据库表结构信息
 */
@Component
public class TableStructureTool {

    private final SqlExecutionPlanService executionPlanService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TableStructureTool(SqlExecutionPlanService executionPlanService) {
        this.executionPlanService = executionPlanService;
    }

    /**
     * 获取表结构信息
     * @param tableName 表名
     * @param datasourceName 数据源名称
     * @return 表结构信息的 JSON 字符串
     */
    @Tool(name = "get_table_structure", description = "获取数据库表结构信息，包括列定义、索引和统计信息")
    public String getTableStructure(
            @ToolParam(name = "tableName", description = "要查询的表名") String tableName,
            @ToolParam(name = "datasourceName", description = "数据源名称") String datasourceName) {
        try {
            List<TableStructure> structures = executionPlanService.getTableStructuresByNames(
                    List.of(tableName), datasourceName);
            if (structures.isEmpty()) {
                return "{\"error\": \"表不存在：" + tableName + "\"}";
            }
            return objectMapper.writeValueAsString(structures.get(0));
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
