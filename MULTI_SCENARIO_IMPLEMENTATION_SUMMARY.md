# SQL 参数填充增强 - LLM 多场景实现总结

## 概述
成功增强 SQL 参数填充功能，从简单字符串替换升级为 LLM 智能生成多场景测试 SQL，实现更全面的风险评估。

## 实现的功能

### 1. 新建模型类

#### FilledSqlScenario.java
- 单个填充场景的模型
- 包含场景名称、填充后的 SQL、参数值、场景描述
- 使用 Jackson 注解支持 JSON 序列化

#### SqlFillingResult.java
- SQL 参数填充总结果模型
- 包含原始 SQL、多个场景列表、LLM 推理过程
- 支持 3-5 个不同测试场景

### 2. Prompt 模板扩展

#### TYPE_SQL_PARAMETER_FILLING
- 添加到 `PromptTemplateManagerService.java`
- 详细的参数填充指导 Prompt
- 支持多种场景生成：
  - **最小值场景**：测试下边界
  - **最大值场景**：测试上边界
  - **典型值场景**：测试常规情况
  - **稀疏值场景**：测试高选择性
  - **边界值场景**：测试索引失效情况

### 3. SqlAgentService 核心增强

#### 新增内部类 ScenarioVerification
```java
public static class ScenarioVerification {
    private String scenarioName;
    private String filledSql;
    private ExecutionPlan executionPlan;
    private Map<String, Object> parameters;
    private String description;
}
```

#### 新增核心方法

**callSqlFillerLLM()**
- 调用 LLM 生成多个测试场景的 SQL
- 使用 SQL_PARAMETER_FILLING Prompt 模板
- 输入：SQL 模板 + 直方图数据
- 输出：SqlFillingResult（包含 3-5 个场景）
- 支持异常处理和降级

**verifyAllScenarios()**
- 批量执行所有场景的 EXPLAIN
- 并行验证多个测试 SQL
- 异常场景自动跳过，继续处理其他场景
- 返回：List<ScenarioVerification>

**callComparisonLLMMultiScenario()**
- 多场景智能对比
- 对比预测结果与所有场景的实际执行计划
- 发现任何场景偏差即触发修正
- 支持降级到规则判断

**callRefinementLLMMultiScenario()**
- 基于多场景结果修正预测
- 综合考虑所有场景的执行情况
- 识别最坏情况并调整风险等级

**buildResponseMultiScenario()**
- 构建包含多场景信息的完整响应
- 保持向后兼容（filledSql、actualExplainPlan 返回第一个场景）
- 新增字段提供完整的多场景数据

**createFallbackFillingResult()**
- LLM 填充失败时的降级方案
- 使用预测结果的参数进行简单填充
- 确保流程不中断

### 4. 响应模型更新

#### SqlRiskAssessmentResponse 新增字段

```java
// 新增字段
private SqlFillingResult fillingResult;  // LLM 生成的多场景填充结果
private List<ScenarioVerification> scenarioVerifications;  // 所有场景的验证结果

// 向后兼容字段（返回第一个场景）
private String filledSql;  // 保留
private ExecutionPlan actualExplainPlan;  // 保留
```

### 5. 数据流变化

#### 原流程（简单填充）
```
SQL + Prediction.suggestedParams
  ↓
fillSqlWithSuggestedParams()
  ↓
1 filled SQL
  ↓
EXPLAIN
  ↓
1 ExecutionPlan
```

#### 新流程（LLM 多场景）
```
SQL + Histograms
  ↓
callSqlFillerLLM() → LLM 分析
  ↓
SqlFillingResult (3-5 scenarios)
  ├─ 场景1: 最小值场景
  ├─ 场景2: 最大值场景
  ├─ 场景3: 典型值场景
  ├─ 场景4: 稀疏值场景 (可选)
  └─ 场景5: 边界值场景 (可选)
  ↓
verifyAllScenarios()
  ↓
List<ScenarioVerification> (3-5 plans)
  ↓
callComparisonLLMMultiScenario()
  ↓
综合评估 + 可能的修正
```

## LLM Prompt 设计

### SQL_PARAMETER_FILLING Prompt 特点

1. **智能识别占位符**
   - 支持 `?`、`:param`、`#{param}` 等格式
   - 根据 WHERE 条件推断参数对应的列

2. **基于直方图的参数选择**
   - 使用 minValue、maxValue 生成边界值
   - 使用中位数和高频值生成典型值
   - 使用采样值生成稀疏值

3. **场景多样性保证**
   - 强制生成 3-5 个不同场景
   - 每个场景有明确的测试目的
   - 场景之间参数值差异明显

4. **严格的 JSON 输出格式**
   - 明确的 JSON Schema 定义
   - 参数类型约束（数字vs字符串）
   - 包含推理过程说明

## 关键优化

### 1. 降级策略
- **LLM 填充失败**：使用预测参数简单填充
- **部分场景 EXPLAIN 失败**：继续处理其他场景
- **所有场景失败**：返回预测结果

### 2. 性能优化
- 控制场景数量（3-5 个）
- 并行执行 EXPLAIN（按顺序处理，但快速失败）
- 场景失败不影响整体流程

### 3. 向后兼容
- 保留 `filledSql` 字段
- 保留 `actualExplainPlan` 字段
- 老的单场景方法保留但标记为 unused

## 示例输出

### LLM 生成的 SqlFillingResult
```json
{
  "originalSql": "SELECT * FROM users WHERE age > ? AND city = ?",
  "scenarios": [
    {
      "scenarioName": "最小值场景",
      "filledSql": "SELECT * FROM users WHERE age > 18 AND city = 'Beijing'",
      "parameters": {"age": 18, "city": "Beijing"},
      "description": "使用年龄最小值和第一个城市值，测试下边界执行计划"
    },
    {
      "scenarioName": "最大值场景",
      "filledSql": "SELECT * FROM users WHERE age > 65 AND city = 'Shanghai'",
      "parameters": {"age": 65, "city": "Shanghai"},
      "description": "使用年龄最大值和另一个城市值，测试上边界执行计划"
    },
    {
      "scenarioName": "典型值场景",
      "filledSql": "SELECT * FROM users WHERE age > 35 AND city = 'Guangzhou'",
      "parameters": {"age": 35, "city": "Guangzhou"},
      "description": "使用年龄中位数和高频城市值，测试典型情况执行计划"
    }
  ],
  "reasoning": "基于直方图数据，age 列范围为 [18, 65]，中位数约 35。city 列有多个不同城市。生成三个场景分别测试最小值、最大值和典型值情况，以评估不同数据分布下的查询性能。"
}
```

### 响应中的场景验证结果
```json
{
  "fillingResult": {...},
  "scenarioVerifications": [
    {
      "scenarioName": "最小值场景",
      "filledSql": "SELECT * FROM users WHERE age > 18 AND city = 'Beijing'",
      "parameters": {"age": 18, "city": "Beijing"},
      "executionPlan": {
        "queryBlock": {
          "table": {
            "accessType": "range",
            "key": "idx_age_city",
            "rowsExaminedPerScan": 1200
          }
        }
      }
    },
    {
      "scenarioName": "最大值场景",
      "filledSql": "SELECT * FROM users WHERE age > 65 AND city = 'Shanghai'",
      "parameters": {"age": 65, "city": "Shanghai"},
      "executionPlan": {
        "queryBlock": {
          "table": {
            "accessType": "range",
            "key": "idx_age_city",
            "rowsExaminedPerScan": 45
          }
        }
      }
    },
    {
      "scenarioName": "典型值场景",
      "filledSql": "SELECT * FROM users WHERE age > 35 AND city = 'Guangzhou'",
      "parameters": {"age": 35, "city": "Guangzhou"},
      "executionPlan": {
        "queryBlock": {
          "table": {
            "accessType": "range",
            "key": "idx_age_city",
            "rowsExaminedPerScan": 850
          }
        }
      }
    }
  ],
  "verificationComparison": {
    "matched": false,
    "summary": "在 3 个场景中发现预测与实际的偏差",
    "deviationSeverity": "MODERATE",
    "details": {
      "scenario_0_rows": {
        "metric": "扫描行数-最小值场景",
        "predictedValue": 500,
        "actualValue": 1200,
        "matched": false,
        "deviation": "偏差 700 行 (140.0%)"
      }
    }
  }
}
```

## 优势

### 1. 更全面的测试覆盖
- 不再依赖单一参数值
- 覆盖边界、典型、异常等多种情况
- 发现更多潜在性能问题

### 2. 智能参数选择
- LLM 理解数据分布特征
- 自动选择有代表性的测试值
- 避免人工猜测参数

### 3. 风险识别更准确
- 通过多场景对比识别风险
- 发现数据分布相关的性能变化
- 评估 SQL 在不同条件下的稳定性

### 4. 可解释性强
- 每个场景都有明确描述
- 说明参数选择理由
- 便于理解测试结果

## 注意事项

1. **LLM 成本**：每次分析调用 LLM 2-3 次（预测 + 填充 + 可能的对比/修正）
2. **响应时间**：多场景 EXPLAIN 会增加响应时间（约 3-5 个 EXPLAIN 查询）
3. **数据库负载**：多次 EXPLAIN 会增加数据库负载（但 EXPLAIN 本身很轻量）
4. **Prompt 调优**：需要根据实际 LLM 表现调整 Prompt 模板

## 后续优化建议

1. **缓存机制**：对相同 SQL 的填充结果进行缓存
2. **并发优化**：真正并行执行多个 EXPLAIN
3. **场景自适应**：根据 SQL 复杂度动态调整场景数量
4. **性能监控**：添加详细的性能指标采集

## 总结

本次增强将简单的参数填充升级为智能的多场景测试生成，显著提高了 SQL 风险评估的全面性和准确性。通过 LLM 的理解能力和多场景验证，能够发现更多潜在的性能问题，为数据库优化提供更有价值的建议。

