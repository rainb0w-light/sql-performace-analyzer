-- Mapper参数初始化SQL
-- 基于 UserMapper.xml 生成
-- mapperId格式: namespace.statementId (例如: com.example.mapper.UserMapper.selectById)

-- 1. selectById - 根据ID查询用户
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper.UserMapper.selectById', '{"id": 1}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 2. selectByCondition - 根据多个条件查询用户（动态WHERE）
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper.UserMapper.selectByCondition', '{"id": 1, "username": "test", "email": "test@example.com", "status": "active", "createTime": "2024-01-01T00:00:00"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 3. selectByUsernameOrEmail - 根据用户名或邮箱查询（choose）
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper.UserMapper.selectByUsernameOrEmail', '{"username": "test"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 4. selectByIds - 根据ID列表查询用户（foreach）
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper.UserMapper.selectByIds', '{"ids": [1, 2, 3, 4, 5]}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 5. selectByPage - 分页查询用户
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper.UserMapper.selectByPage', '{"status": "active", "keyword": "test", "orderBy": "createTime", "offset": 0, "limit": 10}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 6. selectUserWithOrders - 关联查询：用户和订单
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper.UserMapper.selectUserWithOrders', '{"userId": 1, "status": "active"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 7. countByCondition - 统计查询
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper.UserMapper.countByCondition', '{"status": "active", "createTimeStart": "2024-01-01T00:00:00", "createTimeEnd": "2024-12-31T23:59:59"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 8. selectUserOrderStats - 复杂查询：用户订单统计
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper.UserMapper.selectUserOrderStats', '{"userId": 1, "status": "active", "minOrderAmount": 100.00}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 9. selectWithTrim - 使用trim标签的动态查询
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper.UserMapper.selectWithTrim', '{"id": 1, "username": "test", "email": "test@example.com"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 10. selectUserWithOrderDetails - 嵌套查询：查询用户及其订单详情
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper.UserMapper.selectUserWithOrderDetails', '{"userId": 1, "orderStatus": "completed"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 可选：为整个 UserMapper 配置通用参数（层级配置示例）
-- 这个配置会被所有 UserMapper 下的方法使用（如果没有更具体的配置）
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper.UserMapper', '{"status": "active"}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 可选：为整个 mapper 包配置通用参数（更高级的层级配置）
INSERT INTO mapper_parameter (mapper_id, parameter_json, created_at, updated_at) VALUES
('com.example.mapper', '{"tenantId": 100}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
