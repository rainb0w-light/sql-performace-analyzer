package com.biz.sccba.sqlanalyzer.request;

import lombok.Data;
import java.util.List;

/**
 * 多 SQL Agent 分析请求
 */
@Data
public class MultiSqlAgentRequest {
    
    /**
     * SQL 项列表，每个项包含 SQL 和对应的 mapperId
     */
    private List<SqlItem> sqlItems;
    
    /**
     * 数据源名称
     */
    private String datasourceName;
    
    /**
     * LLM 名称
     */
    private String llmName;
    
    /**
     * SQL 项
     */
    @Data
    public static class SqlItem {
        /**
         * SQL 语句
         */
        private String sql;
        
        /**
         * Mapper ID（格式：namespace.statementId）
         * 如果为空，将使用默认格式生成
         */
        private String mapperId;
    }
}

