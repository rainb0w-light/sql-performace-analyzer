package com.biz.sccba.sqlanalyzer.scenario;

import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog;
import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog.StatementStructure;
import com.biz.sccba.sqlanalyzer.mybatis.MyBatisStatementRuntime;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.BoundScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * End-to-end scenario materialization (development-guide §6.2/§6.3):
 * planner output → official {@code MappedStatement.getBoundSql} per scenario → whitespace
 * normalization + SQL-fingerprint deduplication (semantics-preserving) → forced risk flag for
 * {@code ${}} interpolation. Final SQL never leaves this path except verbatim from MyBatis.
 */
@Component
public class ScenarioEngine {

    private final DynamicNodeCatalog catalog;
    private final ScenarioPlanner planner;

    public ScenarioEngine(DynamicNodeCatalog catalog, ScenarioPlanner planner) {
        this.catalog = catalog;
        this.planner = planner;
    }

    public record PlanResult(String namespace, String statementId, List<BoundScenario> scenarios,
                             String loadError) {}

    public PlanResult plan(byte[] mapperXml, String resource, String statementId, PlannerInput input,
                           String mybatisConfigXml, String databaseId) {
        MyBatisStatementRuntime runtime = new MyBatisStatementRuntime(mybatisConfigXml, databaseId);
        MyBatisStatementRuntime.LoadedMapper loaded;
        try {
            loaded = runtime.load(mapperXml, resource);
        } catch (MyBatisStatementRuntime.MapperLoadException e) {
            return new PlanResult(null, statementId, List.of(), "UNSUPPORTED: " + e.getMessage());
        }

        String namespace = loaded.namespace();
        String qualifiedId = statementId.contains(".") ? statementId : namespace + "." + statementId;

        StatementStructure structure = null;
        try {
            var mapperStructure = catalog.scan(new String(mapperXml, StandardCharsets.UTF_8));
            for (StatementStructure s : mapperStructure.statements()) {
                if ((namespace + "." + s.statementId()).equals(qualifiedId)) {
                    structure = s;
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // structural scan is advisory; binding below remains authoritative
        }

        PlannerInput hashedInput = input.withContentHash(contentHash(mapperXml));
        List<ParameterScenario> scenarios = structure == null
                ? List.of() : planner.plan(structure, hashedInput);
        Set<String> dollarExpressions = structure == null ? Set.of()
                : new LinkedHashSet<>(structure.dollarExpressions());

        org.apache.ibatis.session.Configuration configuration =
                runtime.loadConfiguration(mapperXml, resource);

        List<BoundScenario> bound = new ArrayList<>();
        Map<String, BoundScenario> byFingerprint = new LinkedHashMap<>();
        for (ParameterScenario scenario : scenarios) {
            MyBatisStatementRuntime.BoundResult result = runtime.bind(configuration, qualifiedId, scenario.parameters());
            if (result.isUnsupported()) {
                bound.add(new BoundScenario(scenario, null, null, List.of(), Map.of(),
                        scenario.coverageNodeIds(), false, result.unsupported(), scenario.coverageGoals()));
                continue;
            }
            String fingerprint = fingerprint(result.sql());
            boolean dollarRisk = !dollarExpressions.isEmpty() && !whitelistedDollar(scenario, dollarExpressions);
            BoundScenario bs = new BoundScenario(scenario, result.sql(), fingerprint,
                    result.parameterMappings(), result.additionalParameters(),
                    scenario.coverageNodeIds(), dollarRisk, null, scenario.coverageGoals());
            BoundScenario existing = byFingerprint.get(fingerprint);
            if (existing == null) {
                byFingerprint.put(fingerprint, bs);
            } else {
                // merge coverage across scenarios that bind to the same SQL shape; prefer the
                // whitelisted provenance when one of the merged scenarios used an approved ${} value
                Set<String> merged = new LinkedHashSet<>(existing.coveredNodeIds());
                merged.addAll(bs.coveredNodeIds());
                Set<String> mergedGoals = new LinkedHashSet<>(existing.mergedCoverageGoals());
                mergedGoals.addAll(bs.mergedCoverageGoals());
                boolean preferIncoming = existing.hasDollarInterpolation() && !bs.hasDollarInterpolation();
                BoundScenario keep = preferIncoming ? bs : existing;
                byFingerprint.put(fingerprint, new BoundScenario(keep.scenario(), keep.boundSql(),
                        keep.sqlFingerprint(), keep.parameterMappings(), keep.additionalParameters(),
                        new ArrayList<>(merged), keep.hasDollarInterpolation(), null,
                        new ArrayList<>(mergedGoals)));
            }
        }
        bound.addAll(byFingerprint.values());

        int cap = input.maxScenarios() <= 0 ? ScenarioPlanner.DEFAULT_MAX_SCENARIOS : input.maxScenarios();
        if (bound.size() > cap) bound = bound.subList(0, cap);
        return new PlanResult(namespace, statementId, bound, null);
    }

    /** SQL fingerprint: whitespace-normalized, case-folded; placeholders preserved (no semantic change). */
    public static String fingerprint(String sql) {
        String normalized = sql == null ? "" : sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(normalized.hashCode());
        }
    }

    private static String contentHash(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            return Integer.toHexString(java.util.Arrays.hashCode(content));
        }
    }

    private static boolean whitelistedDollar(ParameterScenario scenario, Set<String> dollarExpressions) {
        // Only scenarios explicitly built for whitelist values may carry ${} without the risk flag.
        return scenario.name() != null && scenario.name().startsWith("${")
                && scenario.source() == ScenarioModels.ParameterSource.RULE_INFERRED
                && !dollarExpressions.isEmpty();
    }
}
