package com.biz.sccba.sqlanalyzer.model.request;

import lombok.Data;
import java.util.List;

/**
 * 批量查询填充记录请求
 */
@Data
public class FillingRecordsRequest {
    
    /**
     * Mapper ID 列表
     */
    private List<String> mapperIds;
    
    /**
     * 数据源名称
     */
    private String datasourceName;
    
    /**
     * LLM 名称
     */
    private String llmName;
}

