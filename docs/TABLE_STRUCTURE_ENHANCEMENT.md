# 表结构和索引信息增强说明

## 问题背景

原先的 `callPredictorLLM` 方法在进行 SQL 风险预测时，只提供了直方图数据，没有提供表结构和索引信息给 LLM。这导致 LLM 无法准确评估查询是否能使用索引，从而产生不准确的预测结果。

### 典型问题示例

在之前的测试中：
- **LLM 预测**：风险等级 HIGH，预估扫描行数 2000，预期不使用索引，访问类型 ALL（全表扫描）
- **实际执行**：使用 `idx_username` 索引，访问类型 ref，实际扫描行数 1，查询成本 0.35

预测与实际差距巨大，主要原因是 LLM 不知道表中已存在 `idx_username` 索引。

## 解决方案

### 1. 获取表结构和索引信息

利用 `SqlExecutionPlanService` 中已有的 `getTableStructures()` 方法获取：
- **表统计信息**：总行数、数据大小、索引大小、存储引擎
- **列信息**：列名、数据类型、是否可空、键类型、默认值、额外属性
- **索引信息**：索引名、索引列、是否唯一、索引类型（BTREE、HASH等）

### 2. 格式化表结构信息

新增 `formatTableStructures()` 方法，将表结构信息格式化为易读的文本格式：

```
=== 表: users ===
总行数: 5, 数据大小: 16384 bytes, 索引大小: 32768 bytes, 引擎: InnoDB

列信息:
  - id: int NOT NULL PRIMARY KEY auto_increment
  - username: varchar NOT NULL
  - email: varchar NOT NULL
  - password_hash: varchar NOT NULL
  - full_name: varchar
  - age: int
  - status: varchar
  - created_at: timestamp
  - updated_at: timestamp

索引信息:
  - PRIMARY (BTREE): [id] UNIQUE
  - idx_username (BTREE): [username]
  - idx_email (BTREE): [email]
```

### 3. 更新方法签名

#### `callPredictorLLM` 方法
- 添加重载方法，接受 `datasourceName` 参数
- 在方法内获取表结构信息并传递给 LLM

```java
private SqlRiskPrediction callPredictorLLM(String sql, List<ColumnStatisticsDTO> histograms, 
                                            String llmName, String datasourceName)
```

#### `callSqlFillerLLM` 方法
- 同样添加重载方法，接受 `datasourceName` 参数
- 让 LLM 在生成测试场景时也能考虑索引情况

```java
private SqlFillingResult callSqlFillerLLM(String sql, List<ColumnStatisticsDTO> histograms, 
                                           String llmName, String datasourceName)
```

### 4. 更新 Prompt 模板

#### SQL 风险评估模板（TYPE_SQL_RISK_ASSESSMENT）

添加了 `{table_structure}` 占位符，并更新了任务要求：

**重点强调**：
- LLM 必须**仔细检查表中已有的索引**
- `expectedIndexName` 必须从表结构中的**实际索引**中选择
- 如果有索引但预测不会使用，必须**说明原因**
- 推理过程中要**详细说明为什么选择或不选择某个索引**

#### SQL 参数填充模板（TYPE_SQL_PARAMETER_FILLING）

同样添加了 `{table_structure}` 占位符，让 LLM 在生成测试场景时也能考虑索引覆盖情况。

### 5. 更新调用点

在 `analyze()` 方法中：

```java
// Stage 1: 预测时传递数据源名称
SqlRiskPrediction prediction = callPredictorLLM(request.getSql(), histograms, 
        request.getLlmName(), request.getDatasourceName());

// Stage 2: 生成测试场景时也传递数据源名称
SqlFillingResult fillingResult = callSqlFillerLLM(request.getSql(), histograms, 
        request.getLlmName(), request.getDatasourceName());
```

## 预期效果

### 改进前
- LLM 不知道索引信息 → 预测为全表扫描
- 预测风险等级：HIGH
- 预测扫描行数：2000
- 预测不使用索引

### 改进后
- LLM 知道存在 `idx_username` 索引 → 准确预测使用索引
- 预测风险等级：LOW
- 预测扫描行数：1-10（基于索引）
- 预测使用 `idx_username` 索引，访问类型 ref

## 向后兼容性

- 保留了原有的方法签名作为重载方法（不带 `datasourceName` 参数）
- 如果不提供数据源信息，会在表结构字段中显示"未提供数据源信息"
- 不会破坏现有代码的调用

## 文件修改清单

1. **SqlAgentService.java**
   - 添加 `formatTableStructures()` 方法
   - 更新 `callPredictorLLM()` 方法（添加重载）
   - 更新 `callSqlFillerLLM()` 方法（添加重载）
   - 更新 `analyze()` 方法中的调用

2. **PromptTemplateManagerService.java**
   - 更新 `getDefaultSqlRiskAssessmentTemplate()` 模板
   - 更新 `getDefaultSqlParameterFillingTemplate()` 模板

3. **无需修改**
   - `SqlExecutionPlanService.java`（已有所需方法）
   - 数据模型类（无需变更）

## 测试建议

使用相同的 SQL 进行测试：
```sql
SELECT * FROM users WHERE username = ? AND email = ?
```

对比修改前后的预测结果，应该能看到：
1. LLM 正确识别 `idx_username` 索引
2. 预测的扫描行数更接近实际值
3. 风险等级评估更准确
4. 推理过程中会提到具体的索引名称

## 注意事项

1. **性能影响**：每次预测都会额外查询 `INFORMATION_SCHEMA` 获取表结构，会增加少量数据库查询开销
2. **权限要求**：需要对 `INFORMATION_SCHEMA.COLUMNS` 和 `INFORMATION_SCHEMA.STATISTICS` 有查询权限
3. **异常处理**：如果获取表结构失败，会降级处理，不会影响整体流程
4. **日志输出**：新增了日志输出，可以看到获取到的表结构数量

