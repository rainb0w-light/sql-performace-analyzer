package com.biz.sccba.sqlanalyzer.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "sql_execution_plan_record")
@Data
public class ExecutionPlan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 填充前带参数的 SQL
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalSql;

    /**
     * 填充后的 SQL
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String filledSql;

    /**
     * 原始JSON字符串
     */
    @Lob
    @Column(columnDefinition = "CLOB")
    private String rawJson;

    // ========== QueryBlock 字段 ==========
    
    /**
     * Select ID（来自 query_block.select_id）
     */
    @Transient
    @JsonIgnore
    private Integer selectId;

    /**
     * 查询成本信息（来自 query_block.cost_info，字符串格式）
     */
    @Transient
    @JsonIgnore
    private String costInfo;

    // ========== TableInfo 字段 ==========
    
    /**
     * 表名（来自 query_block.table.table_name）
     */
    @Transient
    @JsonIgnore
    private String tableName;

    /**
     * 访问类型（来自 query_block.table.access_type）
     */
    @Transient
    @JsonIgnore
    private String accessType;

    /**
     * 使用的索引（来自 query_block.table.key）
     */
    @Transient
    @JsonIgnore
    private String key;

    /**
     * 可能的索引（来自 query_block.table.possible_keys）
     */
    @Transient
    @JsonIgnore
    private String[] possibleKeys;

    /**
     * 使用的索引部分（来自 query_block.table.used_key_parts）
     */
    @Transient
    @JsonIgnore
    private String[] usedKeyParts;

    /**
     * 索引长度（来自 query_block.table.key_length）
     */
    @Transient
    @JsonIgnore
    private String keyLength;

    /**
     * 引用（来自 query_block.table.ref）
     */
    @Transient
    @JsonIgnore
    private String[] ref;

    /**
     * 过滤比例（来自 query_block.table.filtered）
     */
    @Transient
    @JsonIgnore
    private String filtered;

    /**
     * 是否使用索引（来自 query_block.table.using_index）
     */
    @Transient
    @JsonIgnore
    private Boolean usingIndex;

    /**
     * 扫描行数（来自 query_block.table.rows_examined_per_scan）
     */
    @Transient
    @JsonIgnore
    private Long rowsExaminedPerScan;

    /**
     * 产生行数（来自 query_block.table.rows_produced_per_join）
     */
    @Transient
    @JsonIgnore
    private Long rowsProducedPerJoin;

    /**
     * 使用的列（来自 query_block.table.used_columns）
     */
    @Transient
    @JsonIgnore
    private String[] usedColumns;

    // ========== CostInfo 字段（来自 query_block.table.cost_info）==========
    
    /**
     * 读取成本（来自 query_block.table.cost_info.read_cost）
     */
    @Transient
    @JsonIgnore
    private String readCost;

    /**
     * 评估成本（来自 query_block.table.cost_info.eval_cost）
     */
    @Transient
    @JsonIgnore
    private String evalCost;

    /**
     * 前缀成本（来自 query_block.table.cost_info.prefix_cost）
     */
    @Transient
    @JsonIgnore
    private String prefixCost;

    /**
     * 数据读取（来自 query_block.table.cost_info.data_read_per_join）
     */
    @Transient
    @JsonIgnore
    private String dataReadPerJoin;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * 从 rawJson 解析并填充字段
     */
    public void parseFromRawJson() {
        if (rawJson == null || rawJson.isEmpty()) {
            return;
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(rawJson);
            parseFromJsonNode(jsonNode);
        } catch (Exception e) {
            // 解析失败时忽略
        }
    }

    /**
     * 从 JsonNode 解析并填充字段
     */
    public void parseFromJsonNode(JsonNode jsonNode) {
        if (jsonNode == null) {
            return;
        }
        
        JsonNode queryBlockNode = jsonNode.path("query_block");
        if (queryBlockNode.isMissingNode()) {
            return;
        }

        // 解析 QueryBlock 字段
        if (queryBlockNode.has("select_id")) {
            this.selectId = queryBlockNode.get("select_id").asInt();
        }
        if (queryBlockNode.has("cost_info")) {
            JsonNode costInfoNode = queryBlockNode.get("cost_info");
            if (costInfoNode.isTextual()) {
                this.costInfo = costInfoNode.asText();
            }
        }

        // 解析 TableInfo 字段
        if (queryBlockNode.has("table")) {
            JsonNode tableNode = queryBlockNode.get("table");
            
            if (tableNode.has("table_name")) {
                this.tableName = tableNode.get("table_name").asText();
            }
            if (tableNode.has("access_type")) {
                this.accessType = tableNode.get("access_type").asText();
            }
            if (tableNode.has("key")) {
                this.key = tableNode.get("key").asText();
            }
            if (tableNode.has("possible_keys")) {
                JsonNode possibleKeysNode = tableNode.get("possible_keys");
                if (possibleKeysNode.isArray()) {
                    String[] keys = new String[possibleKeysNode.size()];
                    for (int i = 0; i < possibleKeysNode.size(); i++) {
                        keys[i] = possibleKeysNode.get(i).asText();
                    }
                    this.possibleKeys = keys;
                }
            }
            if (tableNode.has("used_key_parts")) {
                JsonNode usedKeyPartsNode = tableNode.get("used_key_parts");
                if (usedKeyPartsNode.isArray()) {
                    String[] keyParts = new String[usedKeyPartsNode.size()];
                    for (int i = 0; i < usedKeyPartsNode.size(); i++) {
                        keyParts[i] = usedKeyPartsNode.get(i).asText();
                    }
                    this.usedKeyParts = keyParts;
                }
            }
            if (tableNode.has("key_length")) {
                this.keyLength = tableNode.get("key_length").asText();
            }
            if (tableNode.has("ref")) {
                JsonNode refNode = tableNode.get("ref");
                if (refNode.isArray()) {
                    String[] refs = new String[refNode.size()];
                    for (int i = 0; i < refNode.size(); i++) {
                        refs[i] = refNode.get(i).asText();
                    }
                    this.ref = refs;
                }
            }
            if (tableNode.has("filtered")) {
                this.filtered = tableNode.get("filtered").asText();
            }
            if (tableNode.has("using_index")) {
                this.usingIndex = tableNode.get("using_index").asBoolean();
            }
            if (tableNode.has("rows_examined_per_scan")) {
                this.rowsExaminedPerScan = tableNode.get("rows_examined_per_scan").asLong();
            }
            if (tableNode.has("rows_produced_per_join")) {
                this.rowsProducedPerJoin = tableNode.get("rows_produced_per_join").asLong();
            }
            if (tableNode.has("used_columns")) {
                JsonNode usedColumnsNode = tableNode.get("used_columns");
                if (usedColumnsNode.isArray()) {
                    String[] columns = new String[usedColumnsNode.size()];
                    for (int i = 0; i < usedColumnsNode.size(); i++) {
                        columns[i] = usedColumnsNode.get(i).asText();
                    }
                    this.usedColumns = columns;
                }
            }

            // 解析 CostInfo 字段（来自 table.cost_info）
            if (tableNode.has("cost_info")) {
                JsonNode costInfoNode = tableNode.get("cost_info");
                if (costInfoNode.has("read_cost")) {
                    this.readCost = costInfoNode.get("read_cost").asText();
                }
                if (costInfoNode.has("eval_cost")) {
                    this.evalCost = costInfoNode.get("eval_cost").asText();
                }
                if (costInfoNode.has("prefix_cost")) {
                    this.prefixCost = costInfoNode.get("prefix_cost").asText();
                }
                if (costInfoNode.has("data_read_per_join")) {
                    this.dataReadPerJoin = costInfoNode.get("data_read_per_join").asText();
                }
            }
        }
    }

    /**
     * 将执行计划转换为Markdown格式
     * @return Markdown格式的字符串
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# SQL 执行计划\n\n");
        
        // 如果字段未解析，先解析
        if (selectId == null && rawJson != null && !rawJson.isEmpty()) {
            parseFromRawJson();
        }
        
        if (selectId != null || tableName != null) {
            sb.append("## 查询块信息\n\n");
            sb.append("| 字段 | 值 |\n");
            sb.append("|------|-----|\n");
            
            if (selectId != null) {
                sb.append("| Select ID | ").append(selectId).append(" |\n");
            }
            
            if (costInfo != null) {
                sb.append("| 查询成本 | ").append(costInfo).append(" |\n");
            }
            
            sb.append("\n");
            
            // 表信息
            if (tableName != null) {
                sb.append("## 表信息\n\n");
                sb.append("| 字段 | 值 |\n");
                sb.append("|------|-----|\n");
                
                sb.append("| 表名 | ").append(tableName).append(" |\n");
                
                if (accessType != null) {
                    sb.append("| 访问类型 | ").append(accessType).append(" |\n");
                }
                
                if (key != null && !key.isEmpty()) {
                    sb.append("| 使用的索引 | ").append(key).append(" |\n");
                } else {
                    sb.append("| 使用的索引 | 无 |\n");
                }
                
                if (possibleKeys != null && possibleKeys.length > 0) {
                    sb.append("| 可能的索引 | ").append(String.join(", ", possibleKeys)).append(" |\n");
                }
                
                if (usedKeyParts != null && usedKeyParts.length > 0) {
                    sb.append("| 使用的索引部分 | ").append(String.join(", ", usedKeyParts)).append(" |\n");
                }
                
                if (keyLength != null) {
                    sb.append("| 索引长度 | ").append(keyLength).append(" |\n");
                }
                
                if (ref != null && ref.length > 0) {
                    sb.append("| 引用 | ").append(String.join(", ", ref)).append(" |\n");
                }
                
                if (filtered != null) {
                    sb.append("| 过滤比例 | ").append(filtered).append("% |\n");
                }
                
                if (usingIndex != null) {
                    sb.append("| 使用索引 | ").append(usingIndex ? "是" : "否").append(" |\n");
                }
                
                if (rowsExaminedPerScan != null) {
                    sb.append("| 扫描行数 | ").append(rowsExaminedPerScan).append(" |\n");
                }
                
                if (rowsProducedPerJoin != null) {
                    sb.append("| 产生行数 | ").append(rowsProducedPerJoin).append(" |\n");
                }
                
                sb.append("\n");
                
                // 成本详情
                if (readCost != null || evalCost != null || prefixCost != null || dataReadPerJoin != null) {
                    sb.append("## 成本详情\n\n");
                    sb.append("### 表级别成本\n");
                    boolean hasAnyCost = false;
                    
                    if (readCost != null) {
                        sb.append("- 读取成本: ").append(readCost).append("\n");
                        hasAnyCost = true;
                    }
                    if (evalCost != null) {
                        sb.append("- 评估成本: ").append(evalCost).append("\n");
                        hasAnyCost = true;
                    }
                    if (prefixCost != null) {
                        sb.append("- 前缀成本: ").append(prefixCost).append("\n");
                        hasAnyCost = true;
                    }
                    if (dataReadPerJoin != null) {
                        sb.append("- 数据读取: ").append(dataReadPerJoin).append("\n");
                        hasAnyCost = true;
                    }
                    
                    if (!hasAnyCost) {
                        sb.append("- 无成本信息\n");
                    }
                    sb.append("\n");
                }
                
                // 使用的列
                if (usedColumns != null && usedColumns.length > 0) {
                    sb.append("## 使用的列\n\n");
                    for (String column : usedColumns) {
                        sb.append("- ").append(column).append("\n");
                    }
                    sb.append("\n");
                }
            }
        } else {
            sb.append("执行计划信息不可用\n\n");
        }

        // 原始JSON（可选展示）
        if (rawJson != null && !rawJson.isEmpty()) {
            sb.append("---\n\n");
            sb.append("## 原始JSON\n\n");
            sb.append("<details>\n");
            sb.append("<summary>查看完整执行计划（JSON）</summary>\n\n");
            sb.append("```json\n");
            sb.append(rawJson).append("\n");
            sb.append("```\n\n");
            sb.append("</details>\n");
        }
        
        return sb.toString();
    }

    /**
     * 序列化为 JSON 时，构建嵌套结构
     */
    @JsonGetter("query_block")
    public Object getQueryBlockForJson() {
        if (selectId == null && tableName == null) {
            return null;
        }
        
        java.util.Map<String, Object> queryBlock = new java.util.HashMap<>();
        
        if (selectId != null) {
            queryBlock.put("select_id", selectId);
        }
        
        if (costInfo != null) {
            queryBlock.put("cost_info", costInfo);
        }
        
        if (tableName != null) {
            java.util.Map<String, Object> table = new java.util.HashMap<>();
            table.put("table_name", tableName);
            
            if (accessType != null) {
                table.put("access_type", accessType);
            }
            if (key != null) {
                table.put("key", key);
            }
            if (possibleKeys != null) {
                table.put("possible_keys", possibleKeys);
            }
            if (usedKeyParts != null) {
                table.put("used_key_parts", usedKeyParts);
            }
            if (keyLength != null) {
                table.put("key_length", keyLength);
            }
            if (ref != null) {
                table.put("ref", ref);
            }
            if (filtered != null) {
                table.put("filtered", filtered);
            }
            if (usingIndex != null) {
                table.put("using_index", usingIndex);
            }
            if (rowsExaminedPerScan != null) {
                table.put("rows_examined_per_scan", rowsExaminedPerScan);
            }
            if (rowsProducedPerJoin != null) {
                table.put("rows_produced_per_join", rowsProducedPerJoin);
            }
            if (usedColumns != null) {
                table.put("used_columns", usedColumns);
            }
            
            // CostInfo
            if (readCost != null || evalCost != null || prefixCost != null || dataReadPerJoin != null) {
                java.util.Map<String, Object> costInfo = new java.util.HashMap<>();
                if (readCost != null) {
                    costInfo.put("read_cost", readCost);
                }
                if (evalCost != null) {
                    costInfo.put("eval_cost", evalCost);
                }
                if (prefixCost != null) {
                    costInfo.put("prefix_cost", prefixCost);
                }
                if (dataReadPerJoin != null) {
                    costInfo.put("data_read_per_join", dataReadPerJoin);
                }
                table.put("cost_info", costInfo);
            }
            
            queryBlock.put("table", table);
        }
        
        return queryBlock;
    }
}
