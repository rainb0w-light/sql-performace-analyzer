-- =====================================================
-- GoldenDB 慢 SQL 分析器 - 测试数据库脚本
-- =====================================================
-- 用于测试 MyBatis XML 解析和 SQL 分析功能
-- =====================================================

-- 创建测试数据库
CREATE DATABASE IF NOT EXISTS test_financial_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE test_financial_db;

-- =====================================================
-- 1. 客户信息表
-- =====================================================
DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer` (
    `cust_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '客户 ID',
    `cust_no` VARCHAR(32) NOT NULL COMMENT '客户编号',
    `cust_name` VARCHAR(128) NOT NULL COMMENT '客户姓名',
    `cust_type` TINYINT NOT NULL DEFAULT 1 COMMENT '客户类型：1-个人 2-企业',
    `id_type` TINYINT DEFAULT 1 COMMENT '证件类型：1-身份证 2-护照 3-军官证 4-其他',
    `id_no` VARCHAR(64) COMMENT '证件号码',
    `mobile` VARCHAR(20) COMMENT '手机号码',
    `email` VARCHAR(128) COMMENT '电子邮箱',
    `risk_level` TINYINT DEFAULT 1 COMMENT '风险等级：1-低风险 2-中风险 3-高风险',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-注销 1-正常 2-冻结 9-异常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`cust_id`),
    UNIQUE KEY `uk_cust_no` (`cust_no`),
    KEY `idx_mobile` (`mobile`),
    KEY `idx_id_no` (`id_no`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户信息表';

-- =====================================================
-- 2. 账户信息表
-- =====================================================
DROP TABLE IF EXISTS `account`;
CREATE TABLE `account` (
    `acct_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '账户 ID',
    `acct_no` VARCHAR(32) NOT NULL COMMENT '账户编号',
    `cust_id` BIGINT NOT NULL COMMENT '客户 ID',
    `acct_type` TINYINT NOT NULL DEFAULT 1 COMMENT '账户类型：1-活期 2-定期 3-理财',
    `currency` VARCHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `balance` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '余额',
    `available_balance` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '可用余额',
    `frozen_balance` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '冻结余额',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-销户 1-正常 2-冻结 3-止付',
    `open_date` DATE NOT NULL COMMENT '开户日期',
    `close_date` DATE COMMENT '销户日期',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`acct_id`),
    UNIQUE KEY `uk_acct_no` (`acct_no`),
    KEY `idx_cust_id` (`cust_id`),
    KEY `idx_status` (`status`),
    KEY `idx_open_date` (`open_date`),
    KEY `idx_acct_type` (`acct_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户信息表';

-- =====================================================
-- 3. 交易流水表（分区表）
-- =====================================================
DROP TABLE IF EXISTS `transaction_log`;
CREATE TABLE `transaction_log` (
    `txn_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '交易 ID',
    `txn_no` VARCHAR(64) NOT NULL COMMENT '交易流水号',
    `acct_id` BIGINT NOT NULL COMMENT '账户 ID',
    `cust_id` BIGINT NOT NULL COMMENT '客户 ID',
    `txn_type` TINYINT NOT NULL COMMENT '交易类型：1-存入 2-支取 3-转账 4-缴费 5-消费',
    `txn_channel` TINYINT DEFAULT 1 COMMENT '交易渠道：1-柜面 2-ATM 3-网银 4-手机银行 5-第三方支付',
    `txn_amount` DECIMAL(18,2) NOT NULL COMMENT '交易金额',
    `txn_currency` VARCHAR(3) NOT NULL DEFAULT 'CNY' COMMENT '交易币种',
    `balance_after` DECIMAL(18,2) NOT NULL COMMENT '交易后余额',
    `counterparty_acct_no` VARCHAR(32) COMMENT '对方账户号',
    `counterparty_cust_name` VARCHAR(128) COMMENT '对方户名',
    `remark` VARCHAR(256) COMMENT '备注',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-失败 1-成功 2-处理中 9-已冲正',
    `txn_date` DATE NOT NULL COMMENT '交易日期',
    `txn_time` TIME NOT NULL COMMENT '交易时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`txn_id`, `txn_date`),
    KEY `idx_txn_no` (`txn_no`),
    KEY `idx_acct_id` (`acct_id`),
    KEY `idx_cust_id` (`cust_id`),
    KEY `idx_txn_date` (`txn_date`),
    KEY `idx_txn_time` (`txn_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
PARTITION BY RANGE (`txn_date`) (
    PARTITION p202401 VALUES LESS THAN ('2024-02-01'),
    PARTITION p202402 VALUES LESS THAN ('2024-03-01'),
    PARTITION p202403 VALUES LESS THAN ('2024-04-01'),
    PARTITION p202404 VALUES LESS THAN ('2024-05-01'),
    PARTITION p202405 VALUES LESS THAN ('2024-06-01'),
    PARTITION p202406 VALUES LESS THAN ('2024-07-01'),
    PARTITION p_max VALUES LESS THAN MAXVALUE
) COMMENT='交易流水表';

-- =====================================================
-- 4. 转账交易表
-- =====================================================
DROP TABLE IF EXISTS `transfer_order`;
CREATE TABLE `transfer_order` (
    `order_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单 ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
    `from_acct_id` BIGINT NOT NULL COMMENT '转出账户 ID',
    `from_cust_id` BIGINT NOT NULL COMMENT '转出客户 ID',
    `to_acct_id` BIGINT NOT NULL COMMENT '转入账户 ID',
    `to_cust_id` BIGINT NOT NULL COMMENT '转入客户 ID',
    `amount` DECIMAL(18,2) NOT NULL COMMENT '转账金额',
    `fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '手续费',
    `priority` TINYINT DEFAULT 1 COMMENT '优先级：1-普通 2-加急 3-特急',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待处理 1-处理中 2-成功 3-失败 4-已撤销',
    `fail_reason` VARCHAR(256) COMMENT '失败原因',
    `submit_time` DATETIME NOT NULL COMMENT '提交时间',
    `process_time` DATETIME COMMENT '处理时间',
    `complete_time` DATETIME COMMENT '完成时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`order_id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_from_acct` (`from_acct_id`),
    KEY `idx_to_acct` (`to_acct_id`),
    KEY `idx_status` (`status`),
    KEY `idx_submit_time` (`submit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转账交易表';

-- =====================================================
-- 5. 产品表
-- =====================================================
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `prod_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '产品 ID',
    `prod_no` VARCHAR(32) NOT NULL COMMENT '产品编号',
    `prod_name` VARCHAR(128) NOT NULL COMMENT '产品名称',
    `prod_type` TINYINT NOT NULL COMMENT '产品类型：1-存款 2-贷款 3-理财 4-基金 5-保险',
    `risk_level` TINYINT DEFAULT 1 COMMENT '风险等级：1-R1 2-R2 3-R3 4-R4 5-R5',
    `min_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '起购金额',
    `max_amount` DECIMAL(18,2) COMMENT '限购金额',
    `expected_yield` DECIMAL(6,4) COMMENT '预期收益率（%）',
    `term_days` INT COMMENT '期限（天）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-未发布 1-销售中 2-已售罄 3-已下架',
    `start_date` DATE COMMENT '开始日期',
    `end_date` DATE COMMENT '结束日期',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`prod_id`),
    UNIQUE KEY `uk_prod_no` (`prod_no`),
    KEY `idx_prod_type` (`prod_type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品表';

-- =====================================================
-- 6. 客户持仓表
-- =====================================================
DROP TABLE IF EXISTS `customer_holding`;
CREATE TABLE `customer_holding` (
    `holding_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '持仓 ID',
    `cust_id` BIGINT NOT NULL COMMENT '客户 ID',
    `acct_id` BIGINT NOT NULL COMMENT '账户 ID',
    `prod_id` BIGINT NOT NULL COMMENT '产品 ID',
    `holding_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '持仓金额',
    `holding_shares` DECIMAL(18,4) NOT NULL DEFAULT 0.00 COMMENT '持仓份额',
    `cost_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '成本金额',
    `profit_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '盈亏金额',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-持仓中 2-已赎回 3-已到期',
    `purchase_date` DATE NOT NULL COMMENT '购买日期',
    `redeem_date` DATE COMMENT '赎回日期',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`holding_id`),
    KEY `idx_cust_id` (`cust_id`),
    KEY `idx_acct_id` (`acct_id`),
    KEY `idx_prod_id` (`prod_id`),
    KEY `idx_status` (`status`),
    KEY `idx_purchase_date` (`purchase_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户持仓表';

-- =====================================================
-- 测试数据
-- =====================================================

-- 插入测试客户
INSERT INTO customer (cust_no, cust_name, cust_type, id_type, id_no, mobile, email, risk_level, status) VALUES
('CUST001', '张三', 1, 1, '110101199001011234', '13800138001', 'zhangsan@test.com', 1, 1),
('CUST002', '李四', 1, 1, '110101199002022345', '13800138002', 'lisi@test.com', 2, 1),
('CUST003', '王五', 1, 1, '110101199003033456', '13800138003', 'wangwu@test.com', 1, 1),
('CUST004', '赵六', 1, 1, '110101199004044567', '13800138004', 'zhaoliu@test.com', 3, 2),
('CUST005', '某企业有限公司', 2, 1, '91110000123456789X', '13800138005', 'company@test.com', 2, 1);

-- 插入测试账户
INSERT INTO account (acct_no, cust_id, acct_type, currency, balance, available_balance, status, open_date) VALUES
('ACCT001', 1, 1, 'CNY', 100000.00, 95000.00, 1, '2024-01-15'),
('ACCT002', 1, 2, 'CNY', 500000.00, 500000.00, 1, '2024-01-15'),
('ACCT003', 2, 1, 'CNY', 50000.00, 48000.00, 1, '2024-02-20'),
('ACCT004', 3, 1, 'CNY', 200000.00, 200000.00, 1, '2024-03-10'),
('ACCT005', 4, 1, 'CNY', 10000.00, 0.00, 2, '2024-04-05');

-- 插入测试交易流水（最近 6 个月）
INSERT INTO transaction_log (txn_no, acct_id, cust_id, txn_type, txn_channel, txn_amount, txn_currency, balance_after, status, txn_date, txn_time) VALUES
('TXN20240115001', 1, 1, 1, 1, 50000.00, 'CNY', 150000.00, 1, '2024-01-15', '10:30:00'),
('TXN20240115002', 1, 1, 3, 3, 5000.00, 'CNY', 145000.00, 1, '2024-01-15', '14:20:00'),
('TXN20240220001', 3, 2, 1, 1, 30000.00, 'CNY', 80000.00, 1, '2024-02-20', '09:15:00'),
('TXN20240310001', 4, 3, 1, 4, 100000.00, 'CNY', 300000.00, 1, '2024-03-10', '16:45:00'),
('TXN20240405001', 5, 4, 2, 2, 10000.00, 'CNY', 0.00, 1, '2024-04-05', '11:00:00'),
('TXN20240515001', 1, 1, 3, 5, 2000.00, 'CNY', 93000.00, 1, '2024-05-15', '20:30:00'),
('TXN20240620001', 2, 1, 3, 4, 15000.00, 'CNY', 485000.00, 1, '2024-06-20', '15:00:00');

-- 插入测试转账订单
INSERT INTO transfer_order (order_no, from_acct_id, from_cust_id, to_acct_id, to_cust_id, amount, fee, priority, status, submit_time, process_time, complete_time) VALUES
('ORD20240115001', 1, 1, 3, 2, 5000.00, 0.00, 1, 2, '2024-01-15 14:00:00', '2024-01-15 14:01:00', '2024-01-15 14:02:00'),
('ORD20240220001', 3, 2, 4, 3, 10000.00, 5.00, 2, 2, '2024-02-20 10:00:00', '2024-02-20 10:01:00', '2024-02-20 10:05:00'),
('ORD20240310001', 4, 3, 1, 1, 50000.00, 0.00, 1, 2, '2024-03-10 11:30:00', '2024-03-10 11:31:00', '2024-03-10 11:32:00'),
('ORD20240405001', 5, 4, 2, 1, 1000.00, 2.00, 1, 3, '2024-04-05 12:00:00', '2024-04-05 12:01:00', '2024-04-05 12:02:00');

-- 插入测试产品
INSERT INTO product (prod_no, prod_name, prod_type, risk_level, min_amount, max_amount, expected_yield, term_days, status, start_date, end_date) VALUES
('PROD001', '稳盈宝 1 号', 3, 2, 10000.00, 1000000.00, 4.5000, 365, 1, '2024-01-01', '2024-12-31'),
('PROD002', '灵活存', 1, 1, 1000.00, null, 2.8000, 30, 1, '2024-01-01', '2024-12-31'),
('PROD003', '高收益理财', 3, 3, 100000.00, 5000000.00, 5.8000, 730, 1, '2024-03-01', '2024-06-30');

-- 插入测试持仓
INSERT INTO customer_holding (cust_id, acct_id, prod_id, holding_amount, holding_shares, cost_amount, profit_amount, status, purchase_date) VALUES
(1, 1, 1, 100000.00, 100000.00, 100000.00, 1200.00, 1, '2024-01-15'),
(1, 2, 2, 200000.00, 200000.00, 200000.00, 800.00, 1, '2024-02-01'),
(2, 3, 1, 50000.00, 50000.00, 50000.00, 300.00, 1, '2024-02-20'),
(3, 4, 3, 500000.00, 500000.00, 500000.00, -2000.00, 1, '2024-03-10');
