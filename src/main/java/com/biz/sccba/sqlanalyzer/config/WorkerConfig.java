package com.biz.sccba.sqlanalyzer.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the Agent job worker loop when persistence and worker are both enabled. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "sql-analyzer.worker", name = "enabled", havingValue = "true")
public class WorkerConfig {
}
