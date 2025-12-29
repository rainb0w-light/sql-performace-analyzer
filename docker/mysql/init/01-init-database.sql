-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS test_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE test_db;

-- 创建用户表（demo表）
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL  COMMENT '用户名',
    email VARCHAR(100) NOT NULL  COMMENT '邮箱',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    full_name VARCHAR(100) COMMENT '全名',
    age INT COMMENT '年龄',
    status ENUM('active', 'inactive', 'suspended') DEFAULT 'active' COMMENT '状态',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 创建订单表（demo表）
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    order_number VARCHAR(50) NOT NULL UNIQUE COMMENT '订单号',
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '订单总额',
    status ENUM('pending', 'paid', 'shipped', 'completed', 'cancelled') DEFAULT 'pending' COMMENT '订单状态',
    shipping_address TEXT COMMENT '收货地址',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_order_number (order_number),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 创建订单项表（demo表）
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    quantity INT NOT NULL DEFAULT 1 COMMENT '数量',
    unit_price DECIMAL(10, 2) NOT NULL COMMENT '单价',
    subtotal DECIMAL(10, 2) NOT NULL COMMENT '小计',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表';

-- 插入示例数据
INSERT INTO users (username, email, password_hash, full_name, age, status) VALUES
('alice', 'alice@example.com', 'hash1', 'Alice Smith', 25, 'active'),
('bob', 'bob@example.com', 'hash2', 'Bob Johnson', 30, 'active'),
('charlie', 'charlie@example.com', 'hash3', 'Charlie Brown', 28, 'active'),
('diana', 'diana@example.com', 'hash4', 'Diana Prince', 32, 'inactive'),
('eve', 'eve@example.com', 'hash5', 'Eve Wilson', 27, 'active');

INSERT INTO orders (user_id, order_number, total_amount, status, shipping_address) VALUES
(1, 'ORD001', 299.99, 'completed', '123 Main St, City, State 12345'),
(1, 'ORD002', 159.50, 'shipped', '123 Main St, City, State 12345'),
(2, 'ORD003', 89.99, 'paid', '456 Oak Ave, City, State 67890'),
(3, 'ORD004', 449.00, 'pending', '789 Pine Rd, City, State 11111'),
(5, 'ORD005', 199.99, 'completed', '321 Elm St, City, State 22222');

INSERT INTO order_items (order_id, product_name, quantity, unit_price, subtotal) VALUES
(1, 'Laptop', 1, 299.99, 299.99),
(2, 'Mouse', 2, 29.75, 59.50),
(2, 'Keyboard', 1, 99.99, 99.99),
(3, 'Monitor', 1, 89.99, 89.99),
(4, 'Desktop PC', 1, 449.00, 449.00),
(5, 'Tablet', 1, 199.99, 199.99);

