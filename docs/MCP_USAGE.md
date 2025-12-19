# MCP协议使用指南

## 概述

本项目已实现MCP（Model Context Protocol）服务器，允许大模型通过标准化的MCP协议调用项目功能。

## MCP端点

### 端点地址
```
POST /api/mcp/v1
```

### 协议规范
基于JSON-RPC 2.0和MCP规范（协议版本：2024-11-05）

## 可用工具（Tools）

### 1. get_table_structure
获取指定表的结构信息

**参数：**
- `tableName` (string, 必需): 表名
- `datasourceName` (string, 可选): 数据源名称

**示例请求：**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_table_structure",
    "arguments": {
      "tableName": "users",
      "datasourceName": "mysql-primary"
    }
  },
  "id": "1"
}
```

### 2. get_execution_plan
获取SQL语句的执行计划

**参数：**
- `sql` (string, 必需): SQL语句
- `datasourceName` (string, 可选): 数据源名称

**示例请求：**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_execution_plan",
    "arguments": {
      "sql": "SELECT * FROM users WHERE id = 1",
      "datasourceName": "mysql-primary"
    }
  },
  "id": "2"
}
```

### 3. get_table_indexes
获取指定表的所有索引信息

**参数：**
- `tableName` (string, 必需): 表名
- `datasourceName` (string, 可选): 数据源名称

### 4. get_table_queries
获取指定表相关的所有SQL查询（从MyBatis Mapper XML解析结果中）

**参数：**
- `tableName` (string, 必需): 表名

## 初始化流程

### 1. 初始化连接
```json
{
  "jsonrpc": "2.0",
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {
      "name": "MCP Client",
      "version": "1.0.0"
    }
  },
  "id": "init-1"
}
```

### 2. 获取工具列表
```json
{
  "jsonrpc": "2.0",
  "method": "tools/list",
  "id": "tools-1"
}
```

## 响应格式

### 成功响应
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "..."
      }
    ],
    "isError": false
  },
  "id": "1"
}
```

### 错误响应
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32603,
    "message": "Internal error: ..."
  },
  "id": "1"
}
```

