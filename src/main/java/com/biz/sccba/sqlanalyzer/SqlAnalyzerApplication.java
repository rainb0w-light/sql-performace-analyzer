package com.biz.sccba.sqlanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties
@EnableAsync
public class SqlAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SqlAnalyzerApplication.class, args);
    }
}
