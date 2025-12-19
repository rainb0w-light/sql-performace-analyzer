# 单表慢SQL综合分析使用指南

## 概述

单表分析功能可以针对指定表的所有SQL查询进行综合分析，生成优化建议。

## API端点

### 分析指定表的所有查询

**端点：** `GET /api/analysis/table/{tableName}`

**查询参数：**
- `datasourceName` (可选): 数据源名称

**示例：**
```
GET /api/analysis/table/users?datasourceName=mysql-primary
```

**响应：**
```json
{
  "success": true,
  "message": "分析完成",
  "tableName": "users",
  "datasourceName": "mysql-primary",
  "queryCount": 5,
  "tableStructure": {
    "tableName": "users",
    "columns": [...],
    "indexes": [...],
    "statistics": {...}
  },
  "queryAnalyses": [
    {
      "queryId": 1,
      "mapperNamespace": "com.example.mapper.UserMapper",
      "statementId": "selectById",
      "queryType": "select",
      "sql": "SELECT * FROM users WHERE id = ?",
      "usesIndex": true,
      "indexName": "PRIMARY",
      "accessType": "const",
      "rowsExamined": 1,
      "slowQuery": false
    }
  ],
  "suggestions": {
    "totalQueries": 5,
    "slowQueries": 1,
    "queriesWithoutIndex": 1,
    "indexSuggestions": [
      "建议为列 'email' 创建索引（在 3 个查询中使用）"
    ],
    "sqlSuggestions": [
      "查询 #2 (selectByEmail) 可能存在性能问题：未使用索引, 全表扫描"
    ]
  }
}
```

## 分析内容

### 1. 表结构信息
- 列信息（名称、类型、约束等）
- 索引信息
- 表统计信息

### 2. 查询分析
对每个SQL查询进行分析：
- 执行计划
- 是否使用索引
- 访问类型（const, ref, range, all等）
- 扫描行数
- 是否为慢查询

### 3. 优化建议

#### 索引建议
- 分析WHERE条件中常用的列
- 检查现有索引
- 建议为常用列创建索引

#### SQL优化建议
- 识别慢查询
- 指出性能问题（未使用索引、全表扫描、扫描行数过多等）

## 使用流程

1. **上传MyBatis Mapper XML文件**
   ```
   POST /api/mybatis/upload
   ```

2. **分析指定表**
   ```
   GET /api/analysis/table/{tableName}
   ```

3. **查看优化建议**
   根据返回的`suggestions`字段，获取索引建议和SQL优化建议

## 慢查询判断标准

以下情况会被标记为慢查询：
- 扫描行数 > 10000
- 访问类型为 `ALL`（全表扫描）
- 未使用索引

