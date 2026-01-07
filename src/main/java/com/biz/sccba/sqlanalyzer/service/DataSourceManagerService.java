package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.config.DataSourceConfig;
import com.biz.sccba.sqlanalyzer.data.DataSourceConfigModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据源管理服务
 * 提供数据源的获取和管理功能
 */
@Service
public class DataSourceManagerService {

    private final Map<String, DataSource> dataSourceMap;
    private final Map<String, JdbcTemplate> jdbcTemplateMap;
    private final List<DataSourceConfigModel> dataSourceConfigs;

    @Autowired
    public DataSourceManagerService(
            Map<String, DataSource> dataSourceMap,
            Map<String, JdbcTemplate> jdbcTemplateMap,
            DataSourceConfig.DataSourceConfigProperties properties) {
        this.dataSourceMap = dataSourceMap != null ? dataSourceMap : new HashMap<>();
        this.jdbcTemplateMap = jdbcTemplateMap != null ? jdbcTemplateMap : new HashMap<>();
        this.dataSourceConfigs = properties != null && properties.getConfigs() != null 
            ? properties.getConfigs() 
            : List.of();
    }

    /**
     * 根据名称获取数据源
     */
    public DataSource getDataSource(String name) {
        if (name == null || name.trim().isEmpty()) {
            // 如果没有指定名称，返回第一个数据源（向后兼容）
            if (!dataSourceMap.isEmpty()) {
                return dataSourceMap.values().iterator().next();
            }
            throw new IllegalStateException("没有配置任何数据源");
        }
        
        DataSource dataSource = dataSourceMap.get(name);
        if (dataSource == null) {
            throw new IllegalArgumentException("未找到名称为 '" + name + "' 的数据源");
        }
        return dataSource;
    }

    /**
     * 根据名称获取JdbcTemplate
     */
    public JdbcTemplate getJdbcTemplate(String name) {
        if (name == null || name.trim().isEmpty()) {
            // 如果没有指定名称，返回第一个JdbcTemplate（向后兼容）
            if (!jdbcTemplateMap.isEmpty()) {
                return jdbcTemplateMap.values().iterator().next();
            }
            throw new IllegalStateException("没有配置任何数据源");
        }
        
        JdbcTemplate jdbcTemplate = jdbcTemplateMap.get(name);
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("未找到名称为 '" + name + "' 的数据源");
        }
        return jdbcTemplate;
    }

    /**
     * 获取所有数据源配置列表
     */
    public List<DataSourceInfo> getAllDataSources() {
        return dataSourceConfigs.stream()
            .map(config -> {
                DataSourceInfo info = new DataSourceInfo();
                info.setName(config.getName());
                info.setUrl(config.getUrl());
                info.setUsername(config.getUsername());
                // 不返回密码
                return info;
            })
            .collect(Collectors.toList());
    }

    /**
     * 检查数据源是否存在
     */
    public boolean exists(String name) {
        return name != null && dataSourceMap.containsKey(name);
    }

    /**
     * 数据源信息（用于API返回）
     */
    public static class DataSourceInfo {
        private String name;
        private String url;
        private String username;

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
    }
}

