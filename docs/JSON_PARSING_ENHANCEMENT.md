# JSON 解析增强文档

## 问题背景

在使用 LLM 进行 SQL 风险评估时，LLM 返回的 JSON 字符串可能包含各种格式问题，导致 `ObjectMapper.readValue()` 解析失败：

### 常见问题
1. **反斜杠转义问题**：`\\` 被错误地双重转义为 `\\\\`
2. **Markdown 代码块**：JSON 被包裹在 ````json` ... ``` 中
3. **额外的文本**：JSON 前后有说明性文字
4. **控制字符**：包含不可见的控制字符
5. **错误的引号转义**：`\\\"` 应该是 `\"`
6. **括号不匹配**：JSON 结构不完整

### 错误示例
```
原始 LLM 响应：
这是分析结果：
```json
{
  "riskLevel": "HIGH",
  "reasoning": "这是一个\\测试"
}
```
希望对你有帮助！
```

## 解决方案

### 多层次解析策略

实现了三层解析策略，逐步提高容错能力：

#### 策略 1: 直接解析
```java
return objectMapper.readValue(jsonContent, clazz);
```
- 适用于格式完全正确的 JSON
- 最快速的解析方式

#### 策略 2: 清理后解析
```java
String cleanedContent = cleanJsonContent(jsonContent);
return objectMapper.readValue(cleanedContent, clazz);
```
- 清理常见的格式问题
- 修复转义字符
- 移除控制字符

#### 策略 3: 提取 JSON
```java
String extractedJson = extractJsonFromText(jsonContent);
return objectMapper.readValue(extractedJson, clazz);
```
- 从混杂文本中提取纯 JSON
- 使用括号匹配算法
- 找到最外层的完整 JSON 对象

### 关键方法

#### 1. `parseJsonResponse()` - 主解析方法

**功能增强：**
- ✅ 移除 Markdown 代码块标记
- ✅ 三层解析策略
- ✅ 详细的错误日志
- ✅ 显示问题 JSON 的前 500 字符用于调试

**日志输出：**
```
DEBUG - 原始 LLM 响应长度: 1234 字符
WARN  - JSON 直接解析失败，尝试清理后重试: Unexpected character...
DEBUG - 清理后的 JSON 长度: 1150 字符
INFO  - 尝试从文本中提取 JSON
ERROR - 问题 JSON 内容（前 500 字符）: {...
```

#### 2. `cleanJsonContent()` - JSON 清理方法

**清理操作：**

| 操作 | 描述 | 示例 |
|------|------|------|
| 移除前置文本 | 找到第一个 `{` 或 `[` | `这是结果：{...}` → `{...}` |
| 移除后置文本 | 找到最后一个 `}` 或 `]` | `{...} 完成` → `{...}` |
| 修复双重转义 | `\\\\` → `\\` | `"path": "C:\\\\"` → `"path": "C:\\"` |
| 修复引号转义 | `\\\"` → `\"` | `\\\\"text\\\\"` → `\\"text\\"` |
| 移除控制字符 | 删除 `\x00-\x1F`（除必要的） | 移除不可见字符 |

**实现细节：**
```java
// 修复双重转义
cleaned = cleaned.replaceAll("\\\\\\\\", "\\\\");

// 修复错误引号转义
cleaned = cleaned.replaceAll("\\\\\\\\\"", "\\\\\"");

// 移除控制字符
cleaned = cleaned.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
```

#### 3. `extractJsonFromText()` - JSON 提取方法

**算法：**
1. 扫描字符串，查找 `{` 或 `[`
2. 使用深度计数器跟踪嵌套层级
3. 当深度归零时，找到完整的 JSON 结构
4. 提取并返回

**示例：**
```
输入：Some text { "key": "value" } more text
                 ↑               ↑
              start=10         end=28
输出：{ "key": "value" }
```

## 使用场景

### 场景 1: LLM 返回带说明的 JSON
```
LLM 响应：
根据分析，风险评估结果如下：
{
  "riskLevel": "MEDIUM",
  "estimatedRowsExamined": 1000
}
以上是详细分析。
```

**处理流程：**
1. 策略 1 失败（包含额外文字）
2. 策略 2 清理前后文字
3. 成功解析

### 场景 2: 转义字符问题
```
LLM 响应：
{
  "reasoning": "路径是 C:\\\\Users\\\\test"
}
```

**处理流程：**
1. 策略 1 失败（双重转义）
2. 策略 2 修复转义：`\\\\` → `\\`
3. 成功解析为：`"C:\\Users\\test"`

### 场景 3: 混合问题
```
LLM 响应：
```json
{
  "riskLevel": "HIGH",
  "reasoning": "这个查询有问题，路径是 C:\\\\"
}
```
另外建议...
```

**处理流程：**
1. 移除 Markdown 标记
2. 策略 1 失败（有后置文字 + 转义问题）
3. 策略 2 清理转义和文字
4. 成功解析

## 错误处理

### 详细日志

#### DEBUG 级别
```java
logger.debug("原始 LLM 响应长度: {} 字符", jsonContent.length());
logger.debug("清理后的 JSON 长度: {} 字符", cleanedContent.length());
logger.debug("移除了 JSON 前的 {} 个字符", startPos);
```

#### WARN 级别
```java
logger.warn("JSON 直接解析失败，尝试清理后重试: {}", e.getMessage());
```

#### ERROR 级别
```java
logger.error("JSON 清理后仍然解析失败");
logger.error("问题 JSON 内容（前 500 字符）: {}", 
        jsonContent.substring(0, Math.min(500, jsonContent.length())));
```

### 异常信息

最终异常包含所有尝试的信息：
```java
throw new JsonProcessingException("JSON 解析失败，已尝试多种策略。原始错误: " + e.getMessage())
```

## 性能考虑

### 时间复杂度
- 策略 1: O(n) - JSON 解析
- 策略 2: O(n) - 字符串清理 + JSON 解析
- 策略 3: O(n) - 括号匹配 + JSON 解析

### 空间复杂度
- O(n) - 创建清理后的字符串副本

### 优化建议
1. **大部分情况走策略 1**：正常 JSON 直接解析，无额外开销
2. **缓存清理规则**：可以将正则表达式编译为 Pattern 对象
3. **限制日志输出**：只在 DEBUG 级别输出详细信息

## 配置建议

### 日志级别设置

**application.yml:**
```yaml
logging:
  level:
    com.biz.sccba.sqlanalyzer.service.SqlAgentService: DEBUG
```

**生产环境:**
```yaml
logging:
  level:
    com.biz.sccba.sqlanalyzer.service.SqlAgentService: WARN
```

### ObjectMapper 配置

可以配置 ObjectMapper 以提高容错性：
```java
ObjectMapper objectMapper = new ObjectMapper()
    .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
    .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true)
    .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
```

## 测试用例

### 测试 1: 正常 JSON
```java
String response = "{\"riskLevel\": \"LOW\"}";
SqlRiskPrediction result = parseJsonResponse(response, SqlRiskPrediction.class);
// 应该成功，使用策略 1
```

### 测试 2: 带 Markdown 的 JSON
```java
String response = "```json\n{\"riskLevel\": \"MEDIUM\"}\n```";
SqlRiskPrediction result = parseJsonResponse(response, SqlRiskPrediction.class);
// 应该成功，使用策略 1（移除 Markdown 后）
```

### 测试 3: 双重转义
```java
String response = "{\"reasoning\": \"路径 C:\\\\\\\\\"}";
SqlRiskPrediction result = parseJsonResponse(response, SqlRiskPrediction.class);
// 应该成功，使用策略 2
```

### 测试 4: 混合文本
```java
String response = "分析结果：{\"riskLevel\": \"HIGH\"} 完成";
SqlRiskPrediction result = parseJsonResponse(response, SqlRiskPrediction.class);
// 应该成功，使用策略 2 或 3
```

### 测试 5: 完全无效的 JSON
```java
String response = "这不是 JSON";
// 应该抛出 JsonProcessingException，包含详细错误信息
```

## 监控指标

### 建议监控的指标

1. **解析策略分布**
   - 策略 1 成功率：> 80%（理想）
   - 策略 2 成功率：> 15%
   - 策略 3 成功率：< 5%
   - 完全失败率：< 1%

2. **平均解析时间**
   - 策略 1: < 10ms
   - 策略 2: < 50ms
   - 策略 3: < 100ms

3. **失败率**
   - 总体失败率 < 1%
   - 如果失败率 > 5%，需要检查 Prompt 模板

### 添加监控代码示例

```java
// 在 parseJsonResponse 中添加
long startTime = System.currentTimeMillis();
try {
    T result = objectMapper.readValue(jsonContent, clazz);
    metricsService.recordParseSuccess("strategy1", System.currentTimeMillis() - startTime);
    return result;
} catch (JsonProcessingException e) {
    // ...
}
```

## 常见问题 FAQ

### Q1: 为什么不直接配置 ObjectMapper 允许所有格式？
**A:** 宽松的解析可能掩盖真正的问题。三层策略既保证了容错性，又能通过日志发现 Prompt 质量问题。

### Q2: 性能会受影响吗？
**A:** 不会。正常情况下使用策略 1 直接解析，只有异常时才触发后续策略。

### Q3: 如何调试解析失败的情况？
**A:** 
1. 设置日志级别为 DEBUG
2. 查看 "问题 JSON 内容" 日志
3. 检查 Prompt 模板是否需要优化

### Q4: 可以添加自定义清理规则吗？
**A:** 可以。在 `cleanJsonContent()` 方法中添加新的清理逻辑。

### Q5: 如何处理超大 JSON 响应？
**A:** 
- 当前实现对大 JSON 也适用
- 如果担心内存，可以限制 `extractJsonFromText()` 的扫描范围
- 考虑使用流式 JSON 解析器

## 未来改进

### 可能的增强

1. **智能 Prompt 修正**
   - 检测常见 LLM 错误模式
   - 自动调整 Prompt 模板
   - 反馈机制：失败案例 → Prompt 优化

2. **解析策略统计**
   - 记录各策略使用频率
   - 分析失败原因分布
   - 生成优化报告

3. **增量解析**
   - 对超大 JSON 使用流式解析
   - 减少内存占用

4. **自定义清理规则**
   - 允许配置额外的清理规则
   - 支持正则表达式配置

5. **机器学习辅助**
   - 学习常见的 LLM 响应模式
   - 预测可能的问题并预处理

## 版本历史

### v2.0 (2025-12-29)
- ✅ 添加三层解析策略
- ✅ 实现 `cleanJsonContent()` 方法
- ✅ 实现 `extractJsonFromText()` 方法
- ✅ 增强错误日志
- ✅ 修复双重转义问题
- ✅ 移除控制字符

### v1.0 (之前)
- ✅ 基础 JSON 解析
- ✅ Markdown 代码块处理

## 参考资料

- [Jackson ObjectMapper 文档](https://github.com/FasterXML/jackson-databind)
- [JSON 规范 (RFC 8259)](https://datatracker.ietf.org/doc/html/rfc8259)
- [Java 正则表达式](https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html)



