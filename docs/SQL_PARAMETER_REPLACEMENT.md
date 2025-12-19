# SQL参数替换功能说明

## 概述

SQL参数替换功能能够自动将MyBatis SQL中的占位符（如`#{id}`）替换为数据库中的实际样本值，使得SQL可以直接执行以获取执行计划。

## 功能特性

1. **自动识别占位符**：识别MyBatis格式的占位符（`#{paramName}`和`${paramName}`）
2. **智能样本数据获取**：从数据库中查询样本数据，自动匹配参数名和列名
3. **类型感知替换**：根据列的数据类型正确格式化值（字符串加引号，数字不加引号）
4. **多表支持**：自动识别SQL中涉及的表，使用主表获取样本数据

## 工作原理

### 1. 占位符识别

系统使用正则表达式识别SQL中的占位符：
- `#{id}` → 参数名：`id`
- `${username}` → 参数名：`username`

### 2. 样本数据获取

对于SQL `SELECT * FROM users WHERE id = #{id}`：

1. 提取表名：`users`
2. 查询样本数据：`SELECT * FROM users LIMIT 1`
3. 获取样本行数据，例如：`{id: 1, username: 'alice', email: 'alice@example.com', ...}`

### 3. 参数匹配

匹配规则（按优先级）：
1. 直接匹配：`#{id}` → 查找样本数据中的`id`列
2. 列名匹配：如果参数名包含表别名（如`user.id`），提取列名`id`进行匹配
3. 类型推断：如果找不到匹配值，根据列的数据类型生成默认值

### 4. 值格式化

根据数据类型格式化值：

| 数据类型 | 格式化规则 | 示例 |
|---------|-----------|------|
| 整数类型（INT, BIGINT等） | 直接输出数字 | `1` |
| 浮点类型（DECIMAL, FLOAT等） | 直接输出数字 | `1.0` |
| 字符串类型（VARCHAR, TEXT等） | 加单引号并转义 | `'alice'` |
| 日期时间类型 | 使用SQL函数 | `CURRENT_TIMESTAMP` |
| 布尔类型 | 转换为0/1 | `1` |

### 5. SQL替换

替换示例：

**原始SQL：**
```sql
SELECT * FROM users WHERE id = #{id} AND username = #{username}
```

**替换后：**
```sql
SELECT * FROM users WHERE id = 1 AND username = 'alice'
```

## 使用场景

### 1. 表分析功能

在单表分析功能中，系统会自动替换SQL参数：

```java
// 原始SQL（从MyBatis XML解析）
String sql = "SELECT * FROM users WHERE id = #{id}";

// 自动替换为可执行SQL
String executableSql = "SELECT * FROM users WHERE id = 1";

// 获取执行计划
ExecutionPlan plan = sqlExecutionPlanService.getExecutionPlan(executableSql, datasourceName);
```

### 2. MCP协议调用

通过MCP协议调用`get_execution_plan`工具时，可以自动替换参数：

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_execution_plan",
    "arguments": {
      "sql": "SELECT * FROM users WHERE id = #{id}",
      "datasourceName": "mysql-primary",
      "replaceParameters": true
    }
  },
  "id": "1"
}
```

## API使用

### SqlParameterReplacerService

#### replaceParametersSmart

智能替换SQL中的占位符，自动识别表名：

```java
@Autowired
private SqlParameterReplacerService sqlParameterReplacerService;

String sql = "SELECT * FROM users WHERE id = #{id}";
String executableSql = sqlParameterReplacerService.replaceParametersSmart(sql, datasourceName);
// 结果: "SELECT * FROM users WHERE id = 1"
```

#### replaceParameters

指定表名进行替换：

```java
String sql = "SELECT * FROM users WHERE id = #{id}";
String executableSql = sqlParameterReplacerService.replaceParameters(sql, "users", datasourceName);
```

#### extractParameterNames

提取SQL中的所有参数名：

```java
Set<String> paramNames = sqlParameterReplacerService.extractParameterNames(sql);
// 结果: ["id", "username"]
```

## 注意事项

### 1. 表必须有数据

如果表为空或无法获取样本数据，系统会：
- 根据列的数据类型生成默认值
- 记录警告日志
- 继续执行（可能影响执行计划的准确性）

### 2. 参数名匹配

参数名匹配是大小写不敏感的：
- `#{id}` 可以匹配列 `ID`、`id`、`Id`
- `#{userName}` 可以匹配列 `username`、`user_name`（如果存在）

### 3. 复杂SQL处理

对于复杂的SQL（如包含子查询、UNION等），系统会：
- 使用第一个识别到的表作为主表
- 对于关联查询，使用主表获取样本数据

### 4. 性能考虑

- 每次替换都会查询数据库获取样本数据
- 样本数据查询使用`LIMIT 1`，性能影响较小
- 建议在生产环境中缓存样本数据

## 示例

### 示例1：简单查询

**输入：**
```sql
SELECT * FROM users WHERE id = #{id}
```

**样本数据：** `{id: 1, username: 'alice', ...}`

**输出：**
```sql
SELECT * FROM users WHERE id = 1
```

### 示例2：多条件查询

**输入：**
```sql
SELECT * FROM users 
WHERE id = #{id} 
  AND username = #{username} 
  AND status = #{status}
```

**样本数据：** `{id: 1, username: 'alice', status: 'active', ...}`

**输出：**
```sql
SELECT * FROM users 
WHERE id = 1 
  AND username = 'alice' 
  AND status = 'active'
```

### 示例3：关联查询

**输入：**
```sql
SELECT u.*, o.order_number 
FROM users u 
JOIN orders o ON u.id = o.user_id 
WHERE u.id = #{userId}
```

**样本数据（users表）：** `{id: 1, username: 'alice', ...}`

**输出：**
```sql
SELECT u.*, o.order_number 
FROM users u 
JOIN orders o ON u.id = o.user_id 
WHERE u.id = 1
```

### 示例4：IN查询

**输入：**
```sql
SELECT * FROM users WHERE id IN (#{id1}, #{id2}, #{id3})
```

**样本数据：** `{id: 1, ...}`

**输出：**
```sql
SELECT * FROM users WHERE id IN (1, 1, 1)
```

注意：对于IN查询，所有参数都会被替换为相同的样本值。这是预期的行为，因为目的是获取执行计划，而不是执行实际查询。

## 前端展示

在表分析页面中，会同时显示：

1. **原始SQL**：从MyBatis XML解析出的SQL（包含占位符）
2. **可执行SQL**：替换参数后的SQL（如果与原始SQL不同）

这样可以清楚地看到参数替换的过程和结果。



