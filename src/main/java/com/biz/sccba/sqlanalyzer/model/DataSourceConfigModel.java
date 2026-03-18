package com.biz.sccba.sqlanalyzer.model;

import lombok.Data;

/**
 * 数据源配置模型
 */
@Data
public class DataSourceConfigModel {
    private String name;
    private String url;
    private String username;
    private String password;
    private String driverClassName;
}
