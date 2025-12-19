# MyBatis Mapper XML 演示文件

## 文件说明

`UserMapper.xml` 是一个完整的MyBatis Mapper XML演示文件，包含了各种常见的SQL操作和动态SQL场景。

## 包含的功能

### 1. 基础查询
- `selectById` - 根据ID查询
- `selectByCondition` - 多条件动态查询（使用`<where>`和`<if>`）
- `selectByUsernameOrEmail` - 条件选择查询（使用`<choose>`、`<when>`、`<otherwise>`）

### 2. 批量操作
- `selectByIds` - 根据ID列表查询（使用`<foreach>`）
- `insertBatch` - 批量插入（使用`<foreach>`）
- `deleteByIds` - 批量删除（使用`<foreach>`）

### 3. 分页查询
- `selectByPage` - 分页查询，包含动态排序

### 4. 关联查询
- `selectUserWithOrders` - 用户和订单关联查询
- `selectUserOrderStats` - 用户订单统计查询
- `selectUserWithOrderDetails` - 用户、订单、订单项三级关联查询

### 5. 动态更新
- `update` - 动态更新（使用`<set>`和`<if>`）
- `updateStatus` - 批量更新状态

### 6. 高级动态SQL
- `selectWithTrim` - 使用`<trim>`标签的动态查询

## 涉及的表

- `users` - 用户表
- `orders` - 订单表
- `order_items` - 订单项表

## 使用方法

### 1. 在表分析页面使用

1. 访问 `http://localhost:8080/table-analysis.html`
2. 切换到"上传MyBatis XML"标签页
3. 复制 `UserMapper.xml` 的内容
4. 在"Mapper命名空间"输入框中输入：`com.example.mapper.UserMapper`
5. 将XML内容粘贴到"XML内容"文本框中
6. 点击"上传并解析"

### 2. 测试表分析功能

上传完成后，可以分析以下表：
- `users` - 会解析出所有涉及users表的查询
- `orders` - 会解析出所有涉及orders表的查询
- `order_items` - 会解析出所有涉及order_items表的查询

### 3. 解析出的SQL示例

上传后，解析器会生成以下类型的SQL：

**简单查询：**
```sql
SELECT * FROM users WHERE id = ?
```

**动态WHERE查询（多个变体）：**
```sql
-- 当id不为null时
SELECT * FROM users WHERE id = ? ORDER BY id DESC

-- 当username不为null时
SELECT * FROM users WHERE username = ? ORDER BY id DESC

-- 当id和email都不为null时
SELECT * FROM users WHERE id = ? AND email = ? ORDER BY id DESC
```

**foreach查询：**
```sql
SELECT * FROM users WHERE id IN (?, ?, ?)
```

**关联查询：**
```sql
SELECT u.id, u.username, u.email, u.status,
       o.id as order_id, o.order_number, o.total_amount
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.id = ?
ORDER BY o.create_time DESC
```

## 注意事项

1. 这个文件仅用于演示，实际使用时需要根据你的数据库表结构调整
2. ResultMap中引用的实体类（如`com.example.model.User`）需要在实际项目中存在
3. 某些查询可能需要对应的数据库表存在才能正常执行
4. 解析器会为每个动态SQL条件组合生成独立的SQL查询

## 测试建议

1. **先上传XML文件**，查看解析出的SQL数量
2. **分析users表**，查看所有查询的执行计划和优化建议
3. **检查慢查询**，查看哪些查询可能存在性能问题
4. **查看优化建议**，了解是否需要添加索引或优化SQL



