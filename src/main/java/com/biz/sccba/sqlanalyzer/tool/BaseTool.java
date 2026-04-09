package com.biz.sccba.sqlanalyzer.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope Tool 基类
 *
 * 提供统一的工具执行框架，包括:
 * - 工具元数据管理
 * - 参数验证
 * - 执行时间统计
 * - 异常处理
 * - 结果序列化
 *
 * 子类需要实现 {@link #executeInternal(Map)} 方法
 */
public abstract class BaseTool {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 工具元数据缓存
     */
    private static final Map<String, ToolMetadata> metadataCache = new ConcurrentHashMap<>();

    /**
     * 工具名称
     */
    private final String name;

    /**
     * 工具描述
     */
    private final String description;

    /**
     * 参数 Schema 定义
     */
    private final Map<String, ParameterSchema> parameterSchemas;

    /**
     * 构造函数
     *
     * @param name        工具名称
     * @param description 工具描述
     */
    protected BaseTool(String name, String description) {
        this.name = name;
        this.description = description;
        this.parameterSchemas = new HashMap<>();
        initParameters();
    }

    /**
     * 初始化参数定义
     * 子类可以重写此方法定义参数
     */
    protected void initParameters() {
        // 默认不定义参数，子类根据需要添加
    }

    /**
     * 添加参数定义
     *
     * @param name        参数名
     * @param type        参数类型
     * @param description 参数描述
     * @param required    是否必填
     * @return 当前工具实例（链式调用）
     */
    protected BaseTool addParameter(String name, String type, String description, boolean required) {
        parameterSchemas.put(name, new ParameterSchema(name, type, description, required));
        return this;
    }

    /**
     * 获取工具名称
     *
     * @return 工具名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取工具描述
     *
     * @return 工具描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取参数 Schema
     *
     * @return 参数 Schema 映射
     */
    public Map<String, ParameterSchema> getParameterSchemas() {
        return new HashMap<>(parameterSchemas);
    }

    /**
     * 执行工具
     *
     * 此方法提供统一的执行框架：
     * 1. 参数验证
     * 2. 执行计时
     * 3. 异常处理
     * 4. 结果封装
     *
     * @param parameters 参数 Map
     * @return 工具执行结果
     */
    public ToolResult execute(Map<String, Object> parameters) {
        System.out.println("执行工具：" + name + " 参数：" + parameters);
        long startTime = System.currentTimeMillis();

        try {
            // 参数验证
            validateParameters(parameters);

            // 执行工具逻辑
            Object result = executeInternal(parameters != null ? parameters : new HashMap<>());

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("工具执行成功：" + name + " 耗时：" + duration + "ms");

            return ToolResult.success(result, duration);

        } catch (IllegalArgumentException e) {
            System.out.println("工具 " + name + " 参数验证失败：" + e.getMessage());
            return ToolResult.failure("参数验证失败：" + e.getMessage());
        } catch (Exception e) {
            System.out.println("工具执行失败：" + name + " 错误：" + e.getMessage());
            long duration = System.currentTimeMillis() - startTime;
            return ToolResult.failure("工具执行失败：" + name, e);
        }
    }

    /**
     * 验证参数
     *
     * @param parameters 参数 Map
     * @throws IllegalArgumentException 参数验证失败时抛出
     */
    protected void validateParameters(Map<String, Object> parameters) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }

        for (Map.Entry<String, ParameterSchema> entry : parameterSchemas.entrySet()) {
            ParameterSchema schema = entry.getValue();
            String paramName = schema.name;
            Object value = parameters.get(paramName);

            // 检查必填参数
            if (schema.required && (value == null || (value instanceof String && ((String) value).isEmpty()))) {
                throw new IllegalArgumentException("缺少必填参数：" + paramName);
            }

            // 检查参数类型
            if (value != null && !isValidType(value, schema.type)) {
                throw new IllegalArgumentException("参数 " + paramName + " 类型错误，期望：" + schema.type);
            }
        }
    }

    /**
     * 执行工具内部逻辑
     * 子类必须实现此方法
     *
     * @param parameters 参数 Map
     * @return 执行结果
     * @throws Exception 执行异常
     */
    protected abstract Object executeInternal(Map<String, Object> parameters) throws Exception;

    /**
     * 检查值类型是否有效
     */
    private boolean isValidType(Object value, String expectedType) {
        return switch (expectedType.toLowerCase()) {
            case "string" -> value instanceof String;
            case "integer", "int" -> value instanceof Integer || value instanceof Long;
            case "number", "double", "float" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object", "map" -> value instanceof Map;
            case "array", "list" -> value instanceof java.util.List;
            default -> true; // 未知类型也认为有效
        };
    }

    /**
     * 获取工具元数据
     *
     * @return 工具元数据
     */
    public ToolMetadata getMetadata() {
        return metadataCache.computeIfAbsent(name, k -> new ToolMetadata(
            name,
            description,
            parameterSchemas
        ));
    }

    /**
     * 参数 Schema
     */
    public record ParameterSchema(String name, String type, String description, boolean required) {}

    /**
     * 工具元数据
     */
    public record ToolMetadata(String name, String description, Map<String, ParameterSchema> parameters) {

        /**
         * 转换为 Map 格式
         *
         * @return Map 格式元数据
         */
        public Map<String, Object> toMap() {
            Map<String, Object> result = new HashMap<>();
            result.put("name", name);
            result.put("description", description);

            Map<String, Object> paramsMap = new HashMap<>();
            for (Map.Entry<String, ParameterSchema> entry : parameters.entrySet()) {
                Map<String, Object> paramInfo = new HashMap<>();
                paramInfo.put("type", entry.getValue().type);
                paramInfo.put("description", entry.getValue().description);
                paramInfo.put("required", entry.getValue().required);
                paramsMap.put(entry.getKey(), paramInfo);
            }
            result.put("parameters", paramsMap);

            return result;
        }

        /**
         * 转换为 JSON 字符串
         *
         * @return JSON 字符串
         */
        public String toJson() {
            try {
                return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(toMap());
            } catch (JsonProcessingException e) {
                return "{\"error\": \"Failed to serialize metadata\"}";
            }
        }
    }
}
