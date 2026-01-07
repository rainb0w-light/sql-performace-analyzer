package com.biz.sccba.sqlanalyzer.config;

import com.biz.sccba.sqlanalyzer.data.DataSourceConfigModel;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多数据源配置
 * - H2: 用于JPA缓存（主数据源）
 * - 多个MySQL数据源: 用于SQL分析（辅助数据源）
 */
@Configuration
@EnableConfigurationProperties(DataSourceConfig.DataSourceConfigProperties.class)
public class DataSourceConfig {


//    /**
//     * H2数据源（主数据源，用于JPA）
//     */
//    @Bean
//    @Primary
//    public DataSource h2DataSource(DataSourceProperties properties) {
//        return properties.initializeDataSourceBuilder().build();
//    }

    /**
     * 创建所有配置的数据源Bean
     */
    @Bean
    public Map<String, DataSource> dataSourceMap(DataSourceConfigProperties properties) {
        Map<String, DataSource> dataSourceMap = new HashMap<>();
        List<DataSourceConfigModel> configs = properties.getConfigs();
        
        if (configs != null && !configs.isEmpty()) {
            for (DataSourceConfigModel config : configs) {
                if (config.getName() == null || config.getName().trim().isEmpty()) {
                    throw new IllegalStateException("数据源配置中name不能为空");
                }
                
                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setJdbcUrl(config.getUrl());
                hikariConfig.setUsername(config.getUsername());
                hikariConfig.setPassword(config.getPassword());
                hikariConfig.setDriverClassName(config.getDriverClassName());
                
                // 设置HikariCP连接池配置
                if (config.getHikari() != null) {
                    DataSourceConfigModel.HikariConfig hikari = config.getHikari();
                    if (hikari.getMaximumPoolSize() != null) {
                        hikariConfig.setMaximumPoolSize(hikari.getMaximumPoolSize());
                    }
                    if (hikari.getMinimumIdle() != null) {
                        hikariConfig.setMinimumIdle(hikari.getMinimumIdle());
                    }
                    if (hikari.getConnectionTimeout() != null) {
                        hikariConfig.setConnectionTimeout(hikari.getConnectionTimeout());
                    }
                    if (hikari.getIdleTimeout() != null) {
                        hikariConfig.setIdleTimeout(hikari.getIdleTimeout());
                    }
                    if (hikari.getMaxLifetime() != null) {
                        hikariConfig.setMaxLifetime(hikari.getMaxLifetime());
                    }
                    if (hikari.getLeakDetectionThreshold() != null) {
                        hikariConfig.setLeakDetectionThreshold(hikari.getLeakDetectionThreshold());
                    }
                }
                
                // 设置数据源名称（用于连接池名称）
                hikariConfig.setPoolName("HikariPool-" + config.getName());
                
                DataSource dataSource = new HikariDataSource(hikariConfig);
                dataSourceMap.put(config.getName(), dataSource);
            }
        }
        
        return dataSourceMap;
    }

    /**
     * 创建所有配置的JdbcTemplate Bean
     */
    @Bean
    public Map<String, JdbcTemplate> jdbcTemplateMap(Map<String, DataSource> dataSourceMap) {
        Map<String, JdbcTemplate> jdbcTemplateMap = new HashMap<>();
        for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            jdbcTemplateMap.put(entry.getKey(), new JdbcTemplate(entry.getValue()));
        }
        return jdbcTemplateMap;
    }

    /**
     * 数据源配置属性类
     */
    @ConfigurationProperties(prefix = "spring.datasources")
    public static class DataSourceConfigProperties {
        private List<DataSourceConfigModel> configs;

        public List<DataSourceConfigModel> getConfigs() {
            return configs;
        }

        public void setConfigs(List<DataSourceConfigModel> configs) {
            this.configs = configs;
        }
    }
}

