package com.biz.sccba.sqlanalyzer.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL执行步骤配置
 */
@Data
public class SqlExecutionStep {
    /**
     * 步骤ID（可选，用于标识和结果追踪）
     */
    private String id;

    /**
     * SQL语句列表（支持在一个step中执行多个SQL）
     * 如果设置了此字段，将优先使用此字段；否则使用 sql 字段（向后兼容）
     */
    private List<String> sqls;

    /**
     * SQL语句（单个SQL，向后兼容）
     * 如果sqls为空，则使用此字段
     */
    private String sql;

    /**
     * 事务隔离级别（可选）
     * 支持：READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE
     */
    private String isolationLevel;

    /**
     * 获取要执行的SQL列表
     * 优先返回 sqls，如果为空则返回包含单个 sql 的列表
     */
    public List<String> getSqlList() {
        if (sqls != null && !sqls.isEmpty()) {
            return new ArrayList<>(sqls);
        } else if (sql != null && !sql.trim().isEmpty()) {
            return List.of(sql);
        } else {
            return new ArrayList<>();
        }
    }
}

