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
 * 修改表结构工具
 *
 * 执行 ALTER TABLE 语句，需要用户确认
 */
@Component
public class AlterTableTool {

    private final DataSourceManagerService dataSourceService;
    private final DdlConfirmationManager confirmationManager;
    private final TransactionLogManager transactionLogManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlterTableTool(DataSourceManagerService dataSourceService,
                          DdlConfirmationManager confirmationManager,
                          TransactionLogManager transactionLogManager) {
        this.dataSourceService = dataSourceService;
        this.confirmationManager = confirmationManager;
        this.transactionLogManager = transactionLogManager;
    }

    /**
     * 修改表结构
     *
     * @param datasourceName 数据源名称
     * @param schema         模式名
     * @param tableName      表名
     * @param alterClause    ALTER TABLE 子句
     * @param sessionId      会话 ID
     * @return 执行结果 JSON
     */
    @Tool(name = "alter_table", description = "修改表结构（需要用户确认）")
    public String alterTable(
            @ToolParam(name = "datasource", description = "数据源名称", required = true) String datasourceName,
            @ToolParam(name = "schema", description = "模式名", required = true) String schema,
            @ToolParam(name = "tableName", description = "表名", required = true) String tableName,
            @ToolParam(name = "alterClause", description = "ALTER TABLE 子句", required = true) String alterClause,
            @ToolParam(name = "sessionId", description = "会话 ID", required = true) String sessionId) {
        System.out.println("[AlterTableTool] 收到修改表结构请求：" + datasourceName + "." + schema + "." + tableName + " - " + alterClause);

        try {
            // 构建完整的 ALTER TABLE 语句
            String ddl = buildAlterTableSql(tableName, alterClause, schema);

            // 创建确认请求
            DdlConfirmationRequest request = DdlConfirmationRequest.builder()
                .sessionId(sessionId)
                .operationType(DdlOperationType.ALTER_TABLE)
                .ddlStatement(ddl)
                .description(String.format("修改表 %s 结构：%s", tableName, alterClause))
                .impactAnalysis(String.format("可能影响：1. 表结构变更 2. 可能需要锁表 3. 可能影响依赖此表的查询"))
                .rollbackStatement(buildRollbackSql(tableName, alterClause, schema))
                .metadata(Map.of(
                    "tableName", tableName,
                    "alterClause", alterClause,
                    "schema", schema
                ))
                .build();

            // 创建确认并等待用户响应
            String confirmationId = confirmationManager.createConfirmation(request);
            System.out.println("[AlterTableTool] 等待用户确认 DDL 操作：" + confirmationId);

            // 阻塞等待用户响应
            DdlConfirmationResponse response = confirmationManager.waitForConfirmation(confirmationId);

            if (!response.isConfirmed()) {
                System.out.println("[AlterTableTool] 用户拒绝修改表结构：" + alterClause + " 原因：" + response.getComment());
                return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", "用户拒绝执行：修改表结构被取消 - " + response.getComment()
                ));
            }

            // 用户已确认，执行 DDL
            System.out.println("[AlterTableTool] 用户已确认，开始执行修改表结构：" + alterClause);

            // 记录事务日志（执行前记录，用于回滚）
            TransactionLogEntry logEntry = TransactionLogEntry.builder()
                .sessionId(sessionId)
                .actionType(ActionType.ALTER_TABLE)
                .actionData(Map.of(
                    "ddl", ddl,
                    "tableName", tableName,
                    "alterClause", alterClause,
                    "schema", schema
                ))
                .rollbackData(Map.of(
                    "rollbackSql", buildRollbackSql(tableName, alterClause, schema),
                    "tableName", tableName,
                    "schema", schema
                ))
                .build();
            transactionLogManager.logTransaction(logEntry);

            executeDdl(datasourceName, ddl);

            System.out.println("[AlterTableTool] 表结构修改成功：" + alterClause);

            return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "tableName", tableName,
                "alterClause", alterClause,
                "ddl", ddl,
                "confirmationId", confirmationId
            ));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[AlterTableTool] DDL 确认被中断：" + e.getMessage());
            try {
                return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", "操作被中断"
                ));
            } catch (Exception ex) {
                return "{\"error\": \"操作被中断\"}";
            }
        } catch (Exception e) {
            System.err.println("[AlterTableTool] 修改表结构失败：" + e.getMessage());
            try {
                return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", "修改表结构失败：" + e.getMessage()
                ));
            } catch (Exception ex) {
                return "{\"error\": \"修改表结构失败\"}";
            }
        }
    }

    /**
     * 构建 ALTER TABLE SQL
     */
    private String buildAlterTableSql(String tableName, String alterClause, String schema) {
        StringBuilder sb = new StringBuilder("ALTER TABLE ");
        if (schema != null && !schema.isEmpty()) {
            sb.append(schema).append(".");
        }
        sb.append(tableName);
        sb.append(" ").append(alterClause);
        return sb.toString();
    }

    /**
     * 构建回滚 SQL（简单实现，实际需要根据 alterClause 类型生成）
     */
    private String buildRollbackSql(String tableName, String alterClause, String schema) {
        String upperClause = alterClause.toUpperCase();
        if (upperClause.startsWith("ADD COLUMN")) {
            // ADD COLUMN 的回滚是 DROP COLUMN
            String columnName = extractColumnName(alterClause);
            return buildAlterTableSql(tableName, "DROP COLUMN " + columnName, schema);
        } else if (upperClause.startsWith("DROP COLUMN")) {
            // DROP COLUMN 无法简单回滚
            return "-- 无法自动回滚 DROP COLUMN 操作";
        } else if (upperClause.startsWith("MODIFY COLUMN") || upperClause.startsWith("ALTER COLUMN")) {
            // 列修改无法自动回滚
            return "-- 需要手动回滚列修改";
        }
        return "-- 需要手动回滚此操作";
    }

    /**
     * 提取列名（简单实现）
     */
    private String extractColumnName(String alterClause) {
        // 简单提取 ADD COLUMN 后的列名
        int idx = alterClause.toUpperCase().indexOf("COLUMN");
        if (idx >= 0) {
            String rest = alterClause.substring(idx + 6).trim();
            return rest.split("\\s+")[0];
        }
        return "unknown_column";
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
            System.out.println("[AlterTableTool] DDL 执行成功：" + truncate(ddl, 100));
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