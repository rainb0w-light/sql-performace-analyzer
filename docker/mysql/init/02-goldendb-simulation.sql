-- ============================================================
-- GoldenDB 模拟初始化脚本
-- 用于模拟 GoldenDB 分布式数据库的分片表结构和特性
-- ============================================================

USE test_db;

-- ============================================================
-- 1. 创建全局表（所有分片都有完整数据）
-- ============================================================

-- 全局机构表（金融机构场景）
CREATE TABLE IF NOT EXISTS global_org (
    org_id VARCHAR(20) PRIMARY KEY COMMENT '机构 ID',
    org_name VARCHAR(100) NOT NULL COMMENT '机构名称',
    org_type ENUM('HEADQUARTER', 'BRANCH', 'SUB_BRANCH') COMMENT '机构类型',
    parent_org_id VARCHAR(20) COMMENT '上级机构 ID',
    region_code VARCHAR(10) COMMENT '地区代码',
    status TINYINT DEFAULT 1 COMMENT '状态：1- active, 0-inactive',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局机构表';

-- 全局参数表
CREATE TABLE IF NOT EXISTS global_param (
    param_code VARCHAR(50) PRIMARY KEY COMMENT '参数代码',
    param_value VARCHAR(500) COMMENT '参数值',
    param_desc VARCHAR(200) COMMENT '参数描述',
    app_system VARCHAR(50) COMMENT '应用系统',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局参数表';

-- ============================================================
-- 2. 创建分片表（按 shard_id 分布式存储）
-- ============================================================

-- 客户表（分片键：cust_id）
CREATE TABLE IF NOT EXISTS customer_shard_0 (
    cust_id BIGINT NOT NULL COMMENT '客户 ID',
    shard_id TINYINT NOT NULL DEFAULT 0 COMMENT '分片 ID',
    cust_no VARCHAR(50) NOT NULL COMMENT '客户编号',
    cust_name VARCHAR(100) NOT NULL COMMENT '客户名称',
    cust_type TINYINT COMMENT '客户类型：1-个人，2-企业',
    cert_type VARCHAR(10) COMMENT '证件类型：ID-身份证，PASSPORT-护照',
    cert_no VARCHAR(50) COMMENT '证件号码',
    mobile VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    risk_level TINYINT DEFAULT 1 COMMENT '风险等级：1-低，2-中，3-高',
    status TINYINT DEFAULT 1 COMMENT '状态：0-注销，1-正常，2-冻结，9-异常',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (cust_id, shard_id),
    UNIQUE KEY uk_cust_no (cust_no),
    KEY idx_cust_type (cust_type),
    KEY idx_status (status),
    KEY idx_create_time (create_time),
    KEY idx_cert_no (cert_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户表（分片 0）';

-- 账户表（分片键：account_id）
CREATE TABLE IF NOT EXISTS account_shard_0 (
    account_id BIGINT NOT NULL COMMENT '账户 ID',
    shard_id TINYINT NOT NULL DEFAULT 0 COMMENT '分片 ID',
    account_no VARCHAR(50) NOT NULL UNIQUE COMMENT '账号',
    cust_id BIGINT NOT NULL COMMENT '客户 ID',
    account_type VARCHAR(20) COMMENT '账户类型：SAVINGS-储蓄，CHECKING-结算',
    currency VARCHAR(10) DEFAULT 'CNY' COMMENT '币种',
    balance DECIMAL(20, 2) DEFAULT 0.00 COMMENT '余额',
    available_balance DECIMAL(20, 2) DEFAULT 0.00 COMMENT '可用余额',
    frozen_balance DECIMAL(20, 2) DEFAULT 0.00 COMMENT '冻结余额',
    status TINYINT DEFAULT 1 COMMENT '状态：0-销户，1-正常，2-冻结',
    open_date DATE COMMENT '开户日期',
    close_date DATE COMMENT '销户日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (account_id, shard_id),
    KEY idx_cust_id (cust_id),
    KEY idx_status (status),
    KEY idx_open_date (open_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账户表（分片 0）';

-- 交易流水表（分片键：account_id，按账户分片）
CREATE TABLE IF NOT EXISTS transaction_log_shard_0 (
    trans_id BIGINT NOT NULL COMMENT '交易 ID',
    shard_id TINYINT NOT NULL DEFAULT 0 COMMENT '分片 ID',
    account_id BIGINT NOT NULL COMMENT '账户 ID',
    trans_no VARCHAR(64) NOT NULL UNIQUE COMMENT '交易流水号',
    trans_type VARCHAR(20) NOT NULL COMMENT '交易类型：DEPOSIT-存款，WITHDRAW-取款，TRANSFER-转账',
    trans_amount DECIMAL(20, 2) NOT NULL COMMENT '交易金额',
    trans_direction TINYINT COMMENT '交易方向：1-收入，2-支出',
    balance_before DECIMAL(20, 2) COMMENT '交易前余额',
    balance_after DECIMAL(20, 2) COMMENT '交易后余额',
    counterparty_account VARCHAR(50) COMMENT '对手方账号',
    counterparty_name VARCHAR(100) COMMENT '对手方名称',
    trans_channel VARCHAR(20) COMMENT '交易渠道：COUNTER-柜面，ATM, MOBILE-手机银行，WEB-网银',
    trans_status TINYINT DEFAULT 1 COMMENT '交易状态：0-失败，1-成功，2-处理中',
    trans_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '交易时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (trans_id, shard_id),
    KEY idx_account_id (account_id),
    KEY idx_trans_time (trans_time),
    KEY idx_trans_type (trans_type),
    KEY idx_trans_status (trans_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易流水表（分片 0）';

-- ============================================================
-- 3. 插入示例数据
-- ============================================================

-- 全局机构数据
INSERT INTO global_org (org_id, org_name, org_type, parent_org_id, region_code, status) VALUES
('ORG001', '总行营业部', 'HEADQUARTER', NULL, 'BJ', 1),
('ORG002', '北京分行', 'BRANCH', 'ORG001', 'BJ', 1),
('ORG003', '上海分行', 'BRANCH', 'ORG001', 'SH', 1),
('ORG004', '深圳分行', 'BRANCH', 'ORG001', 'SZ', 1),
('ORG005', '北京分行朝阳支行', 'SUB_BRANCH', 'ORG002', 'BJ', 1);

-- 全局参数数据
INSERT INTO global_param (param_code, param_value, param_desc, app_system) VALUES
('SYS_DATE_FORMAT', 'yyyy-MM-dd HH:mm:ss', '系统日期格式', 'CORE'),
('TXN_BATCH_SIZE', '1000', '交易_batch 处理大小', 'CORE'),
('CUST_RISK_LEVELS', '1,2,3', '客户风险等级列表', 'RISK'),
('ACCOUNT_PREFIX', '622202', '账号前缀', 'CORE');

-- 客户数据（分片 0）
INSERT INTO customer_shard_0 (cust_id, shard_id, cust_no, cust_name, cust_type, cert_type, cert_no, mobile, email, risk_level, status, create_time) VALUES
(1000001, 0, 'CUST000001', '张三', 1, 'ID', '110101199001011234', '13800138001', 'zhangsan@example.com', 1, 1, '2024-01-15 10:30:00'),
(1000002, 0, 'CUST000002', '李四', 1, 'ID', '110101199002022345', '13800138002', 'lisi@example.com', 1, 1, '2024-01-16 11:00:00'),
(1000003, 0, 'CUST000003', '王五', 1, 'ID', '110101199003033456', '13800138003', 'wangwu@example.com', 2, 1, '2024-01-17 14:20:00'),
(1000004, 0, 'CUST000004', '某科技有限公司', 2, 'USCC', '91110000123456789X', '13800138004', 'finance@tech.com', 1, 1, '2024-01-18 09:15:00'),
(1000005, 0, 'CUST000005', '赵六', 1, 'ID', '110101199004044567', '13800138005', 'zhaoliu@example.com', 3, 2, '2024-01-19 16:45:00');

-- 账户数据（分片 0）
INSERT INTO account_shard_0 (account_id, shard_id, account_no, cust_id, account_type, currency, balance, available_balance, frozen_balance, status, open_date) VALUES
(2000001, 0, '6222021000000001', 1000001, 'SAVINGS', 'CNY', 50000.00, 50000.00, 0.00, 1, '2024-01-15'),
(2000002, 0, '6222021000000002', 1000001, 'CHECKING', 'CNY', 120000.00, 120000.00, 0.00, 1, '2024-01-15'),
(2000003, 0, '6222021000000003', 1000002, 'SAVINGS', 'CNY', 35000.00, 30000.00, 5000.00, 1, '2024-01-16'),
(2000004, 0, '6222021000000004', 1000003, 'SAVINGS', 'CNY', 80000.00, 80000.00, 0.00, 1, '2024-01-17'),
(2000005, 0, '6222021000000005', 1000004, 'CHECKING', 'CNY', 500000.00, 500000.00, 0.00, 1, '2024-01-18'),
(2000006, 0, '6222021000000006', 1000005, 'SAVINGS', 'CNY', 0.00, 0.00, 0.00, 2, '2024-01-19');

-- 交易流水数据（分片 0）
INSERT INTO transaction_log_shard_0 (trans_id, shard_id, account_id, trans_no, trans_type, trans_amount, trans_direction, balance_before, balance_after, counterparty_account, counterparty_name, trans_channel, trans_status, trans_time) VALUES
(3000001, 0, 2000001, 'TXN202401150001', 'DEPOSIT', 10000.00, 1, 40000.00, 50000.00, NULL, NULL, 'COUNTER', 1, '2024-01-15 10:35:00'),
(3000002, 0, 2000002, 'TXN202401150002', 'TRANSFER', 20000.00, 2, 140000.00, 120000.00, '6222029999999999', '某某公司', 'WEB', 1, '2024-01-15 14:20:00'),
(3000003, 0, 2000003, 'TXN202401160003', 'DEPOSIT', 30000.00, 1, 5000.00, 35000.00, NULL, NULL, 'ATM', 1, '2024-01-16 11:05:00'),
(3000004, 0, 2000003, 'TXN202401160004', 'WITHDRAW', 5000.00, 2, 35000.00, 30000.00, NULL, NULL, 'ATM', 1, '2024-01-16 16:30:00'),
(3000005, 0, 2000004, 'TXN202401170005', 'DEPOSIT', 80000.00, 1, 0.00, 80000.00, NULL, NULL, 'MOBILE', 1, '2024-01-17 14:25:00'),
(3000006, 0, 2000005, 'TXN202401180006', 'DEPOSIT', 500000.00, 1, 0.00, 500000.00, NULL, '某科技有限公司', 'COUNTER', 1, '2024-01-18 09:20:00'),
(3000007, 0, 2000001, 'TXN202401190007', 'TRANSFER', 5000.00, 2, 50000.00, 45000.00, '6222021000000003', '李四', 'MOBILE', 1, '2024-01-19 10:00:00'),
(3000008, 0, 2000003, 'TXN202401190008', 'DEPOSIT', 5000.00, 1, 30000.00, 35000.00, '6222021000000001', '张三', 'MOBILE', 1, '2024-01-19 10:01:00');

-- ============================================================
-- 4. 创建视图（模拟跨分片查询）
-- ============================================================

-- 全量客户视图（联合多个分片）
CREATE OR REPLACE VIEW v_customer_all AS
SELECT * FROM customer_shard_0
UNION ALL
SELECT * FROM customer_shard_0 WHERE 1=0; -- 预留分片扩展

-- 全量账户视图
CREATE OR REPLACE VIEW v_account_all AS
SELECT * FROM account_shard_0
UNION ALL
SELECT * FROM account_shard_0 WHERE 1=0;

-- 全量交易流水视图
CREATE OR REPLACE VIEW v_transaction_all AS
SELECT * FROM transaction_log_shard_0
UNION ALL
SELECT * FROM transaction_log_shard_0 WHERE 1=0;

-- ============================================================
-- 5. 创建索引建议示例表（用于测试索引优化）
-- ============================================================

-- 慢查询日志分析表
CREATE TABLE IF NOT EXISTS slow_query_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_hash VARCHAR(64) COMMENT 'SQL 哈希值',
    query_text TEXT COMMENT 'SQL 原文',
    exec_count INT DEFAULT 1 COMMENT '执行次数',
    total_time_ms BIGINT COMMENT '总耗时 (毫秒)',
    avg_time_ms DECIMAL(10, 2) COMMENT '平均耗时 (毫秒)',
    max_time_ms BIGINT COMMENT '最大耗时 (毫秒)',
    rows_examined BIGINT COMMENT '扫描行数',
    rows_sent BIGINT COMMENT '返回行数',
    db_name VARCHAR(100) COMMENT '数据库名',
    user_name VARCHAR(100) COMMENT '用户名',
    host_address VARCHAR(100) COMMENT '主机地址',
    first_seen DATETIME COMMENT '首次出现时间',
    last_seen DATETIME COMMENT '最后出现时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='慢查询日志分析表';

-- 插入慢查询示例
INSERT INTO slow_query_log (query_hash, query_text, exec_count, total_time_ms, avg_time_ms, max_time_ms, rows_examined, rows_sent, db_name, user_name, first_seen, last_seen) VALUES
('abc123def456', 'SELECT * FROM customer_shard_0 WHERE cert_no LIKE ?', 150, 45000, 300.00, 2500, 500000, 150, 'test_db', 'app_user', '2024-01-01 00:00:00', '2024-01-20 23:59:59'),
('xyz789ghi012', 'SELECT c.*, a.* FROM customer_shard_0 c JOIN account_shard_0 a ON c.cust_id = a.cust_id WHERE c.status = ?', 500, 125000, 250.00, 3000, 1000000, 5000, 'test_db', 'app_user', '2024-01-01 00:00:00', '2024-01-20 23:59:59'),
('mno345pqr678', 'SELECT account_id, SUM(trans_amount) FROM transaction_log_shard_0 WHERE trans_time BETWEEN ? AND ? GROUP BY account_id', 1000, 500000, 500.00, 8000, 5000000, 10000, 'test_db', 'report_user', '2024-01-01 00:00:00', '2024-01-20 23:59:59');

-- ============================================================
-- 6. 统计信息查询视图
-- ============================================================

-- 表统计信息视图
CREATE OR REPLACE VIEW v_table_stats AS
SELECT 
    TABLE_SCHEMA AS db_name,
    TABLE_NAME AS table_name,
    TABLE_ROWS AS row_count,
    ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) AS total_size_mb,
    ROUND(DATA_LENGTH / 1024 / 1024, 2) AS data_size_mb,
    ROUND(INDEX_LENGTH / 1024 / 1024, 2) AS index_size_mb,
    ENGINE AS engine,
    TABLE_COLLATION AS collation,
    CREATE_TIME AS create_time,
    UPDATE_TIME AS update_time
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'test_db';

-- 索引统计信息视图
CREATE OR REPLACE VIEW v_index_stats AS
SELECT 
    TABLE_SCHEMA AS db_name,
    TABLE_NAME AS table_name,
    INDEX_NAME AS index_name,
    SEQ_IN_INDEX AS column_position,
    COLUMN_NAME AS column_name,
    NON_UNIQUE AS is_non_unique,
    INDEX_TYPE AS index_type,
    CARDINALITY AS cardinality,
    NULLABLE AS is_nullable
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'test_db'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- ============================================================
-- Script End
-- ============================================================
