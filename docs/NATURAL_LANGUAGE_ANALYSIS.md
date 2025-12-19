# 自然语言SQL分析功能说明

## 概述

自然语言分析功能允许用户使用自然语言描述需求，系统会自动识别用户意图，调用相关工具执行分析，并生成专业的分析报告。

## 功能特性

1. **意图识别**：使用AI大模型识别用户的自然语言需求
2. **工具自动调用**：根据意图自动规划并执行相关工具调用
3. **代码扫描**：自动扫描项目目录，查找MyBatis Mapper XML文件
4. **智能分析**：综合分析结果，生成专业的优化建议报告

## 可用工具

系统支持以下工具，AI会根据用户需求自动选择和调用：

1. **scan_mapper_files** - 扫描代码目录，查找MyBatis Mapper XML文件
2. **parse_mapper_file** - 解析MyBatis Mapper XML文件
3. **get_table_structure** - 获取表结构信息
4. **get_table_queries** - 获取表相关的所有SQL查询
5. **analyze_table** - 综合分析表的所有查询
6. **analyze_sql** - 分析单个SQL的性能

## 使用示例

### 示例1：分析表的所有SQL查询

**用户输入：**
```
分析users表的所有SQL查询性能
```

**系统执行：**
1. 识别意图：分析表的所有SQL查询
2. 调用工具：
   - `get_table_queries` - 获取users表的所有SQL查询
   - `analyze_table` - 综合分析users表的所有查询
3. 生成报告：包含性能分析、优化建议等

### 示例2：扫描Mapper文件

**用户输入：**
```
扫描项目中的MyBatis Mapper文件
```

**系统执行：**
1. 识别意图：扫描Mapper文件
2. 调用工具：
   - `scan_mapper_files` - 扫描代码目录
3. 返回结果：列出所有找到的Mapper文件

### 示例3：综合分析

**用户输入：**
```
分析orders表的查询并给出优化建议
```

**系统执行：**
1. 识别意图：分析表查询并优化
2. 调用工具：
   - `get_table_queries` - 获取orders表的所有查询
   - `get_table_structure` - 获取表结构
   - `analyze_table` - 综合分析
3. 生成报告：包含详细的优化建议

## API端点

### 自然语言分析

**端点：** `POST /api/nl/analyze`

**请求体：**
```json
{
  "request": "分析users表的所有SQL查询性能",
  "datasourceName": "mysql-primary",
  "llmName": "deepseek1"
}
```

**响应：**
```json
{
  "success": true,
  "userRequest": "分析users表的所有SQL查询性能",
  "intent": "分析表的所有SQL查询",
  "toolCalls": [
    {
      "tool": "get_table_queries",
      "params": {
        "tableName": "users"
      }
    },
    {
      "tool": "analyze_table",
      "params": {
        "tableName": "users"
      }
    }
  ],
  "toolResults": [
    {
      "tool": "get_table_queries",
      "success": true,
      "result": {
        "queries": [...],
        "count": 5
      }
    },
    {
      "tool": "analyze_table",
      "success": true,
      "result": {
        "analysisResult": {...}
      }
    }
  ],
  "analysisResult": "基于分析结果生成的详细报告..."
}
```

## 工作流程

1. **用户输入自然语言需求**
2. **意图识别**：AI分析用户需求，识别意图
3. **工具规划**：AI规划需要调用的工具序列
4. **工具执行**：按顺序执行工具调用
5. **结果汇总**：汇总所有工具执行结果
6. **生成报告**：AI基于结果生成最终分析报告

## 代码扫描功能

### 扫描路径

系统会自动扫描以下常见路径：
- `src/main/resources`
- `src/main/java`
- `mapper`
- `mappers`

### 文件识别

扫描以下类型的文件：
- `*Mapper.xml`
- `*mapper.xml`

### 示例结果

```json
{
  "files": [
    {
      "path": "src/main/resources/mapper/UserMapper.xml",
      "name": "UserMapper.xml",
      "size": "12345"
    }
  ],
  "count": 1
}
```

## 意图识别策略

### AI识别（主要方式）

使用大模型进行意图识别，支持复杂的自然语言表达。

### 关键词匹配（备用方式）

如果AI识别失败，使用关键词匹配：
- 包含"分析"和"表" → 分析表查询
- 包含"扫描"或"mapper" → 扫描Mapper文件
- 包含"sql"或"查询" → 分析SQL性能

## 注意事项

1. **AI服务依赖**：需要配置AI服务才能使用自然语言分析功能
2. **文件扫描**：代码扫描功能需要文件系统访问权限
3. **性能考虑**：扫描大量文件可能需要较长时间
4. **错误处理**：如果某个工具调用失败，系统会继续执行其他工具

## 前端页面

访问 `http://localhost:8080/natural-language-analysis.html` 使用自然语言分析功能。

页面特性：
- 自然语言输入框
- 示例需求快速选择
- 实时显示分析步骤
- 详细的工具调用和执行结果展示
- 最终分析报告展示



