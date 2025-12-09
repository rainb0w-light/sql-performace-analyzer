package com.example.sqlanalyzer.model;

import lombok.Data;

/**
 * 数据源配置模型
 */
@Data
public class DataSourceConfigModel {
    /**
     * 数据源名称（唯一标识）
     */
    private String name;
    
    /**
     * JDBC URL
     */
    private String url;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * 驱动类名
     */
    private String driverClassName;
    
    /**
     * HikariCP连接池配置
     */
    private HikariConfig hikari = new HikariConfig();
    
    @Data
    public static class HikariConfig {
        /**
         * 最大连接池大小
         */
        private Integer maximumPoolSize = 10;
        
        /**
         * 最小空闲连接数
         */
        private Integer minimumIdle = 5;
        
        /**
         * 连接超时时间（毫秒）
         */
        private Long connectionTimeout = 30000L;
        
        /**
         * 空闲超时时间（毫秒）
         */
        private Long idleTimeout = 600000L;
        
        /**
         * 连接最大生命周期（毫秒）
         */
        private Long maxLifetime = 1800000L;
        
        /**
         * 连接泄漏检测阈值（毫秒）
         */
        private Long leakDetectionThreshold = 60000L;
    }
}

