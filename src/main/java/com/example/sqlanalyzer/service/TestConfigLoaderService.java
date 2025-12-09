package com.example.sqlanalyzer.service;

import com.example.sqlanalyzer.model.DistributedTransactionTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 测试配置加载服务
 */
@Service
public class TestConfigLoaderService {

    private final ObjectMapper yamlMapper;

    public TestConfigLoaderService() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * 从classpath加载配置文件
     */
    public DistributedTransactionTestConfig loadFromClasspath(String configPath) throws IOException {
        Resource resource = new ClassPathResource(configPath);
        try (InputStream inputStream = resource.getInputStream()) {
            DistributedTransactionTestConfig config = yamlMapper.readValue(inputStream, DistributedTransactionTestConfig.class);
            validateConfig(config);
            return config;
        }
    }

    /**
     * 从文件系统加载配置文件
     */
    public DistributedTransactionTestConfig loadFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("配置文件不存在: " + filePath);
        }
        
        try (InputStream inputStream = Files.newInputStream(path)) {
            DistributedTransactionTestConfig config = yamlMapper.readValue(inputStream, DistributedTransactionTestConfig.class);
            validateConfig(config);
            return config;
        }
    }

    /**
     * 从YAML字符串加载配置
     */
    public DistributedTransactionTestConfig loadFromYamlString(String yamlContent) throws IOException {
        DistributedTransactionTestConfig config = yamlMapper.readValue(yamlContent, DistributedTransactionTestConfig.class);
        validateConfig(config);
        return config;
    }

    /**
     * 列出classpath中所有可用的测试场景配置文件
     */
    public List<String> listAvailableScenarios() {
        List<String> scenarios = new ArrayList<>();
        try {
            // 查找test-scenarios目录下的所有yml文件
            Resource resource = new ClassPathResource("test-scenarios");
            if (resource.exists() && resource.getFile().isDirectory()) {
                File dir = resource.getFile();
                File[] files = dir.listFiles((d, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
                if (files != null) {
                    for (File file : files) {
                        scenarios.add("test-scenarios/" + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            // 如果无法访问文件系统（如在jar包中），返回空列表
            System.err.println("无法列出测试场景: " + e.getMessage());
        }
        return scenarios;
    }

    /**
     * 验证配置有效性
     */
    private void validateConfig(DistributedTransactionTestConfig config) {
        if (config == null || config.getScenario() == null) {
            throw new IllegalArgumentException("配置无效: scenario节点不能为空");
        }

        DistributedTransactionTestConfig.Scenario scenario = config.getScenario();
        
        if (!StringUtils.hasText(scenario.getName())) {
            throw new IllegalArgumentException("配置无效: scenario.name不能为空");
        }

        if (!StringUtils.hasText(scenario.getDatasource())) {
            throw new IllegalArgumentException("配置无效: scenario.datasource不能为空");
        }

        if (scenario.getThreads() == null || scenario.getThreads().isEmpty()) {
            throw new IllegalArgumentException("配置无效: scenario.threads不能为空");
        }

        // 验证所有thread的step数量是否相同
        int expectedStepCount = -1;
        for (Map.Entry<String, DistributedTransactionTestConfig.ThreadConfig> entry : scenario.getThreads().entrySet()) {
            String threadId = entry.getKey();
            DistributedTransactionTestConfig.ThreadConfig threadConfig = entry.getValue();
            
            if (threadConfig == null) {
                throw new IllegalArgumentException("配置无效: threads[" + threadId + "]不能为空");
            }
            
            List<com.example.sqlanalyzer.model.SqlExecutionStep> steps = threadConfig.getSteps();
            if (steps == null || steps.isEmpty()) {
                throw new IllegalArgumentException("配置无效: threads[" + threadId + "].steps不能为空");
            }

            // 设置期望的step数量
            if (expectedStepCount == -1) {
                expectedStepCount = steps.size();
            } else if (steps.size() != expectedStepCount) {
                throw new IllegalArgumentException("配置无效: 所有thread的step数量必须相同，" +
                    "thread[" + threadId + "]的step数量为" + steps.size() + "，期望为" + expectedStepCount);
            }

            // 验证每个步骤
            for (int i = 0; i < steps.size(); i++) {
                var step = steps.get(i);
                List<String> sqlList = step.getSqlList();
                if (sqlList == null || sqlList.isEmpty()) {
                    throw new IllegalArgumentException("配置无效: threads[" + threadId + "].steps[" + i + "]必须包含至少一个SQL语句");
                }
                // 验证SQL不为空
                for (int j = 0; j < sqlList.size(); j++) {
                    if (!StringUtils.hasText(sqlList.get(j))) {
                        throw new IllegalArgumentException("配置无效: threads[" + threadId + "].steps[" + i + "].sqls[" + j + "]不能为空");
                    }
                }
            }
        }
    }
}

