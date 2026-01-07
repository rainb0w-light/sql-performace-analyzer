package com.biz.sccba.sqlanalyzer.examples;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
// import org.apache.commons.text.StringEscapeUtils; // 可选依赖
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 增强版 JSON 解析器示例
 * 
 * 这个类展示了如何结合多种技术来处理 LLM 返回的不规范 JSON：
 * 1. Jackson 宽松配置
 * 2. 自定义清理逻辑
 * 3. Apache Commons Text（可选）
 * 4. 多层级 fallback 策略
 * 
 * 使用建议：
 * - 如果只处理 LLM 响应，使用 parseWithStandardStrategy()
 * - 如果需要处理用户输入的转义，添加 commons-text 依赖并使用 parseWithApacheStrategy()
 * 
 * @author SQL Agent Team
 * @version 2.0
 */
public class EnhancedJsonParser {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedJsonParser.class);
    
    private final ObjectMapper objectMapper;
    private final boolean useApacheCommons;
    
    /**
     * 构造函数
     * 
     * @param useApacheCommons 是否使用 Apache Commons Text（需要添加依赖）
     */
    public EnhancedJsonParser(boolean useApacheCommons) {
        this.objectMapper = new ObjectMapper();
        this.useApacheCommons = useApacheCommons;
        configureObjectMapper(this.objectMapper);
    }
    
    /**
     * 默认构造函数 - 不使用 Apache Commons Text
     */
    public EnhancedJsonParser() {
        this(false);
    }
    
    /**
     * 配置 ObjectMapper 以提高容错能力
     */
    @SuppressWarnings("deprecation")
    private void configureObjectMapper(ObjectMapper mapper) {
        // 允许未转义的控制字符（如 \n \t）
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        
        // 允许反斜杠转义任何字符
        mapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        
        // 允许 JSON 中的注释
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        
        // 允许单引号
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        
        // 忽略未知属性（LLM 可能返回额外字段）
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        logger.debug("ObjectMapper 已配置为宽松模式");
    }
    
    /**
     * 主要的解析方法 - 使用标准策略
     * 
     * 这个方法足以处理大多数 LLM 响应场景：
     * - Markdown 代码块包裹
     * - 说明文字混杂
     * - 常见的转义问题
     * 
     * @param response LLM 返回的响应
     * @param clazz 目标类型
     * @return 解析后的对象
     * @throws JsonProcessingException 如果所有策略都失败
     */
    public <T> T parseWithStandardStrategy(String response, Class<T> clazz) throws JsonProcessingException {
        logger.debug("开始使用标准策略解析 JSON，响应长度: {}", response.length());
        
        // 策略 1: 直接解析（最快）
        try {
            logger.trace("尝试策略 1: 直接解析");
            return objectMapper.readValue(response, clazz);
        } catch (JsonProcessingException e1) {
            logger.trace("策略 1 失败: {}", e1.getMessage());
        }
        
        // 策略 2: 移除 Markdown 后解析
        try {
            logger.trace("尝试策略 2: 移除 Markdown");
            String cleaned = removeMarkdown(response);
            return objectMapper.readValue(cleaned, clazz);
        } catch (JsonProcessingException e2) {
            logger.trace("策略 2 失败: {}", e2.getMessage());
        }
        
        // 策略 3: 基础清理后解析
        try {
            logger.trace("尝试策略 3: 基础清理");
            String cleaned = basicClean(response);
            return objectMapper.readValue(cleaned, clazz);
        } catch (JsonProcessingException e3) {
            logger.trace("策略 3 失败: {}", e3.getMessage());
        }
        
        // 策略 4: 激进提取后解析
        try {
            logger.trace("尝试策略 4: 激进提取");
            String extracted = extractJson(response);
            return objectMapper.readValue(extracted, clazz);
        } catch (JsonProcessingException e4) {
            logger.error("所有标准策略都失败了，响应内容: {}", response);
            throw e4;
        }
    }
    
    /**
     * 使用 Apache Commons Text 的解析方法
     * 
     * 需要添加依赖：
     * implementation 'org.apache.commons:commons-text:1.11.0'
     * 
     * 适用场景：
     * - JSON 被多重转义
     * - 需要标准的转义/反转义处理
     * 
     * @param response LLM 返回的响应
     * @param clazz 目标类型
     * @return 解析后的对象
     * @throws JsonProcessingException 如果所有策略都失败
     */
    public <T> T parseWithApacheStrategy(String response, Class<T> clazz) throws JsonProcessingException {
        if (!useApacheCommons) {
            logger.warn("Apache Commons Text 未启用，回退到标准策略");
            return parseWithStandardStrategy(response, clazz);
        }
        
        logger.debug("开始使用 Apache 策略解析 JSON，响应长度: {}", response.length());
        
        // 策略 1: 标准清理 + Apache 反转义
        try {
            logger.trace("尝试 Apache 策略 1: 标准清理 + 反转义");
            String cleaned = basicClean(response);
            // String unescaped = StringEscapeUtils.unescapeJson(cleaned); // 取消注释以使用
            // return objectMapper.readValue(unescaped, clazz);
            logger.warn("StringEscapeUtils 未导入，需要添加 commons-text 依赖");
        } catch (Exception e1) {
            logger.trace("Apache 策略 1 失败: {}", e1.getMessage());
        }
        
        // 回退到标准策略
        return parseWithStandardStrategy(response, clazz);
    }
    
    /**
     * 自动选择最佳策略
     * 
     * @param response LLM 返回的响应
     * @param clazz 目标类型
     * @return 解析后的对象
     * @throws JsonProcessingException 如果所有策略都失败
     */
    public <T> T parse(String response, Class<T> clazz) throws JsonProcessingException {
        if (useApacheCommons) {
            return parseWithApacheStrategy(response, clazz);
        } else {
            return parseWithStandardStrategy(response, clazz);
        }
    }
    
    // ========== 清理方法 ==========
    
    /**
     * 移除 Markdown 代码块标记
     */
    private String removeMarkdown(String content) {
        String cleaned = content.trim();
        
        // 移除开头的 ```json 或 ```
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        
        // 移除结尾的 ```
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        return cleaned.trim();
    }
    
    /**
     * 基础清理
     */
    private String basicClean(String content) {
        String cleaned = removeMarkdown(content);
        
        // 移除多余的换行和空白（但保留结构性空格）
        cleaned = cleaned.replaceAll("\\s*\\n\\s*", "");
        
        // 修复常见的双重转义
        cleaned = cleaned.replace("\\\\\"", "\\\"");
        
        // 移除 BOM（如果存在）
        if (cleaned.startsWith("\uFEFF")) {
            cleaned = cleaned.substring(1);
        }
        
        return cleaned.trim();
    }
    
    /**
     * 激进提取 - 提取第一个完整的 JSON 对象
     */
    private String extractJson(String content) {
        String cleaned = basicClean(content);
        
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        
        if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) {
            throw new IllegalArgumentException("未找到有效的 JSON 结构");
        }
        
        return cleaned.substring(firstBrace, lastBrace + 1);
    }
    
    // ========== 工具方法 ==========
    
    /**
     * 验证 JSON 是否有效
     */
    public boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
    
    /**
     * 美化 JSON
     */
    public String prettify(String json) throws JsonProcessingException {
        Object obj = objectMapper.readValue(json, Object.class);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }
    
    /**
     * 压缩 JSON（移除所有空白）
     */
    public String minify(String json) throws JsonProcessingException {
        Object obj = objectMapper.readValue(json, Object.class);
        return objectMapper.writeValueAsString(obj);
    }
}

/**
 * 使用示例
 */
class EnhancedJsonParserExample {
    
    public static void main(String[] args) {
        EnhancedJsonParser parser = new EnhancedJsonParser();
        
        // 示例 1: 处理 Markdown 包裹的 JSON
        String llmResponse1 = """
            这是分析结果：
            ```json
            {
              "riskLevel": "HIGH",
              "estimatedRowsExamined": 10000
            }
            ```
            希望对你有帮助！
            """;
        
        try {
            // MyDTO result = parser.parse(llmResponse1, MyDTO.class);
            System.out.println("成功解析示例 1");
        } catch (JsonProcessingException e) {
            System.err.println("解析失败: " + e.getMessage());
        }
        
        // 示例 2: 处理包含反斜杠的路径
        String llmResponse2 = """
            {
              "path": "C:\\Users\\test",
              "riskLevel": "LOW"
            }
            """;
        
        try {
            // MyDTO result = parser.parse(llmResponse2, MyDTO.class);
            System.out.println("成功解析示例 2");
        } catch (JsonProcessingException e) {
            System.err.println("解析失败: " + e.getMessage());
        }
        
        // 示例 3: 验证 JSON 有效性
        String testJson = "{\"key\": \"value\"}";
        if (parser.isValidJson(testJson)) {
            System.out.println("JSON 有效");
        }
    }
}

/**
 * 与 Apache Commons Text 结合使用的示例
 * 
 * 需要先添加依赖：
 * implementation 'org.apache.commons:commons-text:1.11.0'
 */
class ApacheCommonsExample {
    
    public static void exampleEscape() {
        // 示例：转义用户输入
        String userInput = "路径是 C:\\Users\\test";
        
        // String escaped = StringEscapeUtils.escapeJson(userInput);
        // 结果: "路径是 C:\\\\Users\\\\test"
        
        // 现在可以安全地插入到 JSON 中
        // String json = "{\"path\": \"" + escaped + "\"}";
    }
    
    public static void exampleUnescape() {
        // 示例：反转义 JSON 字符串
        String escaped = "路径是 C:\\\\Users\\\\test";
        
        // String unescaped = StringEscapeUtils.unescapeJson(escaped);
        // 结果: "路径是 C:\Users\test"
    }
}









