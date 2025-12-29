# SqlAgentService 重构实现总结

## 概述
成功重构 `SqlAgentService.java`，实现了基于 Spring AI 的 SQL 风险评估双阶段验证流程（Two-Stage Verification）。

## 实现的功能

### 1. 新建模型类

#### SqlRiskPrediction.java
- LLM 预测结果的结构化模型
- 包含风险等级、预估扫描行数、索引使用预测、建议参数等字段
- 使用 Jackson 注解支持 JSON 序列化/反序列化

#### SqlRiskAssessmentResponse.java
- 完整的风险评估响应模型
- 包含原始 SQL、预测结果、实际执行计划、对比结果等
- 支持直方图摘要和验证对比详情的嵌套类

### 2. Prompt 模板扩展

在 `PromptTemplateManagerService.java` 中添加了三个新的 Prompt 模板类型：

#### TYPE_SQL_RISK_ASSESSMENT
- DBA 角色的风险预测 Prompt
- 基于直方图数据预测 SQL 执行风险
- 输出 JSON 格式的结构化预测结果

#### TYPE_SQL_RISK_COMPARISON  
- 智能对比分析 Prompt
- 对比 LLM 预测与实际 EXPLAIN 结果
- 判断是否需要修正预测

#### TYPE_SQL_RISK_REFINEMENT
- LLM 修正 Prompt
- 基于实际执行计划修正预测结果
- 提供更准确的风险评估

### 3. 直方图数据获取

在 `SqlExecutionPlanService.java` 中添加：

#### getHistogramDataForSql()
- 从 SQL 语句中提取表名和 WHERE 条件列
- 查询 `information_schema.column_statistics` 获取直方图数据
- 返回 `List<ColumnStatisticsDTO>` 包含数据分布信息

#### extractWhereColumns()
- 使用正则表达式解析 WHERE 子句
- 提取条件中涉及的列名
- 支持多种条件格式（=, >, <, BETWEEN, IN, LIKE等）

### 4. SqlAgentService 核心重构

#### analyze() 主流程
```
1. Stage 1: Predictor
   - 获取直方图数据
   - 调用 LLM 预测 SQL 风险
   - 解析 JSON 响应为 SqlRiskPrediction

2. Stage 2: Verifier
   - 填充 SQL 参数（使用 LLM 建议的参数）
   - 执行 EXPLAIN FORMAT=JSON
   - 获取实际执行计划

3. Intelligent Comparison
   - 调用 LLM 对比预测与实际
   - 判断是否需要修正
   - 如果需要，执行 LLM 修正（仅一次）

4. Build Response
   - 组装完整的响应对象
   - 包含预测、实际、对比、修正等信息
```

#### 关键方法

**callPredictorLLM()**
- 使用 Spring AI ChatClient 调用 LLM
- 应用 DBA Prompt 模板
- 动态注入 SQL 和直方图数据
- 解析 JSON 输出为结构化对象

**callComparisonLLM()**
- 智能对比预测与实际执行计划
- 返回是否需要修正的判断
- 支持降级到规则判断

**callRefinementLLM()**
- 基于实际执行计划修正预测
- 仅调用一次（避免无限循环）
- 返回修正后的预测结果

**needsRefinementByRules()**
- 规则引擎降级方案
- 检查扫描行数偏差（>50% 或 >1000行）
- 检查索引使用不一致
- 检查访问类型不一致（预测高效但实际低效）

**fillSqlWithSuggestedParams()**
- 将 LLM 建议的参数代入 SQL
- 支持 ? 占位符、:param、#{param} 等格式
- 处理字符串、数字、NULL 等类型

### 5. 异常处理与降级

- **Stage 1 失败**：抛出异常，中止流程
- **Stage 2 失败**：降级返回预测结果（不执行 EXPLAIN）
- **Comparison 失败**：降级使用规则判断
- **Refinement 失败**：使用原始预测结果

### 6. 控制器更新

更新 `SqlAgentController.java`：
- 更改返回类型为 `SqlRiskAssessmentResponse`
- 更新日志信息为"风险评估"

## 技术栈使用

1. **Spring AI ChatClient**：统一的 LLM 调用接口
2. **PromptTemplate**：动态 Prompt 模板注入
3. **Jackson ObjectMapper**：JSON 序列化/反序列化
4. **JdbcTemplate**：执行 EXPLAIN 命令和查询直方图
5. **正则表达式**：SQL 解析（表名、列名、WHERE 条件）

## 数据流

```
SqlAgentRequest
    ↓
[获取直方图] → ColumnStatisticsDTO[]
    ↓
[LLM Predictor] → SqlRiskPrediction
    ↓
[填充参数] → Filled SQL
    ↓
[EXPLAIN] → ExecutionPlan
    ↓
[LLM Comparison] → needsRefinement?
    ↓ (if true)
[LLM Refinement] → SqlRiskPrediction (refined)
    ↓
[Build Response] → SqlRiskAssessmentResponse
```

## 主要特性

### ✅ 双阶段验证
- Stage 1: LLM 基于直方图预测
- Stage 2: 实际 EXPLAIN 验证

### ✅ 智能对比
- LLM 智能判断预测准确性
- 规则引擎降级方案

### ✅ 自动修正
- 发现偏差时自动修正
- 仅修正一次，避免循环

### ✅ 完善异常处理
- 多层次降级策略
- 详细日志记录

### ✅ 结构化输出
- JSON 格式 LLM 响应
- 类型安全的 Java 对象

## 配置要求

### 数据库
- MySQL 8.0+ （支持 `information_schema.column_statistics`）
- 需要有列的直方图统计数据

### Spring AI
- 配置至少一个 LLM ChatModel
- 在 `application.yml` 中配置 LLM 连接信息

### Prompt 模板
- 系统启动时自动初始化三个新模板
- 存储在 `prompt_templates` 表中
- 可通过管理接口动态修改

## API 使用示例

### 请求
```json
POST /api/sql-agent/analyze
{
  "sql": "SELECT * FROM users WHERE age > ? AND city = ?",
  "datasourceName": "default",
  "llmName": "deepseek"
}
```

### 响应
```json
{
  "originalSql": "SELECT * FROM users WHERE age > ? AND city = ?",
  "filledSql": "SELECT * FROM users WHERE age > 25 AND city = 'Beijing'",
  "histogramData": [...],
  "predictorResult": {
    "riskLevel": "MEDIUM",
    "estimatedRowsExamined": 500,
    "expectedIndexUsage": true,
    "expectedIndexName": "idx_age_city",
    "expectedAccessType": "range",
    "suggestedParameters": {"age": 25, "city": "Beijing"},
    "reasoning": "...",
    "recommendations": [...]
  },
  "actualExplainPlan": {...},
  "verificationComparison": {
    "matched": true,
    "details": {...},
    "summary": "预测与实际执行计划一致",
    "deviationSeverity": "NONE"
  },
  "refinementApplied": false,
  "finalRiskLevel": "MEDIUM",
  "recommendations": [...],
  "processingTimeMs": 1234
}
```

## 待优化项

1. **Prompt 优化**：根据实际使用效果调整 Prompt 模板内容
2. **缓存机制**：对相同 SQL 的直方图数据进行缓存
3. **并发控制**：LLM 调用的并发限流
4. **性能监控**：添加详细的性能指标采集
5. **单元测试**：补充完整的单元测试用例

## 总结

本次重构完全替换了原有的链式工作流，实现了更智能、更可靠的 SQL 风险评估系统。通过双阶段验证和智能修正机制，显著提高了风险评估的准确性，同时保持了良好的容错性和降级能力。




