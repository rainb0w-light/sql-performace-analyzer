package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.evidence.ReadOnlyEvidenceDao;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine.PlanResult;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.BoundScenario;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects ordinary, read-only EXPLAIN evidence for the official MyBatis BoundSql scenarios.
 *
 * <p>Only SELECT/WITH statements are eligible. Parameters remain separate from SQL and are bound
 * through JDBC; DML, unresolved parameters, ${} risk paths, missing credentials and target
 * failures are represented as visible report limitations rather than analysis failures.
 */
@Component
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ExecutionPlanCollector {

    private final ProfilingRepository profiling;
    private final ObjectProvider<ReadOnlyEvidenceDao> evidenceProvider;
    private final Environment environment;
    private final boolean enabled;

    public ExecutionPlanCollector(ProfilingRepository profiling,
                                  ObjectProvider<ReadOnlyEvidenceDao> evidenceProvider,
                                  Environment environment,
                                  @Value("${sql-analyzer.analysis.explain-enabled:false}") boolean enabled) {
        this.profiling = profiling;
        this.evidenceProvider = evidenceProvider;
        this.environment = environment;
        this.enabled = enabled;
    }

    public Collection collect(String clientId, String datasourceProfileId, String statementType,
                              PlanResult plan) {
        List<String> notes = new ArrayList<>();
        List<String> missingPermissions = new ArrayList<>();
        List<ExecutionPlan> plans = new ArrayList<>();

        String normalizedType = statementType == null ? "" : statementType.toUpperCase(java.util.Locale.ROOT);
        if (!"SELECT".equals(normalizedType)) {
            notes.add((normalizedType.isBlank() ? "非 SELECT" : normalizedType)
                    + " statement 仅执行静态只读分析；禁止 EXPLAIN ANALYZE，也不向目标库发送 DML。");
            return new Collection(List.of(), notes, List.of(), true);
        }
        if (!enabled) {
            notes.add("普通只读 EXPLAIN 未启用；确定性报告仅使用 Mapper、画像、索引和分片证据。");
            return new Collection(List.of(), notes, List.of(), true);
        }
        if (datasourceProfileId == null || datasourceProfileId.isBlank()) {
            missingPermissions.add("未绑定目标数据源，无法执行只读 EXPLAIN。");
            return new Collection(List.of(), notes, missingPermissions, true);
        }

        var profile = profiling.findProfile(datasourceProfileId, clientId).orElse(null);
        if (profile == null) {
            missingPermissions.add("目标数据源不存在或不属于当前客户端，无法执行只读 EXPLAIN。");
            return new Collection(List.of(), notes, missingPermissions, true);
        }
        if (!profile.readOnly()) {
            missingPermissions.add("目标数据源未标记为只读，拒绝执行 EXPLAIN。");
            return new Collection(List.of(), notes, missingPermissions, true);
        }

        ReadOnlyEvidenceDao evidenceDao = evidenceProvider.getIfAvailable();
        if (evidenceDao == null) {
            missingPermissions.add("只读数据库证据适配器不可用，已跳过 EXPLAIN。");
            return new Collection(List.of(), notes, missingPermissions, true);
        }

        Map<String, String> target = new LinkedHashMap<>();
        target.put("datasourceProfileId", profile.id());
        target.put("dialect", profile.dialect());
        target.put("jdbcUrl", profile.jdbcUrl());
        target.put("username", profile.username() == null ? "" : profile.username());
        if (profile.credentialEnv() != null && !profile.credentialEnv().isBlank()) {
            String password = environment.getProperty(profile.credentialEnv());
            if (password == null) {
                missingPermissions.add("目标数据源凭据配置 " + profile.credentialEnv()
                        + " 不存在，已跳过 EXPLAIN。");
                return new Collection(List.of(), notes, missingPermissions, true);
            }
            target.put("password", password);
        } else {
            target.put("password", "");
        }

        for (BoundScenario scenario : plan.scenarios()) {
            if (scenario.isUnsupported() || scenario.boundSql() == null) continue;
            if (scenario.hasDollarInterpolation()) {
                notes.add("场景 " + scenario.scenario().scenarioId()
                        + " 包含未受信任的 ${} 插值，安全策略禁止向目标库发送 EXPLAIN。");
                continue;
            }
            ArgumentResolution arguments = arguments(scenario);
            if (!arguments.success()) {
                notes.add("场景 " + scenario.scenario().scenarioId() + " 未执行 EXPLAIN："
                        + arguments.error());
                continue;
            }

            ReadOnlyEvidenceDao.Evidence evidence;
            try {
                evidence = evidenceDao.explain(scenario.boundSql(), arguments.values(), target);
            } catch (RuntimeException failure) {
                evidence = new ReadOnlyEvidenceDao.Evidence(
                        "EXPLAIN_PLAN", false, "{}", publicError(failure.getMessage()));
            }
            if (evidence.success()) {
                Instant collectedAt = Instant.now();
                String evidenceId = "ev_explain_" + digest(profile.id() + ":"
                        + scenario.sqlFingerprint() + ":" + scenario.scenario().scenarioId());
                plans.add(new ExecutionPlan(scenario.scenario().scenarioId(), scenario.sqlFingerprint(),
                        evidenceId, profile.id(), evidence.payload(), collectedAt, 0.98));
            } else {
                missingPermissions.add("场景 " + scenario.scenario().scenarioId()
                        + " 的 EXPLAIN 不可用：" + publicError(evidence.error()));
            }
        }

        if (plans.isEmpty() && missingPermissions.isEmpty() && notes.isEmpty()) {
            notes.add("没有可安全绑定并执行 EXPLAIN 的 SELECT 场景。");
        }
        return new Collection(List.copyOf(plans), List.copyOf(notes),
                List.copyOf(missingPermissions), plans.isEmpty());
    }

    private ArgumentResolution arguments(BoundScenario scenario) {
        List<Object> values = new ArrayList<>();
        if (scenario.parameterMappings() == null) return new ArgumentResolution(true, values, null);
        for (var mapping : scenario.parameterMappings()) {
            if ("OUT".equalsIgnoreCase(mapping.mode())) continue;
            Resolution resolved = resolve(mapping.property(), scenario.additionalParameters(),
                    scenario.scenario().parameters());
            if (!resolved.found()) {
                return new ArgumentResolution(false, List.of(),
                        "无法解析 MyBatis 参数 " + mapping.property());
            }
            values.add(resolved.value());
        }
        return new ArgumentResolution(true, values, null);
    }

    private static Resolution resolve(String property, Map<String, Object> additional,
                                      Map<String, Object> parameters) {
        if (property == null || property.isBlank()) return new Resolution(null, false);
        if (additional != null && additional.containsKey(property)) {
            return new Resolution(additional.get(property), true);
        }
        Resolution fromAdditional = metaValue(additional, property);
        if (fromAdditional.found()) return fromAdditional;
        if (parameters != null && parameters.containsKey(property)) {
            return new Resolution(parameters.get(property), true);
        }
        return metaValue(parameters, property);
    }

    private static Resolution metaValue(Object source, String property) {
        if (source == null) return new Resolution(null, false);
        try {
            MetaObject meta = SystemMetaObject.forObject(source);
            if (!meta.hasGetter(property)) return new Resolution(null, false);
            return new Resolution(meta.getValue(property), true);
        } catch (RuntimeException ignored) {
            return new Resolution(null, false);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 20);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String publicError(String value) {
        if (value == null || value.isBlank()) return "目标库拒绝或无法执行普通只读 EXPLAIN";
        return value;
    }

    private record Resolution(Object value, boolean found) {}

    private record ArgumentResolution(boolean success, List<Object> values, String error) {}

    public record ExecutionPlan(String scenarioId, String sqlFingerprint, String evidenceId,
                                String datasourceProfileId, String plan, Instant collectedAt,
                                double confidence) {}

    public record Collection(List<ExecutionPlan> plans, List<String> notes,
                             List<String> missingPermissions, boolean explainSkipped) {
        public static Collection skipped(String note) {
            return new Collection(List.of(), note == null ? List.of() : List.of(note),
                    List.of(), true);
        }
    }
}
