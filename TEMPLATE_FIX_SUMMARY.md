# Spring AI PromptTemplate 错误修复总结

## 问题描述

执行时报错：
```
java.lang.RuntimeException: LLM 预测失败: The template string is not valid.
Caused by: java.lang.IllegalArgumentException: The template string is not valid.
Caused by: org.stringtemplate.v4.compiler.STException: null
```

## 根本原因

Spring AI 的 `PromptTemplate` 使用 **StringTemplate (ST4)** 引擎来解析模板字符串。ST4 使用 `{` 和 `}` 作为变量占位符的语法标记。

我们的 Prompt 模板中包含了大量 JSON 示例，例如：
```json
{
  "riskLevel": "LOW",
  "estimatedRowsExamined": 100,
  ...
}
```

这些 JSON 中的花括号被 ST4 误认为是模板变量语法，导致解析失败。

## 解决方案

### 方案选择

有三种解决方案：
1. ✅ **转换为文字描述**（已采用）- 用文字说明 JSON 结构，不包含花括号
2. ❌ 转义花括号 - 使用 `\{` 和 `\}` 转义（但会影响可读性）
3. ❌ 更换模板引擎 - 修改 Spring AI 配置（侵入性太强）

### 实施的修改

将所有 Prompt 模板中的 JSON 示例从：

**修改前**：
```
## 输出格式：

```json
{
  "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
  "estimatedRowsExamined": 数字,
  "expectedIndexUsage": true/false,
  ...
}
```
```

**修改后**：
```
## 输出格式：

JSON结构说明：
- riskLevel: 字符串，值为 LOW, MEDIUM, HIGH, CRITICAL 之一
- estimatedRowsExamined: 数字，预估扫描行数
- expectedIndexUsage: 布尔值，true 或 false
...
```

## 修改的模板

修改了以下 4 个 Prompt 模板中的 JSON 示例：

### 1. `TYPE_SQL_RISK_ASSESSMENT`
- **位置**: `getDefaultSqlRiskAssessmentTemplate()`
- **修改**: 将 JSON 示例改为字段说明列表

### 2. `TYPE_SQL_RISK_COMPARISON`
- **位置**: `getDefaultSqlRiskComparisonTemplate()`
- **修改**: 将 JSON 示例改为字段说明列表

### 3. `TYPE_SQL_RISK_REFINEMENT`
- **位置**: `getDefaultSqlRiskRefinementTemplate()`
- **修改**: 将 JSON 示例改为字段说明列表

### 4. `TYPE_SQL_PARAMETER_FILLING`
- **位置**: `getDefaultSqlParameterFillingTemplate()`
- **修改**: 将 JSON 示例改为字段说明 + 示例说明（不使用花括号）

## 修改示例对比

### SQL Risk Assessment Template

**修改前**：
```
```json
{
  "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
  "estimatedRowsExamined": 数字,
  "expectedIndexUsage": true/false,
  "expectedIndexName": "索引名称或null",
  "expectedAccessType": "ALL|index|range|ref|eq_ref|const",
  "estimatedQueryCost": 数字,
  "suggestedParameters": {
    "参数名或列名": "建议的测试值"
  },
  "reasoning": "详细的预测理由",
  "recommendations": [
    "优化建议1",
    "优化建议2",
    "优化建议3"
  ]
}
```
```

**修改后**：
```
JSON结构示例：
- riskLevel: 字符串，值为 LOW, MEDIUM, HIGH, CRITICAL 之一
- estimatedRowsExamined: 数字，预估扫描行数
- expectedIndexUsage: 布尔值，true 或 false
- expectedIndexName: 字符串，索引名称或 null
- expectedAccessType: 字符串，ALL, index, range, ref, eq_ref, const 之一
- estimatedQueryCost: 数字，查询成本
- suggestedParameters: 对象，键值对表示参数名和建议值
- reasoning: 字符串，详细的预测理由
- recommendations: 数组，包含优化建议字符串
```

### SQL Parameter Filling Template

**修改前**：
```
```json
{
  "originalSql": "原始 SQL 模板",
  "scenarios": [
    {
      "scenarioName": "最小值场景",
      "filledSql": "SELECT * FROM users WHERE age > 18 AND city = 'Beijing'",
      "parameters": {
        "age": 18,
        "city": "Beijing"
      },
      "description": "使用年龄最小值和第一个城市值，测试下边界执行计划"
    }
  ],
  "reasoning": "基于直方图数据..."
}
```
```

**修改后**：
```
JSON结构说明：
- originalSql: 原始 SQL 模板字符串
- scenarios: 场景数组，每个场景包含：
  - scenarioName: 场景名称（如"最小值场景"）
  - filledSql: 填充好的完整 SQL 语句
  - parameters: 参数对象，键为参数名，值为参数值
  - description: 场景描述，说明为什么选择这些参数
- reasoning: 整体推理过程，解释参数选择的依据

示例说明：
对于 SQL "SELECT * FROM users WHERE age > ? AND city = ?"
可以生成三个场景：
1. 最小值场景：age=18, city='Beijing'
2. 最大值场景：age=65, city='Shanghai'  
3. 典型值场景：age=35, city='Guangzhou'
```

## 优势

### 1. 完全兼容 StringTemplate
- 不包含任何花括号，不会被误解析
- 模板可以正常通过 ST4 编译

### 2. 保持可读性
- 文字描述更清晰
- 示例说明更直观
- LLM 依然能理解 JSON 结构要求

### 3. 更灵活
- 可以添加更多说明和注释
- 不受 JSON 格式限制
- 更容易维护和修改

## 验证

修改后：
- ✅ 模板可以正常解析
- ✅ 不再抛出 `STException`
- ✅ LLM 依然能够理解输出格式要求
- ✅ JSON 响应格式不受影响

## 注意事项

### 1. LLM 理解能力
虽然我们移除了 JSON 示例，但通过清晰的字段说明，现代 LLM（如 GPT-4、Claude、DeepSeek）依然能够理解并生成正确的 JSON 格式。

### 2. 未来维护
如果需要添加新的 Prompt 模板：
- ❌ 不要在模板中使用 `{` 和 `}` 包裹的 JSON 示例
- ✅ 使用文字描述 JSON 结构
- ✅ 可以使用示例说明（不包含花括号）

### 3. 其他模板引擎
如果将来需要更复杂的模板功能，可以考虑：
- 使用 Spring AI 的其他模板选项
- 自定义模板解析逻辑
- 但当前的文字描述方案已经足够

## 总结

通过将 JSON 示例转换为文字描述，成功解决了 StringTemplate 解析错误，同时保持了 Prompt 的清晰性和 LLM 的理解能力。这是一个简单、有效、可维护的解决方案。

