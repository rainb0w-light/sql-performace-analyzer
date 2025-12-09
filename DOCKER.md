# Docker 使用指南

## 什么是 docker-compose.yml？

`docker-compose.yml` 是 Docker Compose 的配置文件，用于定义和运行多容器 Docker 应用。它允许你：

- **定义服务**：指定需要运行的容器（如 MySQL、Redis 等）
- **配置环境**：设置环境变量、端口映射、数据卷等
- **管理依赖**：定义服务之间的依赖关系
- **一键启动**：使用 `docker-compose up` 命令启动所有服务

## 快速开始

### 1. 启动 MySQL 容器

```bash
docker-compose up -d
```

`-d` 参数表示在后台运行（detached mode）。

### 2. 查看容器状态

```bash
docker-compose ps
```

### 3. 查看日志

```bash
# 查看所有日志
docker-compose logs

# 实时查看 MySQL 日志
docker-compose logs -f mysql

# 查看最近 100 行日志
docker-compose logs --tail=100 mysql
```

### 4. 停止容器

```bash
# 停止容器（保留数据）
docker-compose stop

# 停止并删除容器（保留数据卷）
docker-compose down

# 停止并删除容器和数据卷（清空所有数据）
docker-compose down -v
```

### 5. 重启容器

```bash
docker-compose restart
```

## 数据库连接信息

容器启动后，你可以使用以下信息连接数据库：

- **主机**：`localhost`
- **端口**：`3306`
- **数据库**：`test_db`
- **用户名**：`root`
- **密码**：`password`

或者使用创建的用户：

- **用户名**：`testuser`
- **密码**：`testpass`

## 示例数据

容器启动时会自动执行 `docker/mysql/init/01-init-database.sql` 脚本，创建以下表：

### users 表
包含 5 个示例用户，字段包括：
- id, username, email, password_hash, full_name, age, status, created_at, updated_at

### orders 表
包含 5 个示例订单，字段包括：
- id, user_id, order_number, total_amount, status, shipping_address, created_at, updated_at

### order_items 表
包含 6 个订单项，字段包括：
- id, order_id, product_name, quantity, unit_price, subtotal, created_at

## 测试 SQL 示例

### 1. 查询所有用户
```sql
SELECT * FROM users;
```

### 2. 查询活跃用户
```sql
SELECT * FROM users WHERE status = 'active';
```

### 3. 查询用户及其订单
```sql
SELECT u.username, u.email, o.order_number, o.total_amount, o.status
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.status = 'active';
```

### 4. 查询订单详情
```sql
SELECT o.order_number, oi.product_name, oi.quantity, oi.unit_price, oi.subtotal
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
ORDER BY o.order_number, oi.id;
```

### 5. 统计每个用户的订单数量和总金额
```sql
SELECT u.username, 
       COUNT(o.id) as order_count,
       COALESCE(SUM(o.total_amount), 0) as total_spent
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
GROUP BY u.id, u.username
ORDER BY total_spent DESC;
```

## 使用 API 测试

启动应用后，可以使用以下 SQL 进行性能分析：

```bash
curl -X POST http://localhost:8080/api/sql/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT * FROM users WHERE status = '\''active'\''"
  }'
```

```bash
curl -X POST http://localhost:8080/api/sql/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT u.username, o.order_number, o.total_amount FROM users u JOIN orders o ON u.id = o.user_id WHERE u.status = '\''active'\''"
  }'
```

## 常见问题

### 1. 端口 3306 已被占用

如果本地已有 MySQL 运行在 3306 端口，可以修改 `docker-compose.yml` 中的端口映射：

```yaml
ports:
  - "3307:3306"  # 将本地端口改为 3307
```

然后更新 `application.properties`：

```properties
spring.datasource.url=jdbc:mysql://localhost:3307/test_db?...
```

### 2. 容器无法启动

检查日志：
```bash
docker-compose logs mysql
```

常见原因：
- 端口被占用
- 磁盘空间不足
- 权限问题

### 3. 数据丢失

如果使用 `docker-compose down -v`，数据卷会被删除，所有数据将丢失。

要保留数据，使用：
```bash
docker-compose down  # 不删除数据卷
```

### 4. 重新初始化数据库

如果需要重新初始化数据库：

```bash
# 停止并删除容器和数据卷
docker-compose down -v

# 重新启动
docker-compose up -d
```

## 进入容器

如果需要进入 MySQL 容器执行命令：

```bash
# 进入容器
docker exec -it sql-analyzer-mysql bash

# 连接 MySQL
mysql -u root -ppassword test_db

# 或者直接执行 SQL
docker exec -it sql-analyzer-mysql mysql -u root -ppassword test_db -e "SELECT * FROM users;"
```

## 备份和恢复

### 备份数据库

```bash
docker exec sql-analyzer-mysql mysqldump -u root -ppassword test_db > backup.sql
```

### 恢复数据库

```bash
docker exec -i sql-analyzer-mysql mysql -u root -ppassword test_db < backup.sql
```

