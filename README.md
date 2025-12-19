# SQL Performance Analyzer

SQL性能分析系统，通过REST API接收SQL语句，自动获取MySQL执行计划和表结构信息，调用DeepSeek API进行性能分析，并生成Markdown格式的分析报告。

## 功能特性

- 自动获取SQL执行计划（EXPLAIN FORMAT=JSON）
- 自动提取表结构、索引和统计信息
- 使用DeepSeek大模型进行性能分析
- 生成Markdown格式的详细分析报告
- **Prompt 模板动态配置**：支持通过前端页面动态配置和修改 Prompt 模板，无需重启应用即可生效
- **MCP协议支持**：实现MCP Server，允许大模型通过标准化协议调用项目功能
- **MyBatis XML解析**：解析MyBatis Mapper XML文件，提取所有可能的SQL查询（包括动态SQL）
- **单表综合分析**：针对单个表的所有查询进行综合分析，生成优化建议
- **自然语言分析**：使用自然语言描述需求，AI自动识别意图并调用相关工具进行分析

## 技术栈

- Spring Boot 3.5.4
- Java 17
- MySQL JDBC
- OkHttp (HTTP客户端)
- Jackson (JSON处理)

## 快速开始

### 启动后端服务

#### 方式一：使用 Docker Compose（推荐）

使用 Docker Compose 可以快速启动 MySQL 容器，包含预配置的数据库和示例数据。

#### 1. 启动 MySQL 容器

```bash
docker-compose up -d
```

这将启动一个 MySQL 8.0 容器，包含：
- 数据库：`test_db`
- 用户名：`root`，密码：`password`
- 示例表：`users`、`orders`、`order_items`（包含示例数据）
- 端口：`3306`

#### 2. 验证容器运行状态

```bash
docker-compose ps
```

#### 3. 查看日志

```bash
docker-compose logs -f mysql
```

#### 4. 停止容器

```bash
docker-compose down
```

如果需要删除数据卷（清空所有数据）：

```bash
docker-compose down -v
```

### 方式二：使用本地 MySQL

#### 1. 配置数据库

编辑 `src/main/resources/application.yml`，配置MySQL数据库连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/test_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: your_password
```

#### 2. 创建数据库和表

可以手动创建数据库，或使用 `docker/mysql/init/01-init-database.sql` 脚本初始化。

### 配置 DeepSeek API

在 `application.yml` 中配置DeepSeek API：

```yaml
spring:
  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY:your-deepseek-api-key-here}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
```

或者使用环境变量：

```bash
export DEEPSEEK_API_KEY=your-deepseek-api-key-here
```

DeepSeek API 兼容 OpenAI API，因此使用 Spring AI 的 OpenAI starter 即可。

### 运行应用

```bash
./gradlew bootRun
```

应用将在 `http://localhost:8080` 启动。

### 访问前端页面

前端页面已集成到 Spring Boot 项目中，位于 `src/main/resources/static/index.html`。

启动应用后，直接在浏览器中访问：

```
http://localhost:8080/
```

或

```
http://localhost:8080/index.html
```

#### 前端功能

- ✅ 输入 SQL 语句
- ✅ 调用后端 API 进行分析
- ✅ Markdown 格式报告展示
- ✅ 导出 PDF 功能
- ✅ **Prompt 模板管理**：动态配置和修改 Prompt 模板

前端页面会自动调用后端 API（`/api/sql/analyze`），无需额外配置。

#### Prompt 模板管理

访问 Prompt 模板管理页面：

```
http://localhost:8080/prompt-management.html
```

或者在主页面点击右上角的 "Prompt 模板管理" 链接。

**功能说明：**

1. **查看模板列表**：显示所有可用的 Prompt 模板（MySQL、GoldenDB 等）
2. **编辑模板**：点击模板卡片，在编辑器中修改模板内容
3. **保存更改**：修改后点击"保存更改"按钮，新模板立即生效
4. **模板变量**：模板支持以下变量，系统会自动替换：
   - `{sql}` - SQL 语句
   - `{execution_plan}` - 执行计划
   - `{table_structures}` - 表结构信息

**注意事项：**

- 模板修改后，新的 SQL 分析请求将使用更新后的模板
- 模板内容保存在 H2 数据库中，重启应用后仍然有效
- 系统首次启动时会自动初始化默认模板

## Docker Compose 说明

### docker-compose.yml 的作用

`docker-compose.yml` 是 Docker Compose 的配置文件，用于定义和运行多容器 Docker 应用。它允许你：

1. **定义服务**：指定需要运行的容器（如 MySQL）
2. **配置环境**：设置环境变量、端口映射、数据卷等
3. **管理依赖**：定义服务之间的依赖关系
4. **一键启动**：使用 `docker-compose up` 命令启动所有服务

### 本项目中的配置

- **MySQL 服务**：使用官方 MySQL 8.0 镜像
- **自动初始化**：容器启动时自动执行 `docker/mysql/init/` 目录下的 SQL 脚本
- **数据持久化**：使用 Docker 数据卷保存数据
- **健康检查**：自动检测 MySQL 是否就绪

### 示例数据

容器启动后会自动创建以下表并插入示例数据：

- **users 表**：5 个示例用户
- **orders 表**：5 个示例订单
- **order_items 表**：6 个订单项

你可以使用以下 SQL 进行测试：

```sql
-- 查询所有用户
SELECT * FROM users;

-- 查询用户的订单
SELECT u.username, o.order_number, o.total_amount 
FROM users u 
JOIN orders o ON u.id = o.user_id;

-- 查询订单详情
SELECT o.order_number, oi.product_name, oi.quantity, oi.subtotal
FROM orders o
JOIN order_items oi ON o.id = oi.order_id;
```

## API使用

### Prompt 模板管理 API

#### 获取所有模板

```http
GET /api/prompts
```

**响应：**
```json
[
  {
    "id": 1,
    "templateType": "MYSQL",
    "templateName": "MySQL性能分析专家",
    "templateContent": "...",
    "description": "系统默认初始化模板",
    "gmtCreated": "2025-12-02T10:00:00",
    "gmtModified": "2025-12-02T10:00:00"
  }
]
```

#### 获取指定模板

```http
GET /api/prompts/{type}
```

**示例：**
```http
GET /api/prompts/MYSQL
```

#### 更新模板

```http
PUT /api/prompts/{type}
Content-Type: application/json

{
  "content": "新的模板内容..."
}
```

**示例：**
```http
PUT /api/prompts/MYSQL
Content-Type: application/json

{
  "content": "你是一位资深的MySQL性能优化专家..."
}
```

### 分析SQL性能

**请求：**
```http
POST /api/sql/analyze
Content-Type: application/json

{
  "sql": "SELECT * FROM users WHERE id = 1"
}
```

**使用示例数据测试：**
```http
POST /api/sql/analyze
Content-Type: application/json

{
  "sql": "SELECT u.username, o.order_number, o.total_amount FROM users u JOIN orders o ON u.id = o.user_id WHERE u.status = 'active'"
}
```

**响应：**
```json
{
  "sql": "SELECT * FROM users WHERE id = 1",
  "executionPlan": {...},
  "tableStructures": [...],
  "analysisResult": "DeepSeek分析结果...",
  "markdownReport": "# SQL性能分析报告\n\n..."
}
```

### 上传MyBatis Mapper XML文件

**请求：**
```http
POST /api/mybatis/upload
Content-Type: application/json

{
  "xmlContent": "<mapper namespace=\"com.example.mapper.UserMapper\">...</mapper>",
  "mapperNamespace": "com.example.mapper.UserMapper"
}
```

### 分析指定表的所有查询

**请求：**
```http
GET /api/analysis/table/users?datasourceName=mysql-primary
```

### MCP协议调用

**请求：**
```http
POST /api/mcp/v1
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_table_structure",
    "arguments": {
      "tableName": "users"
    }
  },
  "id": "1"
}
```

## 项目结构

```
src/main/java/com/example/sqlanalyzer/
├── SqlAnalyzerApplication.java    # 主应用类
├── config/                         # 配置类
│   ├── DatabaseConfig.java
│   └── DeepSeekConfig.java
├── controller/                     # 控制器
│   └── SqlAnalysisController.java
├── model/                          # 模型类
│   ├── SqlAnalysisRequest.java
│   ├── SqlAnalysisResponse.java
│   ├── ExecutionPlan.java
│   └── TableStructure.java
└── service/                        # 服务层
    ├── SqlExecutionPlanService.java
    ├── DeepSeekApiClient.java
    ├── SqlPerformanceAnalysisService.java
    └── ReportGenerator.java
```

## 新功能文档

- [MCP协议使用指南](docs/MCP_USAGE.md) - MCP Server使用说明
- [MyBatis解析器使用指南](docs/MYBATIS_PARSER_USAGE.md) - MyBatis XML解析功能说明
- [单表分析使用指南](docs/TABLE_ANALYSIS_USAGE.md) - 单表慢SQL综合分析功能说明

## 配置说明

详细配置项请参考 `src/main/resources/application.yml`。

### 环境变量配置

可以通过环境变量设置 DeepSeek API Key：

```bash
export DEEPSEEK_API_KEY=your-deepseek-api-key-here
```

或者在 `application.yml` 中直接配置：

```yaml
spring:
  ai:
    openai:
      api-key: your-deepseek-api-key-here
```

## 许可证

MIT License
