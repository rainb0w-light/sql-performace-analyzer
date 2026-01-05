package com.biz.sccba.sqlanalyzer.model.request;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 批量查询填充记录响应
 */
@Data
public class FillingRecordsResponse {
    
    /**
     * Mapper ID 到填充记录的映射
     * Key: mapperId
     * Value: 填充记录数据（包含 scenarios）
     */
    private Map<String, FillingRecordData> records;
    
    /**
     * 填充记录数据
     */
    @Data
    public static class FillingRecordData {
        /**
         * Mapper ID
         */
        private String mapperId;
        
        /**
         * 原始 SQL
         */
        private String originalSql;
        
        /**
         * 场景列表
         */
        private List<ScenarioData> scenarios;
        
        /**
         * 创建时间
         */
        private String createdAt;
    }
    
    /**
     * 场景数据
     */
    @Data
    public static class ScenarioData {
        /**
         * 场景名称
         */
        private String scenarioName;
        
        /**
         * 填充后的 SQL
         */
        private String filledSql;
        
        /**
         * 参数值（可选）
         */
        private Map<String, Object> parameters;
    }
}

