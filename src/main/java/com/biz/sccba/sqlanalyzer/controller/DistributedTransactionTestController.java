package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.model.DistributedTransactionTestConfig;
import com.biz.sccba.sqlanalyzer.model.DistributedTransactionTestRecord;
import com.biz.sccba.sqlanalyzer.model.TestExecutionResult;
import com.biz.sccba.sqlanalyzer.service.DistributedTransactionExecutor;
import com.biz.sccba.sqlanalyzer.service.SequenceDiagramGenerator;
import com.biz.sccba.sqlanalyzer.service.TestConfigLoaderService;
import com.biz.sccba.sqlanalyzer.repository.DistributedTransactionTestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 分布式事务测试控制器
 */
@RestController
@RequestMapping("/api/distributed-transaction")
public class DistributedTransactionTestController {

    private static final Logger logger = LoggerFactory.getLogger(DistributedTransactionTestController.class);

    @Autowired
    private TestConfigLoaderService configLoaderService;

    @Autowired
    private DistributedTransactionExecutor executor;

    @Autowired
    private SequenceDiagramGenerator diagramGenerator;

    @Autowired
    private DistributedTransactionTestRepository testRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行测试场景
     * POST /api/distributed-transaction/execute
     */
    @PostMapping("/execute")
    public ResponseEntity<?> executeTest(@RequestBody ExecuteTestRequest request) {
        try {
            // 加载配置
            DistributedTransactionTestConfig config;
            if (request.getConfigPath() != null && !request.getConfigPath().isEmpty()) {
                // 从文件路径加载
                if (request.getConfigPath().startsWith("classpath:")) {
                    String classpathPath = request.getConfigPath().substring("classpath:".length());
                    config = configLoaderService.loadFromClasspath(classpathPath);
                } else {
                    config = configLoaderService.loadFromFile(request.getConfigPath());
                }
            } else if (request.getConfigYaml() != null && !request.getConfigYaml().isEmpty()) {
                // 从YAML字符串加载
                config = configLoaderService.loadFromYamlString(request.getConfigYaml());
            } else {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("必须提供configPath或configYaml"));
            }

            logger.info("开始执行测试场景: {}", config.getScenario().getName());

            // 执行测试
            List<TestExecutionResult> results = executor.execute(config);

            // 生成测试ID
            String testId = UUID.randomUUID().toString();

            // 保存结果到数据库
            DistributedTransactionTestRecord record = new DistributedTransactionTestRecord();
            record.setTestId(testId);
            record.setScenarioName(config.getScenario().getName());
            record.setResultJson(objectMapper.writeValueAsString(results));
            testRepository.save(record);

            // 生成时序图
            String diagram = diagramGenerator.generateSequenceDiagram(results);

            logger.info("测试执行完成: testId={}, 步骤数={}", testId, results.size());

            ExecuteTestResponse response = new ExecuteTestResponse();
            response.setTestId(testId);
            response.setScenarioName(config.getScenario().getName());
            response.setResults(results);
            response.setSequenceDiagram(diagram);
            response.setSuccessCount((int) results.stream()
                    .filter(r -> r.getStatus() == TestExecutionResult.ExecutionStatus.SUCCESS)
                    .count());
            response.setFailedCount((int) results.stream()
                    .filter(r -> r.getStatus() == TestExecutionResult.ExecutionStatus.FAILED)
                    .count());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("执行测试失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("执行测试失败: " + e.getMessage()));
        }
    }

    /**
     * 获取测试结果
     * GET /api/distributed-transaction/results/{testId}
     */
    @GetMapping("/results/{testId}")
    public ResponseEntity<?> getTestResults(@PathVariable String testId) {
        try {
            Optional<DistributedTransactionTestRecord> recordOpt = testRepository.findByTestId(testId);
            if (recordOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("测试记录不存在: " + testId));
            }

            DistributedTransactionTestRecord record = recordOpt.get();
            List<TestExecutionResult> results = Arrays.asList(
                    objectMapper.readValue(record.getResultJson(), TestExecutionResult[].class));

            TestResultsResponse response = new TestResultsResponse();
            response.setTestId(record.getTestId());
            response.setScenarioName(record.getScenarioName());
            response.setCreatedAt(record.getCreatedAt().toString());
            response.setResults(results);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取测试结果失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("获取测试结果失败: " + e.getMessage()));
        }
    }

    /**
     * 获取时序图
     * GET /api/distributed-transaction/diagram/{testId}
     */
    @GetMapping("/diagram/{testId}")
    public ResponseEntity<?> getSequenceDiagram(@PathVariable String testId) {
        try {
            Optional<DistributedTransactionTestRecord> recordOpt = testRepository.findByTestId(testId);
            if (recordOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("测试记录不存在: " + testId));
            }

            DistributedTransactionTestRecord record = recordOpt.get();
            List<TestExecutionResult> results = Arrays.asList(
                    objectMapper.readValue(record.getResultJson(), TestExecutionResult[].class));

            String diagram = diagramGenerator.generateSequenceDiagram(results);

            DiagramResponse response = new DiagramResponse();
            response.setTestId(record.getTestId());
            response.setScenarioName(record.getScenarioName());
            response.setMermaidDiagram(diagram);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("生成时序图失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("生成时序图失败: " + e.getMessage()));
        }
    }

    /**
     * 列出所有测试场景
     * GET /api/distributed-transaction/scenarios
     */
    @GetMapping("/scenarios")
    public ResponseEntity<?> listScenarios() {
        try {
            List<String> scenarios = configLoaderService.listAvailableScenarios();
            ScenariosResponse response = new ScenariosResponse();
            response.setScenarios(scenarios);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("列出测试场景失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("列出测试场景失败: " + e.getMessage()));
        }
    }

    /**
     * 列出所有测试记录
     * GET /api/distributed-transaction/records
     */
    @GetMapping("/records")
    public ResponseEntity<?> listRecords() {
        try {
            List<DistributedTransactionTestRecord> records = testRepository.findAllByOrderByCreatedAtDesc();
            List<RecordSummary> summaries = records.stream()
                    .map(record -> {
                        RecordSummary summary = new RecordSummary();
                        summary.setTestId(record.getTestId());
                        summary.setScenarioName(record.getScenarioName());
                        summary.setCreatedAt(record.getCreatedAt().toString());
                        return summary;
                    })
                    .collect(Collectors.toList());

            RecordsResponse response = new RecordsResponse();
            response.setRecords(summaries);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("列出测试记录失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("列出测试记录失败: " + e.getMessage()));
        }
    }

    // 请求和响应类
    public static class ExecuteTestRequest {
        private String configPath;
        private String configYaml;

        public String getConfigPath() {
            return configPath;
        }

        public void setConfigPath(String configPath) {
            this.configPath = configPath;
        }

        public String getConfigYaml() {
            return configYaml;
        }

        public void setConfigYaml(String configYaml) {
            this.configYaml = configYaml;
        }
    }

    public static class ExecuteTestResponse {
        private String testId;
        private String scenarioName;
        private List<TestExecutionResult> results;
        private String sequenceDiagram;
        private int successCount;
        private int failedCount;

        // Getters and Setters
        public String getTestId() { return testId; }
        public void setTestId(String testId) { this.testId = testId; }
        public String getScenarioName() { return scenarioName; }
        public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }
        public List<TestExecutionResult> getResults() { return results; }
        public void setResults(List<TestExecutionResult> results) { this.results = results; }
        public String getSequenceDiagram() { return sequenceDiagram; }
        public void setSequenceDiagram(String sequenceDiagram) { this.sequenceDiagram = sequenceDiagram; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    }

    public static class TestResultsResponse {
        private String testId;
        private String scenarioName;
        private String createdAt;
        private List<TestExecutionResult> results;

        // Getters and Setters
        public String getTestId() { return testId; }
        public void setTestId(String testId) { this.testId = testId; }
        public String getScenarioName() { return scenarioName; }
        public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public List<TestExecutionResult> getResults() { return results; }
        public void setResults(List<TestExecutionResult> results) { this.results = results; }
    }

    public static class DiagramResponse {
        private String testId;
        private String scenarioName;
        private String mermaidDiagram;

        // Getters and Setters
        public String getTestId() { return testId; }
        public void setTestId(String testId) { this.testId = testId; }
        public String getScenarioName() { return scenarioName; }
        public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }
        public String getMermaidDiagram() { return mermaidDiagram; }
        public void setMermaidDiagram(String mermaidDiagram) { this.mermaidDiagram = mermaidDiagram; }
    }

    public static class ScenariosResponse {
        private List<String> scenarios;

        public List<String> getScenarios() { return scenarios; }
        public void setScenarios(List<String> scenarios) { this.scenarios = scenarios; }
    }

    public static class RecordsResponse {
        private List<RecordSummary> records;

        public List<RecordSummary> getRecords() { return records; }
        public void setRecords(List<RecordSummary> records) { this.records = records; }
    }

    public static class RecordSummary {
        private String testId;
        private String scenarioName;
        private String createdAt;

        // Getters and Setters
        public String getTestId() { return testId; }
        public void setTestId(String testId) { this.testId = testId; }
        public String getScenarioName() { return scenarioName; }
        public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse(String message) {
            this.error = "ERROR";
            this.message = message;
        }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}

