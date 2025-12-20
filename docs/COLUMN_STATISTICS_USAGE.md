# 数据分布采样与SQL分析使用指南

## 概述

本功能实现了基于数据分布的SQL性能分析系统，通过收集数据库列的统计信息（直方图数据），针对性地生成不同场景的SQL查询，对比执行计划，并给出索引优化建议。

## 核心功能

1. **数据分布收集**：使用 `ANALYZE TABLE` 收集列的直方图统计信息
2. **数据分布解析**：解析 `information_schema.column_statistics` 中的JSON数据
3. **结构化存储**：将数据分布信息存储到H2数据库中
4. **SQL场景生成**：基于数据分布生成多个不同场景的SQL（最小值、最大值、中位数、分位数等）
5. **执行计划对比**：对比不同场景下的执行计划表现
6. **索引建议**：根据执行计划分析结果，自动生成索引优化建议

## API接口

### 1. 收集表统计信息

**接口**：`POST /api/statistics/collect`

**请求体**：
```json
{
  "tableName": "user",
  "datasourceName": "mysql-primary",
  "columns": ["id", "name", "email"],
  "bucketCount": 100
}
```

**参数说明**：
- `tableName`（必需）：要收集统计信息的表名
- `datasourceName`（可选）：数据源名称，如果不指定则使用默认数据源
- `columns`（可选）：要收集的列名列表，如果不指定则收集所有列
- `bucketCount`（可选）：直方图桶数量，默认100

**响应示例**：
```json
{
  "success": true,
  "tableName": "user",
  "datasourceName": "mysql-primary",
  "count": 3,
  "statistics": [
    {
      "id": 1,
      "datasourceName": "mysql-primary",
      "databaseName": "test_db",
      "tableName": "user",
      "columnName": "id",
      "histogramType": "singleton",
      "bucketCount": 100,
      "minValue": "1",
      "maxValue": "10000",
      "distinctCount": 10000,
      ...
    }
  ]
}
```

### 2. 批量收集多个表的统计信息

**接口**：`POST /api/statistics/collect/batch`

**请求体**：
```json
{
  "tableNames": ["user", "order", "product"],
  "datasourceName": "mysql-primary",
  "bucketCount": 100
}
```

### 3. 分析SQL并对比执行计划

**接口**：`POST /api/statistics/analyze`

**请求体**：
```json
{
  "sql": "SELECT * FROM user WHERE id = #{id} AND status = #{status}",
  "datasourceName": "mysql-primary"
}
```

**响应示例**：
```json
{
  "success": true,
  "comparison": {
    "originalSql": "SELECT * FROM user WHERE id = #{id} AND status = #{status}",
    "datasourceName": "mysql-primary",
    "comparisons": [
      {
        "scenario": "最小值场景",
        "filledSql": "SELECT * FROM user WHERE id = 1 AND status = 'active'",
        "sampleValues": {
          "id": "1",
          "status": "active"
        },
        "queryCost": 0.5,
        "rowsExamined": 1,
        "usesIndex": true,
        "indexName": "PRIMARY",
        "accessType": "const"
      },
      {
        "scenario": "最大值场景",
        "filledSql": "SELECT * FROM user WHERE id = 10000 AND status = 'inactive'",
        "queryCost": 0.5,
        "rowsExamined": 1,
        "usesIndex": true,
        "indexName": "PRIMARY",
        "accessType": "const"
      },
      ...
    ],
    "bestPlan": {
      "scenario": "最小值场景",
      "queryCost": 0.5,
      ...
    },
    "worstPlan": {
      "scenario": "随机采样场景4",
      "queryCost": 150.2,
      "rowsExamined": 5000,
      "usesIndex": false,
      "accessType": "ALL"
    },
    "needsIndexSuggestion": true,
    "indexSuggestions": [
      {
        "tableName": "user",
        "columns": ["status"],
        "indexType": "INDEX",
        "reason": "在10个测试场景中，有6个场景未使用索引（60.0%）。平均扫描行数：2500行，建议添加索引以提升查询性能。预期性能提升：75.0%。",
        "expectedImprovement": 75.0,
        "currentCost": 150.2,
        "expectedCost": 37.5
      }
    ]
  }
}
```

## 使用流程

### 步骤1：收集数据分布

首先需要收集目标表的列统计信息：

```bash
curl -X POST http://localhost:8080/api/statistics/collect \
  -H "Content-Type: application/json" \
  -d '{
    "tableName": "user",
    "datasourceName": "mysql-primary",
    "bucketCount": 100
  }'
```

### 步骤2：分析MyBatis SQL

收集完统计信息后，可以分析MyBatis Mapper中的SQL：

```bash
curl -X POST http://localhost:8080/api/statistics/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT * FROM user WHERE id = #{id} AND create_time >= #{startTime}",
    "datasourceName": "mysql-primary"
  }'
```

系统会自动：
1. 识别SQL中的占位符参数
2. 从收集的统计信息中获取对应列的数据分布
3. 生成多个场景的SQL（最小值、最大值、中位数、25/50/75分位数、随机采样等）
4. 为每个场景获取执行计划
5. 对比分析执行计划，找出性能问题
6. 生成索引优化建议

## 生成的SQL场景

系统会自动生成以下场景的SQL：

1. **最小值场景**：使用列的最小值
2. **最大值场景**：使用列的最大值
3. **中位数场景**：使用列的中位数值
4. **25分位数场景**：使用25分位数值
5. **50分位数场景**：使用50分位数值
6. **75分位数场景**：使用75分位数值
7. **随机采样场景1-4**：使用不同的随机样本值

这样可以全面测试SQL在不同数据分布下的性能表现。

## 索引建议规则

系统会根据以下规则生成索引建议：

1. **未使用索引比例**：如果超过50%的场景未使用索引，建议添加索引
2. **扫描行数过多**：如果平均扫描行数超过1000行，建议添加索引
3. **成本差异大**：如果最差场景比最好场景慢10倍以上，建议添加索引

索引建议会包含：
- 建议的索引列（基于WHERE条件中出现频率）
- 预期性能提升百分比
- 当前成本和预期成本对比
- 详细的建议原因

## 数据存储

所有收集的统计信息都存储在H2数据库中，表名为 `column_statistics`。包括：

- 直方图类型和桶数量
- 最小值和最大值
- 不同值数量（基数）
- 完整的直方图JSON数据
- 采样值列表

这些数据可以重复使用，无需每次都重新收集。

## 注意事项

1. **MySQL版本要求**：`ANALYZE TABLE ... UPDATE HISTOGRAM` 需要 MySQL 8.0.2+ 版本
2. **权限要求**：需要 `SELECT` 和 `ANALYZE` 权限
3. **性能影响**：收集统计信息可能会对数据库产生一定负载，建议在低峰期执行
4. **数据更新**：如果表数据发生变化，建议重新收集统计信息

## 与MyBatis集成

当分析MyBatis Mapper中的SQL时，系统会：

1. 解析SQL中的占位符（`#{paramName}` 或 `${paramName}`）
2. 识别占位符对应的列名
3. 从统计信息中获取该列的数据分布
4. 根据SQL上下文（如BETWEEN、>=、<=等）智能选择合适的值
5. 生成多个场景的SQL进行测试

这样可以避免因为使用不合适的参数值导致执行计划失真。

## 示例

### 完整示例：分析用户查询SQL

```bash
# 1. 收集user表的统计信息
curl -X POST http://localhost:8080/api/statistics/collect \
  -H "Content-Type: application/json" \
  -d '{
    "tableName": "user",
    "datasourceName": "mysql-primary"
  }'

# 2. 分析查询SQL
curl -X POST http://localhost:8080/api/statistics/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT * FROM user WHERE id = #{id} AND status = #{status} AND create_time BETWEEN #{startTime} AND #{endTime}",
    "datasourceName": "mysql-primary"
  }'
```

系统会生成多个场景，例如：
- 最小值场景：使用id=1, status='active', startTime和endTime为最早的时间范围
- 最大值场景：使用id=最大值, status='inactive', startTime和endTime为最晚的时间范围
- 中位数场景：使用中位数值
- 等等...

然后对比这些场景的执行计划，如果发现某些场景性能很差（如全表扫描），系统会建议添加相应的索引。
