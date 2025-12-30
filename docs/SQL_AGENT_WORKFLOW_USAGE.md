# SQL Agent 链式工作流使用指南

## 概述

SQL Agent 是一个基于 Spring AI 链式工作流的智能 SQL 分析系统，能够深度分析 SQL 性能并提供优化建议。它结合了 MySQL 统计信息、直方图、执行计划，并通过大模型（LLM）进行智能解读。

## 核心功能

### 1. 数据分布分析
- 从 MySQL `information_schema.COLUMN_STATISTICS` 获取直方图数据
- LLM 解读数据分布特征（倾斜度、范围分布等）
- 评估特定数据分布下 SQL 的执行效率（尤其是范围查询）

### 2. SQL 实例化
- 根据直方图中的采样值生成具体的 SQL 场景
- 支持多种数据分布场景（最小值、最大值、中位数等）
- 将带参数的 SQL 转换为可执行的具体 SQL

### 3. 执行计划评估
- 生成每个场景的执行计划（`EXPLAIN` JSON 格式）
- LLM 判定执行计划的效率
- 评估添加索引的可行性并给出建议

## 使用方式

### 方式一：Web 界面

访问：`http://localhost:8080/sql-agent-analysis.html`

#### 单条 SQL 分析
1. 在"单条 SQL 分析"标签页中输入 SQL 语句
2. 选择数据源
3. 选择 LLM 模型
4. 点击"开始分析"

示例 SQL：
```sql
SELECT * FROM users WHERE age > ? AND city = ?
```

#### Mapper XML 批量分析
1. 切换到"Mapper XML 批量分析"标签页
2. 粘贴 Mapper XML 文件内容
3. 输入 Namespace（例如：`com.example.mapper.UserMapper`）
4. 选择数据源和 LLM 模型
5. 点击"批量分析"

### 方式二：API 调用

#### 分析单条 SQL

**Endpoint:** `POST /api/sql-agent/analyze`

**请求体：**
```json
{
  "sql": "SELECT * FROM users WHERE age > ? AND city = ?",
  "datasourceName": "main_db",
  "llmName": "gpt-4"
}
```

**响应：**
```json
{
  "originalSql": "SELECT * FROM users WHERE age > ? AND city = ?",
  "distributionAnalysis": "数据分布分析结果...",
  "instantiatedSqls": [
    {
      "scenarioName": "最小值场景",
      "sql": "SELECT * FROM users WHERE age > 18 AND city = 'Beijing'",
      "parameters": {"age": 18, "city": "Beijing"}
    }
  ],
  "planEvaluations": [
    {
      "scenarioName": "最小值场景",
      "sql": "SELECT * FROM users WHERE age > 18 AND city = 'Beijing'",
      "executionPlan": {...},
      "evaluation": "LLM 评估结果..."
    }
  ],
  "finalReport": "完整的 Markdown 报告"
}
```

#### 分析 Mapper XML

**Endpoint:** `POST /api/sql-agent/analyze-mapper`

**请求体：**
```json
{
  "xmlContent": "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>...",
  "namespace": "com.example.mapper.UserMapper",
  "datasourceName": "main_db",
  "llmName": "gpt-4"
}
```

**响应：**
```json
{
  "mapperNamespace": "com.example.mapper.UserMapper",
  "results": [
    {
      "originalSql": "SELECT * FROM users WHERE id = ?",
      "distributionAnalysis": "...",
      "instantiatedSqls": [...],
      "planEvaluations": [...],
      "finalReport": "..."
    }
  ],
  "overallSummary": "共分析了 5 条 SQL 语句。"
}
```

## 工作流架构

### 核心组件

1. **SqlAgentWorkflowContext** - 工作流上下文
   - 存储 SQL、数据源、LLM 配置
   - 传递各步骤的中间结果

2. **SqlAgentChain** - 工作流链
   - 管理并按顺序执行步骤
   - 处理异常和日志

3. **SqlAgentStep** - 步骤接口
   - `DistributionAnalysisStep` - 数据分布分析
   - `SqlInstantiationStep` - SQL 实例化
   - `ExecutionPlanAnalysisStep` - 执行计划评估

### 工作流程图

```
输入 SQL
    ↓
[步骤 1: 数据分布分析]
    ├─ 获取表的统计信息和直方图
    ├─ 提取列的最小值、最大值、采样值
    └─ LLM 解读数据分布 → 评估 SQL 效率
    ↓
[步骤 2: SQL 实例化]
    ├─ 根据采样值生成多个场景
    └─ 将参数化 SQL 转换为具体可执行 SQL
    ↓
[步骤 3: 执行计划评估]
    ├─ 对每个场景生成执行计划
    ├─ LLM 分析执行计划效率
    └─ 给出索引优化建议
    ↓
生成最终报告
```

## 前置条件

### 1. 数据库准备

确保目标表已经生成了直方图统计信息：

```sql
-- 为表的所有列生成直方图（100 个桶）
ANALYZE TABLE users UPDATE HISTOGRAM ON age, city, create_time WITH 100 BUCKETS;

-- 查看统计信息
SELECT * FROM information_schema.COLUMN_STATISTICS 
WHERE SCHEMA_NAME = 'your_database' AND TABLE_NAME = 'users';
```

### 2. LLM 配置

在 `application.yml` 中配置 LLM：

```yaml
llm:
  configs:
    - name: gpt-4
      type: openai
      model: gpt-4-turbo
      api-key: your-api-key
      base-url: https://api.openai.com
      temperature: 0.7
```

### 3. Prompt 模板

系统需要两个 Prompt 模板：
- `sql_agent_distribution` - 数据分布分析模板
- `sql_agent_plan_evaluation` - 执行计划评估模板

这些模板可以通过 Prompt 管理页面配置。

## 最佳实践

### 1. 定期更新统计信息
```sql
-- 每周或数据发生大量变化后执行
ANALYZE TABLE your_table UPDATE HISTOGRAM ON column1, column2 WITH 100 BUCKETS;
```

### 2. 选择合适的 LLM 模型
- 对于复杂分析：使用 GPT-4 或 Claude-3
- 对于快速分析：使用 GPT-3.5 或本地模型

### 3. 批量分析优化
- 一次分析一个 Mapper 文件
- 对于大型项目，分模块进行分析
- 异步处理大量 SQL 的分析任务

## 常见问题

### Q1: 为什么没有分析结果？
A: 检查以下几点：
- 表是否有直方图统计信息
- LLM 是否配置正确
- 数据源连接是否正常

### Q2: 分析速度较慢？
A: 原因可能包括：
- LLM 响应时间较长
- 生成了多个场景的执行计划
- 建议使用本地或内网部署的 LLM

### Q3: 如何优化 Prompt？
A: 访问 Prompt 管理页面，调整以下内容：
- 增加具体的分析维度
- 优化输出格式
- 添加示例和上下文

## 扩展开发

### 添加自定义步骤

```java
public class CustomAnalysisStep implements SqlAgentStep {
    @Override
    public void execute(SqlAgentWorkflowContext context) {
        // 自定义分析逻辑
        // 可以访问 context 中的所有数据
    }

    @Override
    public String getName() {
        return "自定义分析步骤";
    }
}
```

### 在工作流中使用

```java
SqlAgentChain chain = new SqlAgentChain()
    .addStep(new DistributionAnalysisStep(...))
    .addStep(new SqlInstantiationStep(...))
    .addStep(new CustomAnalysisStep(...))
    .addStep(new ExecutionPlanAnalysisStep(...));

chain.execute(context);
```

## 相关文档

- [MyBatis 解析器使用指南](MYBATIS_PARSER_USAGE.md)
- [SQL 参数替换说明](SQL_PARAMETER_REPLACEMENT.md)
- [表分析功能使用指南](TABLE_ANALYSIS_USAGE.md)







