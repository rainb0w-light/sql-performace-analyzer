package com.biz.sccba.sqlanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板管理服务
 *
 * 管理 SQL 分析器中使用的各类 Prompt 模板：
 * - 模板 1: test 表达式转自然语言（结构化 Prompt）
 * - 模板 2: 业务场景推测（Few-shot 示例）
 * - 模板 3: 参数值生成（结合业务语义）
 *
 * 功能特性：
 * - 模板版本控制（支持 A/B 测试）
 * - 动态变量注入
 * - 输出验证（JSON Schema 校验）
 * - 缓存策略（30 分钟过期）
 */
@Service
public class PromptTemplateService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 模板存储：templateId -> 版本 -> 模板内容
    private final Map<String, Map<String, Template>> templateStore = new ConcurrentHashMap<>();

    // 缓存：cacheKey -> 缓存结果
    private final Map<String, CachedResult> resultCache = new ConcurrentHashMap<>();

    // 默认缓存过期时间（30 分钟）
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(30);

    /**
     * 模板模型
     */
    public record Template(
        String id,
        String version,
        String type,  // STRUCTURED, FEW_SHOT, HYBRID
        String name,
        String description,
        String content,
        String outputSchema,  // JSON Schema 用于验证输出
        List<String> variables,  // 动态变量列表
        Instant createdAt,
        Instant updatedAt,
        boolean active  // 是否启用（用于 A/B 测试）
    ) {}

    /**
     * 缓存结果
     */
    public record CachedResult(
        Object result,
        Instant expiresAt
    ) {}

    /**
     * 初始化默认模板
     */
    public PromptTemplateService() {
        initializeDefaultTemplates();
    }

    /**
     * 初始化系统默认模板
     */
    private void initializeDefaultTemplates() {
        // 模板 1: test 表达式转自然语言（结构化 Prompt）
        registerTemplate(new Template(
            "test-to-natural-language",
            "v1.0",
            "STRUCTURED",
            "Test 表达式转自然语言",
            "将 MyBatis test 条件表达式转换为自然语言描述",
            """
            你是一个 SQL 分析专家。请将以下 MyBatis test 条件表达式转换为自然语言描述。

            ## 输入信息
            - 表名：{{tableName}}
            - 字段名：{{fieldName}}
            - test 表达式：{{testExpression}}
            - 字段类型：{{fieldType}}

            ## 输出格式
            请用简洁的中文描述该条件的业务含义，格式为：
            "当 [字段] [条件] 时，表示 [业务场景]"

            ## 示例
            输入：test="type != null and type != ''"
            输出：当类型字段不为空时，表示用户指定了具体类型

            ## 实际转换
            输入：test="{{testExpression}}"
            输出：
            """,
            null,
            List.of("tableName", "fieldName", "testExpression", "fieldType"),
            Instant.now(),
            Instant.now(),
            true
        ));

        // 模板 2: 业务场景推测（Few-shot 示例）
        registerTemplate(new Template(
            "business-scene-inference",
            "v1.0",
            "FEW_SHOT",
            "业务场景推测",
            "根据多个 test 条件组合推测 SQL 的业务场景",
            """
            你是一个金融系统业务分析专家。请根据以下 MyBatis 动态 SQL 的条件组合，推测该 SQL 可能用于哪些业务场景。

            ## 表结构信息
            表名：{{tableName}}
            业务域：{{businessDomain}}

            ## 条件列表
            {{conditions}}

            ## 参考示例

            ### 示例 1
            条件：
            - customerId != null
            - startDate != null and endDate != null
            - amount > 0
            业务场景：查询指定客户在某个时间段内的正金额交易记录

            ### 示例 2
            条件：
            - status in (1, 2)
            - createTime >= lastWeek
            - orderBy = "amount DESC"
            业务场景：查询上周创建的有效状态订单，按金额降序排列（常见于风控监控）

            ### 示例 3
            条件：
            - accountType = "VIP"
            - balance > 10000
            - lastActiveDate within 30 days
            业务场景：筛选高净值活跃 VIP 客户（用于精准营销）

            ## 实际推测
            条件：
            {{conditions}}

            业务场景推测：
            """,
            """
            {
              "type": "object",
              "properties": {
                "scenes": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": {"type": "string"},
                      "description": {"type": "string"},
                      "confidence": {"type": "number", "minimum": 0, "maximum": 1}
                    },
                    "required": ["name", "description", "confidence"]
                  }
                },
                "recommendation": {"type": "string"}
              },
              "required": ["scenes", "recommendation"]
            }
            """,
            List.of("tableName", "businessDomain", "conditions"),
            Instant.now(),
            Instant.now(),
            true
        ));

        // 模板 3: 参数值生成（结合业务语义）
        registerTemplate(new Template(
            "parameter-value-generation",
            "v1.0",
            "HYBRID",
            "参数值生成",
            "根据业务语义和数据分布生成 SQL 参数测试值",
            """
            你是一个测试数据生成专家。请根据以下业务语义信息，为 SQL 查询生成合理的测试参数值。

            ## 表语义信息
            表名：{{tableName}}
            业务域：{{businessDomain}}
            表描述：{{tableDescription}}

            ## 字段语义和统计信息
            {{fieldStats}}

            ## 业务规则
            {{businessRules}}

            ## 数据分布特征
            {{distributionStats}}

            ## 参数值生成要求
            1. 遵循业务规范（如状态字段只能使用有效状态值）
            2. 符合数据分布（如金额字段使用常见金额范围）
            3. 考虑业务规则（如日期不能早于业务开始日期）
            4. 生成多组值以覆盖不同场景

            ## 输出格式
            请以 JSON 格式输出：
            {
              "parameters": [
                {
                  "name": "param1",
                  "fieldName": "customer_id",
                  "value": "C001",
                  "description": "普通客户 ID",
                  "scene": "正常场景"
                }
              ],
              "warnings": ["注意：xxx 字段使用了默认值"]
            }

            ## 实际生成
            表名：{{tableName}}
            字段：{{targetFields}}

            生成的参数值：
            """,
            """
            {
              "type": "object",
              "properties": {
                "parameters": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": {"type": "string"},
                      "fieldName": {"type": "string"},
                      "value": {},
                      "description": {"type": "string"},
                      "scene": {"type": "string"}
                    },
                    "required": ["name", "fieldName", "value", "description"]
                  }
                },
                "warnings": {
                  "type": "array",
                  "items": {"type": "string"}
                }
              },
              "required": ["parameters", "warnings"]
            }
            """,
            List.of("tableName", "businessDomain", "tableDescription", "fieldStats", "businessRules", "distributionStats", "targetFields"),
            Instant.now(),
            Instant.now(),
            true
        ));
    }

    /**
     * 注册模板
     */
    public void registerTemplate(Template template) {
        templateStore.computeIfAbsent(template.id, k -> new ConcurrentHashMap<>())
            .put(template.version, template);
    }

    /**
     * 获取模板
     *
     * @param templateId 模板 ID
     * @param version 版本号（可选，默认返回最新启用的版本）
     * @return 模板，不存在返回 null
     */
    public Template getTemplate(String templateId, String version) {
        Map<String, Template> versions = templateStore.get(templateId);
        if (versions == null) {
            return null;
        }

        if (version != null) {
            return versions.get(version);
        }

        // 返回最新启用的版本
        return versions.values().stream()
            .filter(Template::active)
            .max(Comparator.comparing(Template::updatedAt))
            .orElse(null);
    }

    /**
     * 获取所有模板 ID
     */
    public Set<String> getAllTemplateIds() {
        return templateStore.keySet();
    }

    /**
     * 获取模板的所有版本
     */
    public List<Template> getAllVersions(String templateId) {
        Map<String, Template> versions = templateStore.get(templateId);
        return versions != null ? new ArrayList<>(versions.values()) : Collections.emptyList();
    }

    /**
     * 填充模板变量
     *
     * @param templateId 模板 ID
     * @param variables 变量映射
     * @param version 版本号（可选）
     * @return 填充后的 Prompt
     */
    public String fillTemplate(String templateId, Map<String, Object> variables, String version) {
        Template template = getTemplate(templateId, version);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在：" + templateId);
        }

        String content = template.content;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            content = content.replace(placeholder, value);
        }

        return content;
    }

    /**
     * 从缓存获取结果
     *
     * @param cacheKey 缓存键
     * @return 缓存结果，过期或不存在返回 null
     */
    public Object getCachedResult(String cacheKey) {
        CachedResult cached = resultCache.get(cacheKey);
        if (cached == null) {
            return null;
        }

        if (Instant.now().isAfter(cached.expiresAt)) {
            resultCache.remove(cacheKey);
            return null;
        }

        return cached.result;
    }

    /**
     * 缓存结果
     *
     * @param cacheKey 缓存键
     * @param result 结果
     * @param ttl 过期时间（可选，默认 30 分钟）
     */
    public void cacheResult(String cacheKey, Object result, Duration ttl) {
        Duration actualTtl = ttl != null ? ttl : DEFAULT_CACHE_TTL;
        resultCache.put(cacheKey, new CachedResult(result, Instant.now().plus(actualTtl)));
    }

    /**
     * 缓存结果（使用默认 TTL）
     */
    public void cacheResult(String cacheKey, Object result) {
        cacheResult(cacheKey, result, null);
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        resultCache.clear();
    }

    /**
     * 清理过期缓存
     */
    public void cleanupExpiredCache() {
        Instant now = Instant.now();
        resultCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    /**
     * 验证输出是否符合 JSON Schema
     *
     * @param templateId 模板 ID
     * @param output 输出内容（JSON 字符串）
     * @return 是否有效
     */
    public boolean validateOutput(String templateId, String output) {
        Template template = getTemplate(templateId, null);
        if (template == null || template.outputSchema == null) {
            return true;  // 没有 Schema 时默认通过
        }

        try {
            JsonNode outputJson = objectMapper.readTree(output);
            JsonNode schema = objectMapper.readTree(template.outputSchema);
            return validateJsonSchema(outputJson, schema);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 简单的 JSON Schema 验证（简化实现）
     * 生产环境建议使用专门的 JSON Schema 验证库
     */
    private boolean validateJsonSchema(JsonNode data, JsonNode schema) {
        // 检查类型
        if (schema.has("type")) {
            String expectedType = schema.get("type").asText();
            if (!matchesType(data, expectedType)) {
                return false;
            }
        }

        // 检查必填字段
        if (schema.has("required")) {
            for (JsonNode requiredField : schema.get("required")) {
                if (!data.has(requiredField.asText())) {
                    return false;
                }
            }
        }

        // 检查属性
        if (schema.has("properties") && data.isObject()) {
            ObjectNode properties = (ObjectNode) schema.get("properties");
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode fieldSchema = field.getValue();

                if (data.has(fieldName)) {
                    if (!validateJsonSchema(data.get(fieldName), fieldSchema)) {
                        return false;
                    }
                }
            }
        }

        // 检查数组项
        if (schema.has("items") && data.isArray()) {
            JsonNode itemsSchema = schema.get("items");
            for (JsonNode item : data) {
                if (!validateJsonSchema(item, itemsSchema)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 检查数据类型是否匹配
     */
    private boolean matchesType(JsonNode data, String expectedType) {
        return switch (expectedType) {
            case "string" -> data.isTextual();
            case "number" -> data.isNumber();
            case "integer" -> data.isInt() || data.isLong();
            case "boolean" -> data.isBoolean();
            case "array" -> data.isArray();
            case "object" -> data.isObject();
            case "null" -> data.isNull();
            default -> true;
        };
    }

    /**
     * 设置模板激活状态（用于 A/B 测试）
     */
    public void setTemplateActive(String templateId, String version, boolean active) {
        Template template = getTemplate(templateId, version);
        if (template != null) {
            Template updated = new Template(
                template.id(),
                template.version(),
                template.type(),
                template.name(),
                template.description(),
                template.content(),
                template.outputSchema(),
                template.variables(),
                template.createdAt(),
                Instant.now(),
                active
            );
            registerTemplate(updated);
        }
    }

    /**
     * 获取模板统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("templateCount", templateStore.size());
        stats.put("cacheSize", resultCache.size());

        Map<String, Integer> versionCount = new HashMap<>();
        templateStore.forEach((id, versions) -> versionCount.put(id, versions.size()));
        stats.put("versionsPerTemplate", versionCount);

        return stats;
    }
}
