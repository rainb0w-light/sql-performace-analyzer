package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.agent.AgentRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Optional AgentScope advisory pass over the already validated deterministic report.
 *
 * <p>The Agent receives the report (including EXPLAIN evidence), never target credentials. Its
 * output is stored in a separate advisory field and cannot replace deterministic risks,
 * recommendations or evidence. Disabled/unavailable/failed enhancement always returns the
 * baseline report with a visible status.
 */
@Component
public class StatementAgentEnhancer {

    private final ObjectProvider<AgentRuntime> runtimeProvider;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String modelName;
    private final int maxOutputChars;

    public StatementAgentEnhancer(ObjectProvider<AgentRuntime> runtimeProvider,
                                  ObjectMapper mapper,
                                  @Value("${sql-analyzer.analysis.agent-enhancement-enabled:false}")
                                  boolean enabled,
                                  @Value("${sql-analyzer.analysis.agent-enhancement-model:}")
                                  String modelName,
                                  @Value("${sql-analyzer.analysis.agent-enhancement-max-output-chars:20000}")
                                  int maxOutputChars) {
        this.runtimeProvider = runtimeProvider;
        this.mapper = mapper;
        this.enabled = enabled;
        this.modelName = modelName == null ? "" : modelName.trim();
        this.maxOutputChars = Math.max(1_000, maxOutputChars);
    }

    public String enhance(String clientId, String sessionId, String runId,
                          String datasourceProfileId, String baselineReportJson) {
        ObjectNode report = parseBaseline(baselineReportJson);
        if (!enabled) {
            apply(report, "SKIPPED", null, null, "AgentScope 增强未启用。");
            return report.toString();
        }

        AgentRuntime runtime = runtimeProvider.getIfAvailable();
        if (runtime == null) {
            apply(report, "SKIPPED", null, null, "AgentScope Runtime 不可用。");
            addLimit(report, "AgentScope 增强不可用；确定性报告不受影响。");
            return report.toString();
        }

        try {
            String prompt = """
                    你是 SQL 性能分析报告的审阅 Agent。下面是已经通过 Schema 校验的确定性报告，
                    其中可能包含普通只读 EXPLAIN 证据。

                    约束：
                    1. 只基于报告中已有的 BoundSql、画像、索引、分片和 EXPLAIN 证据做补充判断。
                    2. 不连接数据库、不调用工具、不执行 SQL、DDL 或 EXPLAIN ANALYZE。
                    3. 不覆盖确定性结论；证据不足时必须明确说明。
                    4. 输出简洁 JSON，格式为 {"summary":"...","observations":["..."]}。

                    确定性报告：
                    """ + baselineReportJson;
            AgentRuntime.AgentOutput output = runtime.execute(new AgentRuntime.AgentExecutionRequest(
                    clientId, sessionId, runId, prompt,
                    modelName.isBlank() ? null : modelName, List.of(),
                    Map.of("datasourceProfileId",
                            datasourceProfileId == null ? "" : datasourceProfileId)));
            if (output == null || !output.success() || output.report() == null
                    || output.report().isBlank()) {
                String error = output == null ? "Agent 无响应"
                        : safe(output.report(), 1_000);
                apply(report, "FAILED", null, error, "AgentScope 增强失败。");
                addLimit(report, "AgentScope 增强失败：" + error + "；确定性报告已保留。");
            } else {
                apply(report, "COMPLETED", safe(output.report(), maxOutputChars),
                        null, null);
                ObjectNode audit = report.with("audit");
                audit.put("model", modelName.isBlank()
                        ? "deterministic-analysis+agent:default"
                        : "deterministic-analysis+agent:" + modelName);
            }
        } catch (RuntimeException failure) {
            String error = safe(failure.getMessage(), 1_000);
            apply(report, "FAILED", null, error, "AgentScope 增强失败。");
            addLimit(report, "AgentScope 增强失败：" + error + "；确定性报告已保留。");
        }
        return report.toString();
    }

    public boolean enabled() {
        return enabled;
    }

    private ObjectNode parseBaseline(String json) {
        try {
            return (ObjectNode) mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("确定性报告不是 JSON object", e);
        }
    }

    private void apply(ObjectNode report, String status, String content, String error, String reason) {
        ObjectNode node = report.putObject("agentEnhancement");
        node.put("status", status);
        node.put("model", modelName.isBlank() ? null : modelName);
        node.put("content", content);
        node.put("error", error);
        node.put("reason", reason);
        node.put("generatedAt", Instant.now().toString());
    }

    private void addLimit(ObjectNode report, String message) {
        report.with("limits").withArray("notes").add(message);
    }

    private static String safe(String value, int maxChars) {
        if (value == null || value.isBlank()) return "AgentScope 增强不可用";
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
