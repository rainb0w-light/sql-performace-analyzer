# JSON 解析库和工具对比

## 通用的 JSON 处理方法

### 1. Apache Commons Text - StringEscapeUtils

**库依赖：**
```gradle
implementation 'org.apache.commons:commons-text:1.11.0'
```

**主要方法：**

#### `StringEscapeUtils.escapeJson(String)`
将字符串转义为 JSON 格式：
```java
import org.apache.commons.text.StringEscapeUtils;

String raw = "路径是 C:\\Users\\test";
String escaped = StringEscapeUtils.escapeJson(raw);
// 结果: "路径是 C:\\\\Users\\\\test"
```

#### `StringEscapeUtils.unescapeJson(String)`
将 JSON 转义字符还原：
```java
String json = "路径是 C:\\\\Users\\\\test";
String unescaped = StringEscapeUtils.unescapeJson(json);
// 结果: "路径是 C:\Users\test"
```

**优点：**
- ✅ Apache 官方维护
- ✅ 成熟稳定
- ✅ 处理标准的 JSON 转义

**缺点：**
- ❌ 只处理字符串内容，不处理 JSON 结构
- ❌ 无法修复格式错误的 JSON
- ❌ 无法移除非 JSON 文本

---

### 2. Jackson - 配置宽松模式

**库依赖：**
```gradle
implementation 'com.fasterxml.jackson.core:jackson-databind:2.16.0'
```

**配置选项：**

#### 允许未引号的字段名
```java
ObjectMapper mapper = new ObjectMapper();
mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);

// 可以解析: {name: "value"} 而不是标准的 {"name": "value"}
```

#### 允许单引号
```java
mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

// 可以解析: {'name': 'value'}
```

#### 允许注释
```java
mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);

// 可以解析:
// {
//   "name": "value" // 这是注释
// }
```

#### 允许控制字符
```java
mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

// 可以解析包含 \n \t 等未转义的控制字符
```

#### 忽略未知属性
```java
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

// 可以解析: {"name": "value", "unknown": "field"}
// 即使目标类没有 unknown 字段
```

#### 允许反斜杠转义任何字符
```java
mapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);

// 可以解析: "path\:value" (虽然 \: 不是标准转义)
```

**优点：**
- ✅ 配置灵活
- ✅ 性能优秀
- ✅ 广泛使用

**缺点：**
- ❌ 只能处理接近 JSON 格式的字符串
- ❌ 无法处理完全错误的格式
- ❌ 宽松配置可能掩盖问题

---

### 3. Google Gson - 宽松模式

**库依赖：**
```gradle
implementation 'com.google.code.gson:gson:2.10.1'
```

**宽松模式：**
```java
Gson gson = new GsonBuilder()
    .setLenient()  // 启用宽松模式
    .create();

// 可以解析不太严格的 JSON
```

**优点：**
- ✅ 使用简单
- ✅ 宽松模式很实用
- ✅ 自动处理很多边界情况

**缺点：**
- ❌ 如果已使用 Jackson，引入 Gson 会增加依赖
- ❌ 两个 JSON 库可能产生冲突

---

### 4. 正则表达式清理

**移除控制字符：**
```java
// 移除所有控制字符（除了必要的空白）
String cleaned = jsonString.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
```

**修复转义问题：**
```java
// 修复双重转义
String fixed = jsonString.replaceAll("\\\\\\\\", "\\\\");
```

**移除非 ASCII 可打印字符：**
```java
String cleaned = jsonString.replaceAll("[^\\x20-\\x7E]", "");
```

**优点：**
- ✅ 无需额外依赖
- ✅ 灵活可控

**缺点：**
- ❌ 容易出错
- ❌ 需要深入理解 JSON 规范
- ❌ 维护成本高

---

## 推荐方案

### 方案 A: Jackson 宽松配置（推荐）

**适用场景：** JSON 格式基本正确，只是不够严格

```java
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 宽松解析配置
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        mapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        return mapper;
    }
}
```

**优点：**
- 无需额外依赖
- 配置简单
- 性能好

---

### 方案 B: 自定义清理 + Jackson（当前实现）

**适用场景：** LLM 返回的 JSON 可能包含说明文字、Markdown 标记等

```java
private <T> T parseJsonResponse(String response, Class<T> clazz) {
    // 1. 移除 Markdown 标记
    String cleaned = removeMarkdown(response);
    
    // 2. 清理常见问题
    cleaned = cleanJsonContent(cleaned);
    
    // 3. 使用 Jackson 解析
    return objectMapper.readValue(cleaned, clazz);
}
```

**优点：**
- 处理 LLM 特有的问题
- 容错能力强
- 可自定义规则

---

### 方案 C: Apache Commons Text + Jackson

**适用场景：** 需要标准的 JSON 转义处理

```java
import org.apache.commons.text.StringEscapeUtils;

private <T> T parseJsonResponse(String response, Class<T> clazz) {
    // 1. 提取 JSON 部分
    String jsonPart = extractJson(response);
    
    // 2. 如果是已转义的字符串，先反转义
    String unescaped = StringEscapeUtils.unescapeJson(jsonPart);
    
    // 3. 使用 Jackson 解析
    return objectMapper.readValue(unescaped, clazz);
}
```

**优点：**
- 使用标准库
- 处理标准转义
- 代码简洁

**缺点：**
- 需要额外依赖
- 可能不适合 LLM 场景

---

## 对比表格

| 方案 | 依赖 | 复杂度 | 容错能力 | LLM 适用 | 推荐度 |
|------|------|--------|----------|----------|--------|
| Jackson 宽松配置 | 无 | ⭐ | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐ |
| 自定义清理 + Jackson | 无 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Apache Commons Text | commons-text | ⭐⭐ | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ |
| Gson 宽松模式 | gson | ⭐ | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ |
| 纯正则表达式 | 无 | ⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐ | ⭐⭐ |

---

## 实际应用建议

### 对于我们的 SQL Agent 项目

**最佳实践：组合方案**

```java
@Service
public class SqlAgentService {
    
    // 1. 配置宽松的 ObjectMapper
    private final ObjectMapper objectMapper;
    
    @Autowired
    public SqlAgentService() {
        this.objectMapper = new ObjectMapper();
        configureMapper(this.objectMapper);
    }
    
    private void configureMapper(ObjectMapper mapper) {
        // 允许控制字符
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        // 允许反斜杠转义任何字符
        mapper.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        // 忽略未知属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    // 2. 保留自定义清理逻辑
    private <T> T parseJsonResponse(String response, Class<T> clazz) {
        // 策略 1: 移除 Markdown + 直接解析
        try {
            String cleaned = removeMarkdownMarkers(response);
            return objectMapper.readValue(cleaned, clazz);
        } catch (JsonProcessingException e1) {
            // 策略 2: 深度清理 + 解析
            try {
                String cleaned = cleanJsonContent(response);
                return objectMapper.readValue(cleaned, clazz);
            } catch (JsonProcessingException e2) {
                // 策略 3: 提取 JSON + 解析
                String extracted = extractJsonFromText(response);
                return objectMapper.readValue(extracted, clazz);
            }
        }
    }
}
```

---

## 各库的适用场景

### 使用 Jackson 宽松配置
```
✅ JSON 格式基本正确
✅ 包含未转义的控制字符
✅ 需要高性能
✅ 已在使用 Jackson
```

### 使用 Apache Commons Text
```
✅ 需要标准的 JSON 转义/反转义
✅ 处理用户输入的字符串
✅ 构建 JSON 字符串
❌ 修复格式错误的 JSON（不适合）
```

### 使用自定义清理
```
✅ LLM 返回的响应
✅ 包含说明文字的 JSON
✅ Markdown 包裹的 JSON
✅ 需要提取 JSON 片段
```

---

## 推荐的依赖版本

```gradle
dependencies {
    // Jackson (核心，必需)
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.16.1'
    
    // Apache Commons Text (可选，如需标准转义处理)
    implementation 'org.apache.commons:commons-text:1.11.0'
    
    // Gson (可选，如需备选方案)
    // implementation 'com.google.code.gson:gson:2.10.1'
}
```

---

## 总结

### ✅ 推荐使用的组合
1. **Jackson 宽松配置** - 作为基础解析器
2. **自定义清理逻辑** - 处理 LLM 特有问题
3. **Apache Commons Text** - 可选，用于标准转义处理

### ❌ 不推荐
1. 完全依赖正则表达式
2. 引入多个 JSON 库（造成依赖冲突）
3. 过度宽松的配置（掩盖真正的问题）

### 🎯 针对你的问题
**是的，Apache 有通用方法：**
- `org.apache.commons.text.StringEscapeUtils.escapeJson()`
- `org.apache.commons.text.StringEscapeUtils.unescapeJson()`

**但对于 LLM 场景：**
- 这些方法不够用，因为 LLM 返回的不只是转义问题
- 还有 Markdown 标记、说明文字、结构问题等
- 所以我们的自定义方案 + Jackson 宽松配置是最佳选择

### 💡 建议
如果你想使用 Apache Commons Text，可以这样结合：
```java
// 先用自定义方法清理格式
String cleaned = cleanJsonContent(response);
// 再用 Apache 方法处理转义
String unescaped = StringEscapeUtils.unescapeJson(cleaned);
// 最后用 Jackson 解析
return objectMapper.readValue(unescaped, clazz);
```

但在实践中，配置好的 Jackson + 自定义清理已经足够强大了。



