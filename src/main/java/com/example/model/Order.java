package com.example.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单实体类
 * 对应数据库表 orders
 */
@Data
public class Order {
    /**
     * 订单ID
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderNumber;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 订单状态
     */
    private String status;

    /**
     * 订单项列表（关联查询使用）
     */
    private List<OrderItem> items;
}
