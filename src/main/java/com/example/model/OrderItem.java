package com.example.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单项实体类
 * 对应数据库表 order_items
 */
@Data
public class OrderItem {
    /**
     * 订单项ID
     */
    private Long id;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 单价
     */
    private BigDecimal price;

    /**
     * 订单ID
     */
    private Long orderId;
}
