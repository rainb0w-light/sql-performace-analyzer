package com.biz.sccba.sqlanalyzer.memory;

import com.biz.sccba.sqlanalyzer.model.agent.BusinessSemantics;
import com.biz.sccba.sqlanalyzer.model.agent.BusinessSemantics.UpdateSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务语义记忆服务
 * 管理表/列的业务语义存储和检索
 *
 * 注意：这是一个简化的内存实现
 * 生产环境可以替换为 AgentScope 的 LongTermMemory 或 Mem0
 */
@Service
public class BusinessSemanticsMemoryService {

    private final ObjectMapper objectMapper;

    // 内存存储 (生产环境可替换为 AgentScope LongTermMemory)
    private final Map<String, BusinessSemantics> semanticsStore = new ConcurrentHashMap<>();

    public BusinessSemanticsMemoryService() {
        this.objectMapper = new ObjectMapper();
        // 注册 Java 8 时间模块支持 LocalDateTime
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 存储业务语义
     *
     * @param tableName 表名
     * @param semantics 业务语义
     */
    public void storeSemantics(String tableName, BusinessSemantics semantics) {
        System.out.println("[BusinessSemanticsMemoryService] 存储业务语义：" + tableName);
        semantics.setLastUpdatedAt(LocalDateTime.now());
        if (semantics.getUpdatedBy() == null) {
            semantics.setUpdatedBy(UpdateSource.MANUAL);
        }
        semanticsStore.put(tableName.toLowerCase(), semantics);
    }

    /**
     * 获取业务语义
     *
     * @param tableName 表名
     * @return 业务语义，如果不存在返回 null
     */
    public BusinessSemantics getSemantics(String tableName) {
        return semanticsStore.get(tableName.toLowerCase());
    }

    /**
     * 检查是否存在业务语义
     *
     * @param tableName 表名
     * @return 是否存在
     */
    public boolean hasSemantics(String tableName) {
        return semanticsStore.containsKey(tableName.toLowerCase());
    }

    /**
     * 删除业务语义
     *
     * @param tableName 表名
     */
    public void removeSemantics(String tableName) {
        System.out.println("[BusinessSemanticsMemoryService] 删除业务语义：" + tableName);
        semanticsStore.remove(tableName.toLowerCase());
    }

    /**
     * 获取所有表名
     *
     * @return 表名列表
     */
    public List<String> getAllTableNames() {
        return new ArrayList<>(semanticsStore.keySet());
    }

    /**
     * 获取所有业务语义
     *
     * @return 表名 -> 业务语义映射
     */
    public Map<String, BusinessSemantics> getAllSemantics() {
        return new HashMap<>(semanticsStore);
    }

    /**
     * 清空所有业务语义
     */
    public void clearAll() {
        System.out.println("[BusinessSemanticsMemoryService] 清空所有业务语义");
        semanticsStore.clear();
    }

    /**
     * 导出业务语义为 JSON
     *
     * @return JSON 字符串
     */
    public String exportToJson() {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(semanticsStore);
        } catch (Exception e) {
            System.out.println("[BusinessSemanticsMemoryService] 导出业务语义失败：" + e.getMessage());
            return "{}";
        }
    }

    /**
     * 从 JSON 导入业务语义
     *
     * @param json JSON 字符串
     */
    @SuppressWarnings("unchecked")
    public void importFromJson(String json) {
        try {
            Map<String, BusinessSemantics> imported = objectMapper.readValue(
                json,
                objectMapper.getTypeFactory().constructMapType(
                    Map.class, String.class, BusinessSemantics.class
                )
            );
            // 确保键名统一小写
            for (Map.Entry<String, BusinessSemantics> entry : imported.entrySet()) {
                semanticsStore.put(entry.getKey().toLowerCase(), entry.getValue());
            }
            System.out.println("[BusinessSemanticsMemoryService] 导入 " + imported.size() + " 条业务语义");
        } catch (Exception e) {
            System.out.println("[BusinessSemanticsMemoryService] 导入业务语义失败：" + e.getMessage());
        }
    }

    /**
     * 批量存储业务语义
     *
     * @param semanticsMap 表名 -> 业务语义映射
     */
    public void storeAll(Map<String, BusinessSemantics> semanticsMap) {
        System.out.println("[BusinessSemanticsMemoryService] 批量存储 " + semanticsMap.size() + " 条业务语义");
        semanticsMap.forEach((tableName, semantics) -> {
            semantics.setLastUpdatedAt(LocalDateTime.now());
            if (semantics.getUpdatedBy() == null) {
                semantics.setUpdatedBy(UpdateSource.MANUAL);
            }
            semanticsStore.put(tableName.toLowerCase(), semantics);
        });
    }
}
