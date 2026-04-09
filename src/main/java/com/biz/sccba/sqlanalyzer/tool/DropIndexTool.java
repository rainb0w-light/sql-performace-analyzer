package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.memory.DdlConfirmationManager;
import com.biz.sccba.sqlanalyzer.memory.TransactionLogManager;
import com.biz.sccba.sqlanalyzer.model.TransactionLogEntry;
import com.biz.sccba.sqlanalyzer.model.TransactionLogEntry.ActionType;
import com.biz.sccba.sqlanalyzer.model.agent.DdlConfirmationRequest;
import com.biz.sccba.sqlanalyzer.model.agent.DdlConfirmationRequest.DdlOperationType;
import com.biz.sccba.sqlanalyzer.model.agent.DdlConfirmationResponse;
import com.biz.sccba.sqlanalyzer.service.DataSourceManagerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

/**
 * 删除索引工具
 *
 * 执行 DROP INDEX 语句，需要用户确认
 */
@Component
public class DropIndexTool {

    private final DataSourceManagerService dataSourceService;
    private final DdlConfirmationManager confirmationManager;
    private final TransactionLogManager transactionLogManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DropIndexTool(DataSourceManagerService dataSourceService,
                         DdlConfirmationManager confirmationManager,
                         TransactionLogManager transactionLogManager) {
        this.dataSourceService = dataSourceService;
        this.confirmationManager = confirmationManager;
        this.transactionLogManager = transactionLogManager;
    }

    /**
     * 删除索引
     *
     * @param datasourceName 数据源名称
     * @param schema         模式名
     * @param tableName      表名
     * @param indexName      索引名
     * @param sessionId      会话 ID
     * @return 执行结果 JSON
     */
    @Tool(name = "drop_index", description = "删除数据库索引（需要用户确认）")
    public String dropIndex(
            @ToolParam(name = "datasource", description = "数据源名称", required = true) String datasourceName,
            @ToolParam(name = "schema", description = "模式名", required = true) String schema,
            @ToolParam(name = "tableName", description = "表名", required = true) String tableName,
            @ToolParam(name = "indexName", description = "索引名", required = true) String indexName,
            @ToolParam(name = "sessionId", description = "会话 ID", required = true) String sessionId) {
        System.out.println("[DropIndexTool] 收到删除索引请求：" + datasourceName + "." + schema + "." + tableName);

        try {
            // 构建 DDL 语句
            String ddl = buildDropIndexSql(indexName, schema);

            // 创建确认请求
            DdlConfirmationRequest request = DdlConfirmationRequest.builder()
                .sessionId(sessionId)
                .operationType(DdlOperationType.DROP_INDEX)
                .ddlStatement(ddl)
                .description(String.format("删除表 %s 上的索引：%s", tableName, indexName))
                .impactAnalysis(String.format("可能影响：1. 依赖此索引的查询性能可能下降 2. 存储空间释放"))
                .rollbackStatement(String.format("-- 需要重新创建索引：CREATE INDEX %s ON %s.%s (需要指定列)",
                    indexName, schema != null ? schema + "." : "", tableName))
                .metadata(Map.of(
                    "tableName", tableName,
                    "indexName", indexName,
                    "schema", schema
                ))
                .build();

            // 创建确认并等待用户响应
            String confirmationId = confirmationManager.createConfirmation(request);
            System.out.println("[DropIndexTool] 等待用户确认 DDL 操作：" + confirmationId);

            // 阻塞等待用户响应
            DdlConfirmationResponse response = confirmationManager.waitForConfirmation(confirmationId);

            if (!response.isConfirmed()) {
                System.out.println("[DropIndexTool] 用户拒绝删除索引：" + indexName + " 原因：" + response.getComment());
                return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", "用户拒绝执行：删除索引被取消 - " + response.getComment()
                ));
            }

            // 用户已确认，执行 DDL
            System.out.println("[DropIndexTool] 用户已确认，开始执行删除索引：" + indexName);

            // 记录事务日志（执行前记录，用于回滚）
            TransactionLogEntry logEntry = TransactionLogEntry.builder()
                .sessionId(sessionId)
                .actionType(ActionType.DROP_INDEX)
                .actionData(Map.of(
                    "ddl", ddl,
                    "tableName", tableName,
                    "indexName", indexName,
                    "schema", schema
                ))
                .rollbackData(Map.of(
                    "indexName", indexName,
                    "tableName", tableName,
                    "schema", schema
                ))
                .build();
            transactionLogManager.logTransaction(logEntry);

            executeDdl(datasourceName, ddl);

            System.out.println("[DropIndexTool] 索引删除成功：" + indexName);

            return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "indexName", indexName,
                "tableName", tableName,
                "ddl", ddl,
                "confirmationId", confirmationId
            ));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[DropIndexTool] DDL 确认被中断：" + e.getMessage());
            try {
                return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", "操作被中断"
                ));
            } catch (Exception ex) {
                return "{\"error\": \"操作被中断\"}";
            }
        } catch (Exception e) {
            System.err.println("[DropIndexTool] 删除索引失败：" + e.getMessage());
            try {
                return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", "删除索引失败：" + e.getMessage()
                ));
            } catch (Exception ex) {
                return "{\"error\": \"删除索引失败\"}";
            }
        }
    }

    /**
     * 构建 DROP INDEX SQL
     */
    private String buildDropIndexSql(String indexName, String schema) {
        StringBuilder sb = new StringBuilder("DROP INDEX ");
        if (schema != null && !schema.isEmpty()) {
            sb.append(schema).append(".");
        }
        sb.append(indexName);
        return sb.toString();
    }

    /**
     * 执行 DDL 语句
     */
    @Transactional
    private void executeDdl(String datasourceName, String ddl) throws SQLException {
        DataSource dataSource = dataSourceService.getDataSource(datasourceName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(ddl)) {
            stmt.executeUpdate();
            System.out.println("[DropIndexTool] DDL 执行成功：" + truncate(ddl, 100));
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }
}