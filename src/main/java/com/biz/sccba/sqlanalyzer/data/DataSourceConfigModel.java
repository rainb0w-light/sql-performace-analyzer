package com.biz.sccba.sqlanalyzer.data;

/**
 * 数据源配置模型
 */
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
     * HikariCP 连接池配置
     */
    private HikariConfig hikari = new HikariConfig();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public HikariConfig getHikari() {
        return hikari;
    }

    public void setHikari(HikariConfig hikari) {
        this.hikari = hikari;
    }

    /**
     * HikariCP 配置
     */
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

        public Integer getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(Integer maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public Integer getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(Integer minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public Long getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(Long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public Long getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(Long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public Long getMaxLifetime() {
            return maxLifetime;
        }

        public void setMaxLifetime(Long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }

        public Long getLeakDetectionThreshold() {
            return leakDetectionThreshold;
        }

        public void setLeakDetectionThreshold(Long leakDetectionThreshold) {
            this.leakDetectionThreshold = leakDetectionThreshold;
        }
    }
}
