package com.example.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户实体类
 * 对应数据库表 users
 */
@Data
public class User {
    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 密码
     */
    private String password;

    /**
     * 状态
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 订单列表（关联查询使用）
     */
    private List<Order> orders;
}
