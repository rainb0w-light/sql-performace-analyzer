# JSON 解析最佳实践

## 当前实现的优化方案

### 1. 配置宽松的 ObjectMapper

在 `SqlAgentService.java` 中，我们现在使用了宽松配置的 `ObjectMapper`：

```java
@Service
public class SqlAgentService {
    
    private final ObjectMapper objectMapper;
    
    public SqlAgentService() {
        this.objectMapper = new ObjectMapper();
        configureObjectMapper(this.objectMapper);
    }
    
    @SuppressWarnings("deprecation")
    private void configureObjectMapper(ObjectMapper mapper) {
        // 允许未转义的控制字符
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        
        // 允许反斜杠转义任何字符
        mapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        
        // 允许 JSON 中的注释
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        
        // 忽略未知属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
```

### 2. 多层级解析策略

我们的 `parseJsonResponse` 方法采用了三层策略：

```java
private <T> T parseJsonResponse(String response, Class<T> clazz) throws JsonProcessingException {
    String jsonContent = response.trim();

    // ========== 第一层：移除 Markdown 标记 ==========
    if (jsonContent.startsWith("```json")) {
        jsonContent = jsonContent.substring(7);
    } else if (jsonContent.startsWith("```")) {
        jsonContent = jsonContent.substring(3);
    }
    if (jsonContent.endsWith("```")) {
        jsonContent = jsonContent.substring(0, jsonContent.length() - 3);
    }
    jsonContent = jsonContent.trim();

    // ========== 第二层：基础清理 ==========
    // 1. 移除多余的换行符和制表符
    jsonContent = jsonContent.replaceAll("\\s*\\n\\s*", "");
    
    // 2. 修复双重转义
    jsonContent = jsonContent.replace("\\\\\"", "\\\"");

    // ========== 第三层：首次解析尝试 ==========
    try {
        return objectMapper.readValue(jsonContent, clazz);
    } catch (JsonProcessingException e) {
        logger.warn("初次解析JSON失败，尝试进行更激进的清理: {}", e.getMessage());
        
        // ========== 第四层：激进清理 ==========
        int firstBrace = jsonContent.indexOf('{');
        int lastBrace = jsonContent.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            String potentialJson = jsonContent.substring(firstBrace, lastBrace + 1);
            try {
                return objectMapper.readValue(potentialJson, clazz);
            } catch (JsonProcessingException e2) {
                logger.error("激进清理后仍无法解析JSON: {}", potentialJson, e2);
                throw e;
            }
        }
        throw e;
    }
}
```

---

## 可选：添加 Apache Commons Text 支持

如果你想使用 Apache Commons Text 的标准转义处理，可以这样做：

### 步骤 1：添加依赖

```gradle
dependencies {
    implementation 'org.apache.commons:commons-text:1.11.0'
}
```

### 步骤 2：创建增强版解析方法

```java
import org.apache.commons.text.StringEscapeUtils;

/**
 * 增强版 JSON 解析 - 结合 Apache Commons Text
 */
private <T> T parseJsonResponseWithApache(String response, Class<T> clazz) throws JsonProcessingException {
    String jsonContent = response.trim();

    // 策略 1: 标准清理
    try {
        String cleaned = standardClean(jsonContent);
        return objectMapper.readValue(cleaned, clazz);
    } catch (JsonProcessingException e1) {
        logger.debug("策略 1 失败: {}", e1.getMessage());
        
        // 策略 2: Apache 反转义
        try {
            String cleaned = standardClean(jsonContent);
            String unescaped = StringEscapeUtils.unescapeJson(cleaned);
            return objectMapper.readValue(unescaped, clazz);
        } catch (Exception e2) {
            logger.debug("策略 2 失败: {}", e2.getMessage());
            
            // 策略 3: 激进提取
            String extracted = extractJsonFromText(jsonContent);
            return objectMapper.readValue(extracted, clazz);
        }
    }
}

/**
 * 标准清理流程
 */
private String standardClean(String json) {
    String cleaned = json;
    
    // 移除 Markdown 标记
    if (cleaned.startsWith("```json")) {
        cleaned = cleaned.substring(7);
    } else if (cleaned.startsWith("```")) {
        cleaned = cleaned.substring(3);
    }
    if (cleaned.endsWith("```")) {
        cleaned = cleaned.substring(0, cleaned.length() - 3);
    }
    
    // 移除多余空白
    cleaned = cleaned.trim();
    cleaned = cleaned.replaceAll("\\s*\\n\\s*", "");
    
    return cleaned;
}

/**
 * 从文本中提取 JSON
 */
private String extractJsonFromText(String text) {
    int firstBrace = text.indexOf('{');
    int lastBrace = text.lastIndexOf('}');
    
    if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
        return text.substring(firstBrace, lastBrace + 1);
    }
    
    throw new IllegalArgumentException("未找到有效的 JSON 结构");
}
```

---

## 不同场景的推荐方案

### 场景 1：LLM 返回标准 JSON

**推荐：** Jackson 宽松配置

```java
// 已配置的 objectMapper 就足够了
return objectMapper.readValue(llmResponse, clazz);
```

### 场景 2：LLM 返回 Markdown 包裹的 JSON

**推荐：** 当前的 parseJsonResponse

```java
// 自动移除 ```json 标记
return parseJsonResponse(llmResponse, clazz);
```

### 场景 3：LLM 返回带说明文字的 JSON

**推荐：** 激进提取策略

```java
// 已在 parseJsonResponse 的第二次尝试中实现
// 会自动提取第一个 { 到最后一个 } 之间的内容
return parseJsonResponse(llmResponse, clazz);
```

### 场景 4：JSON 被多重转义

**推荐：** Apache Commons Text

```java
String unescaped = StringEscapeUtils.unescapeJson(jsonContent);
return objectMapper.readValue(unescaped, clazz);
```

### 场景 5：用户输入需要转义

**推荐：** Apache Commons Text

```java
String userInput = "路径是 C:\\Users\\test";
String escaped = StringEscapeUtils.escapeJson(userInput);
// 现在可以安全地插入到 JSON 中
String json = "{\"path\": \"" + escaped + "\"}";
```

---

## 常见问题和解决方案

### 问题 1：反斜杠导致解析失败

**症状：**
```
JsonProcessingException: Unexpected character ('\\' (code 92))
```

**原因：**
- LLM 返回了未正确转义的反斜杠
- 例如：`"path": "C:\Users"` 而不是 `"path": "C:\\Users"`

**解决方案：**
```java
// 方案 A: 使用 Jackson 宽松配置（已实现）
mapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);

// 方案 B: 手动修复
jsonContent = jsonContent.replace("\\", "\\\\");
// 但要注意已经正确转义的情况

// 方案 C: 使用 Apache Commons Text
String unescaped = StringEscapeUtils.unescapeJson(jsonContent);
```

---

### 问题 2：控制字符导致解析失败

**症状：**
```
JsonProcessingException: Illegal unquoted character ((CTRL-CHAR, code 10))
```

**原因：**
- JSON 中包含未转义的换行符 `\n`、制表符 `\t` 等

**解决方案：**
```java
// 方案 A: 使用 Jackson 宽松配置（已实现）
mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

// 方案 B: 手动移除
jsonContent = jsonContent.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
```

---

### 问题 3：LLM 返回了说明文字

**症状：**
```
这是分析结果：
{
  "riskLevel": "HIGH"
}
希望对你有帮助！
```

**解决方案：**
```java
// 已在 parseJsonResponse 的激进提取策略中实现
int firstBrace = jsonContent.indexOf('{');
int lastBrace = jsonContent.lastIndexOf('}');
String potentialJson = jsonContent.substring(firstBrace, lastBrace + 1);
```

---

### 问题 4：JSON 被 Markdown 包裹

**症状：**
````
```json
{
  "riskLevel": "HIGH"
}
```
````

**解决方案：**
```java
// 已在 parseJsonResponse 中实现
if (jsonContent.startsWith("```json")) {
    jsonContent = jsonContent.substring(7);
}
if (jsonContent.endsWith("```")) {
    jsonContent = jsonContent.substring(0, jsonContent.length() - 3);
}
```

---

## 性能对比

| 方案 | 解析速度 | 容错能力 | 内存占用 | 推荐场景 |
|------|----------|----------|----------|----------|
| 纯 Jackson | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ | 标准 JSON |
| Jackson 宽松配置 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 略有瑕疵的 JSON |
| 自定义清理 + Jackson | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | LLM 响应 |
| Apache Commons Text | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | 标准转义处理 |
| Gson 宽松模式 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 备选方案 |

---

## 最终推荐

### 对于我们的 SQL Agent 项目

**✅ 当前实现已经很好：**

1. ✅ Jackson 宽松配置 - 提高基础容错能力
2. ✅ 多层级清理策略 - 处理各种边界情况
3. ✅ 激进提取 fallback - 最后的兜底方案

**🔧 可选增强（如果经常遇到转义问题）：**

```gradle
// 添加到 build.gradle
implementation 'org.apache.commons:commons-text:1.11.0'
```

```java
// 在 parseJsonResponse 中增加一个策略
try {
    String unescaped = StringEscapeUtils.unescapeJson(jsonContent);
    return objectMapper.readValue(unescaped, clazz);
} catch (Exception e) {
    // 继续下一个策略
}
```

**❌ 不需要：**
- 引入 Gson（已有 Jackson）
- 完全重写（当前方案已足够好）
- 过度复杂的正则表达式

---

## 总结

### 你问的问题：是否有通用方法？

**答案：是的，Apache 提供了：**

```java
// 转义（用于构建 JSON）
String escaped = StringEscapeUtils.escapeJson(raw);

// 反转义（用于处理已转义的 JSON）
String unescaped = StringEscapeUtils.unescapeJson(escaped);
```

**但对于 LLM 场景：**
- 这些方法只是工具之一，不是全部解决方案
- 我们的**组合方案**（Jackson 宽松配置 + 自定义清理）**已经足够强大**
- 如果需要，可以**可选地**添加 Apache Commons Text 作为增强

### 下一步行动

**无需修改（当前已经很好）：**
- ✅ Jackson 宽松配置已添加
- ✅ 多层级解析策略已实现
- ✅ 容错能力已增强

**可选升级（如果想用 Apache 方法）：**
1. 添加 `commons-text` 依赖
2. 在一个解析策略中加入 `StringEscapeUtils.unescapeJson`
3. 测试看是否进一步提高成功率

**推荐做法：**
- 先观察当前方案的效果
- 如果仍有解析失败，再考虑添加 Apache Commons Text
- 记录具体失败案例，针对性优化

