# MySQL 初始化脚本说明

此目录包含 MySQL 容器的初始化 SQL 脚本。

## 文件说明

- `01-init-database.sql`: 创建数据库、表结构和示例数据

## 表结构

### users 表
- 用户基本信息表
- 包含索引：username, email, status, created_at

### orders 表
- 订单表
- 外键关联 users 表
- 包含索引：user_id, order_number, status, created_at

### order_items 表
- 订单项表
- 外键关联 orders 表
- 包含索引：order_id

## 使用说明

这些脚本会在 MySQL 容器首次启动时自动执行。如果需要重新初始化：

1. 停止并删除容器：`docker-compose down -v`
2. 重新启动：`docker-compose up -d`

注意：`-v` 参数会删除数据卷，所有数据将被清空。

