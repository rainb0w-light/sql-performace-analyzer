# StringTemplate 解析错误终极修复

## 问题分析

### 错误信息
```
31:23: '"value"' came as a complete surprise to me
Caused by: org.stringtemplate.v4.compiler.STException: null
```

### 根本原因

之前我们只修复了 **静态模板** 中的花括号问题，但忽略了 **动态内容** 也会导致解析错误。

问题出在两个层面：

1. **静态模板层面**（已修复）
   - Prompt 模板文件中的 JSON 示例包含 `{` 和 `}`
   - 解决方案：改用文字描述

2. **动态内容层面**（本次修复）
   - `formatHistogramData()` 生成的内容包含特殊字符
   - 例如：采样值中的 `"value"`, `{key: value}` 等
   - 这些内容被插入到模板后，StringTemplate 尝试解析它们
   - 结果：`"value"` 中的双引号被误认为是模板语法

## 问题示例

### 动态内容示例
```java
String histogramSummary = formatHistogramData(histograms);
// 可能生成：
// 表: users, 列: name
//   示例值: ["Alice", "Bob", {"key": "value"}]
//           ↑ 这些引号和花括号会导致 ST4 解析失败
```

### 为什么会失败

```java
// 原来的代码
PromptTemplate promptTemplate = new PromptTemplate(templateContent);
params.put("histogram_data", histogramSummary); // 包含 "value", {}, 等
String prompt = promptTemplate.create(params).getContents();
// ST4 尝试解析整个字符串，遇到 "value" 时崩溃
```

## 解决方案

### 放弃 PromptTemplate，使用直接字符串替换

**修改前**（使用 PromptTemplate）：
```java
PromptTemplate promptTemplate = new PromptTemplate(templateContent);
Map<String, Object> params = new HashMap<>();
params.put("sql", sql);
params.put("histogram_data", histogramSummary);
String prompt = promptTemplate.create(params).getContents();
```

**修改后**（直接字符串替换）：
```java
String prompt = templateContent
        .replace("{sql}", sql)
        .replace("{histogram_data}", histogramSummary);
```

### 优势

1. **完全避免 StringTemplate 解析**
   - 不会触发 ST4 的任何解析逻辑
   - 动态内容中的任何字符都不会被误解析

2. **简单高效**
   - 代码更简洁
   - 性能更好（无需编译模板）
   - 更容易调试

3. **完全兼容**
   - 动态内容可以包含任何字符
   - 引号、花括号、特殊符号都不会有问题

## 修改的方法

修改了所有使用 PromptTemplate 的方法：

1. ✅ `callPredictorLLM()` - Stage 1 预测
2. ✅ `callSqlFillerLLM()` - SQL 参数填充
3. ✅ `callComparisonLLMMultiScenario()` - 多场景对比
4. ✅ `callRefinementLLMMultiScenario()` - 多场景修正
5. ✅ `callComparisonLLM()` - 单场景对比（兼容）
6. ✅ `callRefinementLLM()` - 单场景修正（兼容）

## 代码对比

### callPredictorLLM 修改

**修改前**：
```java
PromptTemplate promptTemplate = new PromptTemplate(templateContent);
Map<String, Object> params = new HashMap<>();
params.put("sql", sql);
params.put("histogram_data", histogramSummary);
String prompt = promptTemplate.create(params).getContents();
```

**修改后**：
```java
// 直接进行字符串替换（避免 StringTemplate 解析问题）
String prompt = templateContent
        .replace("{sql}", sql)
        .replace("{histogram_data}", histogramSummary);
```

### callRefinementLLMMultiScenario 修改

**修改前**：
```java
PromptTemplate promptTemplate = new PromptTemplate(templateContent);
Map<String, Object> params = new HashMap<>();
params.put("original_prediction", originalPredictionJson);
params.put("actual_explain", verificationsSummary);
params.put("histogram_data", histogramSummary);
params.put("deviation_details", deviationDetails);
String prompt = promptTemplate.create(params).getContents();
```

**修改后**：
```java
// 直接进行字符串替换（避免 StringTemplate 解析问题）
String prompt = templateContent
        .replace("{original_prediction}", originalPredictionJson)
        .replace("{actual_explain}", verificationsSummary)
        .replace("{histogram_data}", histogramSummary)
        .replace("{deviation_details}", deviationDetails);
```

## 技术细节

### StringTemplate (ST4) 的限制

StringTemplate 设计用于**模板编译**，有严格的语法规则：
- `{var}` - 变量引用
- `<...>` - 表达式
- `"..."` - 字符串字面量
- 特殊字符需要转义

当动态内容包含这些字符时，ST4 会尝试解析它们，导致错误。

### 为什么 Spring AI 使用 StringTemplate

Spring AI 的 `PromptTemplate` 设计用于：
- 支持复杂的模板逻辑
- 条件渲染
- 循环
- 函数调用

但对于我们的简单用例（仅需变量替换），这些功能是不必要的。

### 直接字符串替换的适用场景

✅ **适用于**：
- 简单的变量替换
- 动态内容可能包含特殊字符
- 不需要条件逻辑或循环

❌ **不适用于**：
- 需要条件渲染（if/else）
- 需要循环（foreach）
- 需要复杂的表达式计算

对于我们的 Prompt 使用场景，直接字符串替换完全足够。

## 其他修改

### 移除 PromptTemplate 导入

```java
// 移除
import org.springframework.ai.chat.prompt.PromptTemplate;
```

### Linter 警告

保留了一些未使用的兼容方法（warnings）：
- `callComparisonLLM()` - 单场景对比
- `callRefinementLLM()` - 单场景修正
- `buildResponse()` - 单场景响应构建
- `buildDegradedResponse()` - 单场景降级响应

这些方法保留用于向后兼容，可以安全忽略 warnings。

## 验证

修改后的代码：
- ✅ 不再抛出 `STException`
- ✅ 可以处理任何动态内容（引号、花括号等）
- ✅ 功能完全等价
- ✅ 代码更简洁
- ✅ 性能更好

## 经验教训

### 1. 理解第三方库的限制
StringTemplate (ST4) 是一个强大但严格的模板引擎，不适合处理不可控的动态内容。

### 2. 选择合适的工具
对于简单的变量替换，直接字符串操作比复杂的模板引擎更可靠。

### 3. 考虑动态内容的影响
在使用模板引擎时，必须考虑动态插入的内容是否会影响模板解析。

### 4. 优先考虑简单方案
不要过度使用框架提供的"高级"功能，如果简单方案就能解决问题。

## 总结

通过放弃 `PromptTemplate` 改用直接字符串替换，我们彻底解决了 StringTemplate 解析错误问题。这个方案：
- **更简单**：减少了依赖和复杂度
- **更可靠**：不受动态内容中特殊字符的影响
- **更高效**：无需模板编译开销
- **更易维护**：代码清晰直观

现在系统可以正常处理任何包含特殊字符的直方图数据！🎉

