package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog;
import com.biz.sccba.sqlanalyzer.mybatis.MyBatisStatementRuntime;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamInfo;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves what a MyBatis statement references — tables, parameters, ${} interpolation points —
 * through the official runtime only (docs/cloud-code-next-goal.md §4/§6): a probe binding with
 * every referenced name set non-null expands all optional branches via
 * {@code MappedStatement.getBoundSql}, then tables are read off the bound SQL text and parameter
 * kinds are inferred structurally. No condition is ever evaluated by this class.
 */
@Component
public class StatementReferenceResolver {

    private static final Pattern TABLE_REF = Pattern.compile(
            "(?is)(?:from|join|into|update)\\s+([a-z_][a-z0-9_]*)");
    private static final Pattern HASH_INTERPOLATION = Pattern.compile("#\\{([^}]+)}");
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "select", "where", "set", "values", "dual", "lateral", "unnest");

    private final DynamicNodeCatalog catalog;

    public StatementReferenceResolver(DynamicNodeCatalog catalog) {
        this.catalog = catalog;
    }

    public record References(String namespace, String statementId, String statementType,
                             List<String> tables, Map<String, ParamInfo> parameters,
                             List<String> dollarExpressions) {}

    public References resolve(byte[] mapperXml, String resource, String statementId,
                              String mybatisConfigXml, String databaseId) {
        DynamicNodeCatalog.MapperStructure structure = catalog.scan(new String(mapperXml, java.nio.charset.StandardCharsets.UTF_8));
        String namespace = structure.namespace();
        DynamicNodeCatalog.StatementStructure statement = structure.statements().stream()
                .filter(s -> s.statementId().equals(statementId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("statement 不存在：" + statementId));

        // Parameter names referenced by dynamic nodes (structural scan).
        Set<String> names = new LinkedHashSet<>();
        Set<String> collectionNames = new LinkedHashSet<>();
        for (var node : statement.nodes()) {
            names.addAll(node.referencedNames());
            if (node.type().equals("foreach")) {
                String collection = node.attributes().getOrDefault("collection", "list");
                collectionNames.add(collection);
                names.add(collection);
            }
        }
        for (String dollar : statement.dollarExpressions()) {
            names.add(topLevel(dollar));
        }
        Matcher hash = HASH_INTERPOLATION.matcher(statement.rawDynamicSql());
        while (hash.find()) {
            names.add(topLevel(hash.group(1)));
        }

        // Probe-bind through the official runtime so every optional branch expands, then read
        // tables off the resulting SQL.
        Map<String, Object> probeParams = new LinkedHashMap<>();
        for (String name : names) {
            probeParams.put(name, collectionNames.contains(name) ? List.of("probe") : probeValue(name));
        }
        List<String> tables = new ArrayList<>();
        try {
            MyBatisStatementRuntime runtime = new MyBatisStatementRuntime(mybatisConfigXml, databaseId);
            var configuration = runtime.loadConfiguration(mapperXml, resource);
            String qualifiedId = statementId.contains(".") ? statementId : namespace + "." + statementId;
            var bound = runtime.bind(configuration, qualifiedId, probeParams);
            if (!bound.isUnsupported() && bound.sql() != null) {
                Matcher m = TABLE_REF.matcher(bound.sql());
                while (m.find()) {
                    String table = m.group(1).toLowerCase(Locale.ROOT);
                    if (!SQL_KEYWORDS.contains(table) && !tables.contains(table)) {
                        tables.add(table);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // unresolved references leave the table list partial; limits record it downstream
        }

        Map<String, ParamInfo> parameters = new LinkedHashMap<>();
        for (String name : names) {
            ParamKind kind = collectionNames.contains(name) ? ParamKind.LIST : inferKind(name);
            parameters.put(name, new ParamInfo(name, kind, true));
        }
        return new References(namespace, statementId, statement.statementType(),
                tables, parameters, statement.dollarExpressions());
    }

    private static Object probeValue(String name) {
        return switch (inferKind(name)) {
            case LONG, INT -> 1;
            case DOUBLE -> 1.0;
            case BOOLEAN -> true;
            case DATE -> "2026-01-01T00:00:00Z";
            default -> "probe";
        };
    }

    private static ParamKind inferKind(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("asof") || lower.endsWith("as_of") || lower.contains("asof")) {
            return ParamKind.DATE;
        }
        if (lower.endsWith("id") && !lower.equals("id")) return ParamKind.LONG;
        if (lower.endsWith("at") || lower.endsWith("time") || lower.endsWith("from")
                || lower.endsWith("to") || lower.endsWith("before") || lower.contains("date")) {
            return ParamKind.DATE;
        }
        if (lower.endsWith("ids") || lower.endsWith("list") || lower.endsWith("statuses")
                || lower.endsWith("categories")) {
            return ParamKind.LIST;
        }
        if (lower.endsWith("count") || lower.endsWith("limit") || lower.endsWith("size")
                || lower.endsWith("priority")) {
            return ParamKind.INT;
        }
        return ParamKind.STRING;
    }

    private static String topLevel(String expression) {
        String trimmed = expression.trim();
        int dot = trimmed.indexOf('.');
        int bracket = trimmed.indexOf('[');
        int cut = trimmed.length();
        if (dot > 0) cut = Math.min(cut, dot);
        if (bracket > 0) cut = Math.min(cut, bracket);
        return trimmed.substring(0, cut);
    }
}
