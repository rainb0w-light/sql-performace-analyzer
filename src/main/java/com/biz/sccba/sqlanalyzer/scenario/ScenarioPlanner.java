package com.biz.sccba.sqlanalyzer.scenario;

import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog.NodeInfo;
import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog.StatementStructure;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ColumnKnowledge;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ColumnProfile;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamInfo;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamKind;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterScenario;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParameterSource;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ShardInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * Business-semantics-driven scenario generation (development-guide §6.3). The planner only
 * chooses parameter objects and coverage targets; the resulting SQL always comes from
 * {@code MappedStatement.getBoundSql}. Combinations are compressed by greedy coverage-goal
 * selection (a covering-array strategy) instead of an unbounded 2^N cartesian product, and the
 * total is capped (default 20 per statement) keeping, in priority order: business main path,
 * branch coverage, foreach classes, index/shard-sensitive paths and high-risk paths.
 */
@Component
public class ScenarioPlanner {

    public static final int DEFAULT_MAX_SCENARIOS = 20;
    private static final int LARGE_FOREACH_REPRESENTATIVE_SIZE = 20;

    public List<ParameterScenario> plan(StatementStructure structure, PlannerInput input) {
        int cap = input.maxScenarios() <= 0 ? DEFAULT_MAX_SCENARIOS : input.maxScenarios();
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Object> baseline = baselineParameters(input);

        // 1. Business main path (priority 0).
        candidates.add(candidate("main-path", "业务主路径", "必填参数齐备的典型业务调用",
                ParameterSource.RULE_INFERRED, baseline, branchesAllTrue(structure),
                nodesOfType(structure, "if", "when"), goals(structure, "IF_TRUE", "WHEN"), 0.9, input, 0));

        // 2. User-provided samples (priority 1).
        int sampleIdx = 0;
        for (Map<String, Object> sample : input.userSamples() == null ? List.<Map<String, Object>>of() : input.userSamples()) {
            Map<String, Object> params = mergeOver(baseline, sample);
            candidates.add(candidate("user-sample-" + sampleIdx, "用户样例 " + (sampleIdx + 1),
                    "用户手工提供或保存过的参数样例", ParameterSource.USER, params,
                    List.of(), List.of(), List.of(), 1.0, input, 1));
            sampleIdx++;
        }

        // 3. ${} whitelist scenarios (priority 5): interpolated values from explicit enums only.
        for (String expr : structure.dollarExpressions()) {
            String name = topLevelName(expr);
            ColumnKnowledge knowledge = knowledgeFor(input, name);
            if (knowledge != null && !knowledge.frequentValues().isEmpty()) {
                Map<String, Object> params = copy(baseline);
                setPath(params, name, knowledge.frequentValues().get(0));
                candidates.add(candidate("dollar-whitelist-" + name, "${" + name + "} 白名单取值",
                        "${} 注入点仅取显式白名单值（" + knowledge.frequentValues().get(0) + "）",
                        ParameterSource.RULE_INFERRED, params, List.of(), List.of(), List.of(), 0.85, input, 5));
            }
        }

        // 4. foreach size classes (priority 10-13). Items use element-level defaults (profile
        // top-K values when available), never the collection-typed default (which would nest a
        // list inside the list and break the item type handler).
        for (NodeInfo foreach : byType(structure, "foreach")) {
            String collection = foreach.attributes().getOrDefault("collection", "list");
            for (int cls = 0; cls < 4; cls++) {
                String label = List.of("空集合", "单元素", "典型多元素", "受控大列表").get(cls);
                String goal = "FOREACH_" + List.of("EMPTY", "SINGLE", "MULTI", "LARGE").get(cls) + "@" + foreach.nodeId();
                Map<String, Object> params = copy(baseline);
                List<Object> values = switch (cls) {
                    case 0 -> List.of();
                    case 1 -> List.of(foreachItem(input, collection, 1));
                    case 2 -> List.of(foreachItem(input, collection, 1), foreachItem(input, collection, 2),
                            foreachItem(input, collection, 3));
                    default -> representativeLarge(input, collection);
                };
                setPath(params, collection, values);
                candidates.add(candidate("foreach-" + collection + "-" + cls, "foreach(" + collection + ") " + label,
                        "foreach 集合规模场景：" + label + "（不复制真实大集合）", ParameterSource.BOUNDARY_GENERATED,
                        params, List.of(), List.of(foreach.nodeId()), List.of(goal), 0.8, input, 10 + cls));
            }
        }

        // 5. Shard-sensitive paths (priority 12-13). Shard keys are DB column names; the mapper
        // parameters carrying them are matched by name stem (member_id -> memberId;
        // borrowed_at -> borrowedFrom/borrowedTo range params), so the scenarios bind to real
        // parameters and survive BoundSql fingerprint dedup.
        for (ShardInfo shard : input.shards() == null ? List.<ShardInfo>of() : input.shards()) {
            if (shard.shardKey() != null && !shard.shardKey().isBlank()) {
                List<String> primaryParams = paramsForColumn(input, shard.shardKey());
                if (!primaryParams.isEmpty()) {
                    Map<String, Object> single = copy(baseline);
                    for (String p : primaryParams) setPath(single, p, typedDefault(input, p, 7));
                    candidates.add(candidate("shard-single", "命中单分片",
                            "分片键 " + shard.shardKey() + " 取确定值，路由到单分片", ParameterSource.RULE_INFERRED,
                            single, List.of(), List.of(), List.of("SHARD_SINGLE"), 0.85, input, 12));

                    Map<String, Object> cross = copy(baseline);
                    for (String p : primaryParams) setPath(cross, p, null);
                    candidates.add(candidate("shard-cross", "跨分片扫描",
                            "分片键 " + shard.shardKey() + " 缺失，可能触发跨分片扫描", ParameterSource.BOUNDARY_GENERATED,
                            cross, List.of(), List.of(), List.of("SHARD_CROSS"), 0.85, input, 12));
                }
            }
            if (shard.secondaryShardKey() != null && !shard.secondaryShardKey().isBlank()) {
                List<String> secondaryParams = paramsForColumn(input, shard.secondaryShardKey());
                if (!secondaryParams.isEmpty()) {
                    Map<String, Object> missingSecondary = copy(baseline);
                    for (String p : secondaryParams) setPath(missingSecondary, p, null);
                    candidates.add(candidate("shard-secondary-missing", "二级分片时间范围缺失",
                            "二级分片键 " + shard.secondaryShardKey() + " 对应参数缺失，扩大时间分区扫描",
                            ParameterSource.BOUNDARY_GENERATED,
                            missingSecondary, List.of(), List.of(), List.of("SHARD_SECONDARY_MISSING"), 0.8, input, 13));
                }
            }
        }

        // 6. choose arms: each when + otherwise (priority 15-16).
        for (NodeInfo choose : byType(structure, "choose")) {
            List<NodeInfo> whens = children(structure, choose, "when");
            NodeInfo otherwise = firstChild(structure, choose, "otherwise");
            for (int i = 0; i < whens.size(); i++) {
                NodeInfo when = whens.get(i);
                Map<String, Object> params = copy(baseline);
                // make this when true, prior whens false
                for (int j = 0; j < i; j++) {
                    for (String ref : whens.get(j).referencedNames()) setPath(params, topLevelName(ref), null);
                }
                for (String ref : when.referencedNames()) {
                    setPath(params, topLevelName(ref), typedDefault(input, topLevelName(ref), 1));
                }
                String goal = "WHEN_ARM@" + when.nodeId();
                candidates.add(candidate("when-" + i, "choose 分支 " + (i + 1),
                        "choose 第 " + (i + 1) + " 个 when 命中：" + when.test(), ParameterSource.BOUNDARY_GENERATED,
                        params, List.of(when.nodeId() + ":true"), List.of(when.nodeId()), List.of(goal), 0.8, input, 15));
            }
            if (otherwise != null) {
                Map<String, Object> params = copy(baseline);
                for (NodeInfo when : whens) {
                    for (String ref : when.referencedNames()) setPath(params, topLevelName(ref), null);
                }
                candidates.add(candidate("otherwise", "choose otherwise 分支",
                        "所有 when 均不命中，走 otherwise", ParameterSource.BOUNDARY_GENERATED,
                        params, List.of(otherwise.nodeId() + ":true"), List.of(otherwise.nodeId()),
                        List.of("WHEN_ARM@" + otherwise.nodeId()), 0.8, input, 16));
            }
        }

        // 7. <if> true/false coverage (priority 20).
        for (NodeInfo ifNode : byType(structure, "if")) {
            for (boolean value : new boolean[] { true, false }) {
                Map<String, Object> params = copy(baseline);
                List<String> refs = ifNode.referencedNames().isEmpty()
                        ? List.of() : List.of(topLevelName(ifNode.referencedNames().get(0)));
                for (String ref : refs) {
                    setPath(params, ref, value ? typedDefault(input, ref, 1) : null);
                }
                String goal = "IF_" + (value ? "TRUE" : "FALSE") + "@" + ifNode.nodeId();
                candidates.add(candidate("if-" + (value ? "t-" : "f-") + shortId(ifNode),
                        (value ? "条件成立：" : "条件不成立：") + ifNode.test(),
                        "动态条件 " + (value ? "true" : "false") + " 覆盖：" + ifNode.test(),
                        ParameterSource.BOUNDARY_GENERATED, params,
                        List.of(ifNode.nodeId() + ":" + value), List.of(ifNode.nodeId()), List.of(goal), 0.8, input, 20));
            }
        }

        // 8. Enum value classes (priority 25). When the target parameter is a collection
        // (e.g. statuses <foreach>), the enum value is wrapped as a single-element list so the
        // mapper's size() checks and IN clause bind correctly.
        for (ColumnKnowledge k : input.knowledge() == null ? List.<ColumnKnowledge>of() : input.knowledge()) {
            for (int cls = 0; cls < 3; cls++) {
                String value = switch (cls) {
                    case 0 -> k.frequentValues().isEmpty() ? null : k.frequentValues().get(0);
                    case 1 -> k.rareValues().isEmpty() ? null : k.rareValues().get(0);
                    default -> "__UNKNOWN_ENUM__";
                };
                if (value == null) continue;
                Map<String, Object> params = copy(baseline);
                ParamInfo target = input.parameters().get(k.columnName());
                Object enumValue = target != null && target.kind() == ParamKind.LIST ? List.of(value) : value;
                setPath(params, k.columnName(), enumValue);
                String label = List.of("高频枚举值", "低频枚举值", "未知枚举值").get(cls);
                candidates.add(candidate("enum-" + k.columnName() + "-" + cls, "枚举(" + k.columnName() + ") " + label,
                        "枚举字段 " + k.columnName() + " 场景：" + label + " " + value,
                        cls == 2 ? ParameterSource.BOUNDARY_GENERATED : ParameterSource.EXCEL,
                        params, List.of(), List.of(), List.of("ENUM_" + List.of("HIGH", "LOW", "UNKNOWN").get(cls) + "@" + k.columnName()),
                        0.8, input, 25));
            }
        }

        // 9. Range boundaries from profiles (priority 30).
        for (ColumnProfile p : input.profiles() == null ? List.<ColumnProfile>of() : input.profiles()) {
            List<Object> values = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            if (p.min() != null) { values.add(p.min()); labels.add("MIN"); }
            if (p.quantiles() != null && p.quantiles().size() >= 2) {
                values.add(p.quantiles().get(p.quantiles().size() / 2)); labels.add("P50");
            }
            if (p.max() != null) { values.add(p.max()); labels.add("MAX"); }
            if (p.max() != null) { values.add(outOfRange(p.max())); labels.add("OUT_OF_RANGE"); }
            for (int i = 0; i < values.size(); i++) {
                Map<String, Object> params = copy(baseline);
                setPath(params, p.columnName(), values.get(i));
                candidates.add(candidate("range-" + p.columnName() + "-" + labels.get(i),
                        "范围(" + p.columnName() + ") " + labels.get(i),
                        "范围字段 " + p.columnName() + " 边界场景 " + labels.get(i) + "，来自画像快照",
                        ParameterSource.PROFILE, params, List.of(), List.of(),
                        List.of("RANGE_" + labels.get(i) + "@" + p.columnName()), 0.75, input, 30));
            }
        }

        return select(candidates, cap);
    }

    // ---- compression: greedy coverage-goal selection, then priority fill ----

    private List<ParameterScenario> select(List<Candidate> candidates, int cap) {
        Set<String> allGoals = new LinkedHashSet<>();
        for (Candidate c : candidates) allGoals.addAll(c.scenario.coverageGoals());

        List<Candidate> selected = new ArrayList<>();
        Set<String> covered = new LinkedHashSet<>();
        List<Candidate> pool = new ArrayList<>(candidates);

        // Reserved slots: business main path, user samples and ${} whitelist paths (priority <= 5)
        // are always kept before goal-driven compression.
        List<Candidate> mandatory = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.scenario.priority() <= 5) mandatory.add(c);
        }
        mandatory.sort(Comparator.comparingInt(c -> c.scenario.priority()));
        for (Candidate c : mandatory) {
            if (selected.size() >= cap) break;
            selected.add(c);
            covered.addAll(c.scenario.coverageGoals());
            pool.remove(c);
        }

        while (selected.size() < cap && !pool.isEmpty()) {
            Candidate best = null;
            int bestNew = -1;
            for (Candidate c : pool) {
                int fresh = 0;
                for (String g : c.scenario.coverageGoals()) if (!covered.contains(g)) fresh++;
                if (best == null || fresh > bestNew
                        || (fresh == bestNew && c.scenario.priority() < best.scenario.priority())) {
                    best = c;
                    bestNew = fresh;
                }
            }
            if (best == null) break;
            if (bestNew == 0 && covered.containsAll(allGoals)) {
                // goals exhausted: fill remaining capacity in priority order (high-risk paths)
                pool.sort(Comparator.comparingInt(c -> c.scenario.priority()));
                for (Candidate c : pool) {
                    if (selected.size() >= cap) break;
                    selected.add(c);
                }
                break;
            }
            selected.add(best);
            covered.addAll(best.scenario.coverageGoals());
            pool.remove(best);
        }
        return selected.stream().map(c -> c.scenario).toList();
    }

    private record Candidate(ParameterScenario scenario) {}

    private Candidate candidate(String key, String name, String description, ParameterSource source,
                                Map<String, Object> params, List<String> expectedBranches,
                                List<String> coverageNodes, List<String> goals, double confidence,
                                PlannerInput input, int priority) {
        return new Candidate(new ParameterScenario(stableScenarioId(key, params, input),
                name, description, source, params, expectedBranches, coverageNodes, goals, confidence,
                input.knowledgeVersion(), input.profileSnapshotId(), priority));
    }

    private static String stableScenarioId(String key, Map<String, Object> params, PlannerInput input) {
        String material = String.join("|", key, canonical(params),
                String.valueOf(input.contentHash()), String.valueOf(input.knowledgeVersion()),
                String.valueOf(input.profileSnapshotId()));
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
            return "scn_" + key.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + hash;
        } catch (Exception e) {
            return "scn_" + key.replaceAll("[^A-Za-z0-9_-]", "_")
                    + "_" + Integer.toHexString(material.hashCode());
        }
    }

    private static String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), item));
            return sorted.entrySet().stream()
                    .map(entry -> entry.getKey() + ":" + canonical(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> items = new ArrayList<>();
            iterable.forEach(item -> items.add(canonical(item)));
            return String.join(",", items);
        }
        return value.getClass().getName() + ":" + value;
    }

    // ---- parameter construction ----

    private Map<String, Object> baselineParameters(PlannerInput input) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, ParamInfo> entry : input.parameters().entrySet()) {
            setPath(params, entry.getKey(), defaultValue(entry.getValue().kind(), entry.getKey(), 1, input));
        }
        // profile top-K first value as a typical value for known columns
        for (ColumnProfile p : input.profiles() == null ? List.<ColumnProfile>of() : input.profiles()) {
            if (p.topK() != null && !p.topK().isEmpty() && !params.containsKey(p.columnName())) {
                setPath(params, p.columnName(), p.topK().get(0));
            }
        }
        return params;
    }

    private Object typedDefault(PlannerInput input, String name, int variant) {
        ParamInfo info = input.parameters().get(name);
        return defaultValue(info == null ? ParamKind.UNKNOWN : info.kind(), name, variant, input);
    }

    private Object defaultValue(ParamKind kind, String name, int variant, PlannerInput input) {
        return switch (kind) {
            case INT -> variant;
            case LONG -> (long) variant;
            case DOUBLE -> variant * 1.5;
            case BOOLEAN -> variant % 2 == 1;
            case DATE -> "2026-01-0" + Math.max(1, variant % 9);
            case LIST -> List.of("item_" + variant);
            case OBJECT -> Map.of();
            case STRING, UNKNOWN -> {
                ColumnProfile profile = profileFor(input, name);
                if (profile != null && profile.topK() != null && !profile.topK().isEmpty()) {
                    yield profile.topK().get(Math.min(variant - 1, profile.topK().size() - 1));
                }
                yield "value_" + name + "_" + variant;
            }
        };
    }

    private List<Object> representativeLarge(PlannerInput input, String collection) {
        List<Object> values = new ArrayList<>();
        for (int i = 1; i <= LARGE_FOREACH_REPRESENTATIVE_SIZE; i++) {
            values.add(foreachItem(input, collection, i));
        }
        return values;
    }

    /** Element-level default for foreach items: profile top-K values first, else typed defaults. */
    private Object foreachItem(PlannerInput input, String collection, int variant) {
        ColumnProfile profile = profileFor(input, collection);
        if (profile != null && profile.topK() != null && !profile.topK().isEmpty()) {
            return profile.topK().get((variant - 1) % profile.topK().size());
        }
        ParamInfo info = input.parameters().get(collection);
        ParamKind elementKind = info != null && info.kind() == ParamKind.LIST ? ParamKind.STRING : info == null ? ParamKind.STRING : info.kind();
        return defaultValue(elementKind, collection, variant, input);
    }

    private static Object outOfRange(String max) {
        try {
            double v = Double.parseDouble(max);
            return v + Math.max(1.0, Math.abs(v));
        } catch (NumberFormatException e) {
            return max + "~OUT_OF_RANGE";
        }
    }

    // ---- structure helpers ----

    private List<String> branchesAllTrue(StatementStructure structure) {
        List<String> branches = new ArrayList<>();
        for (NodeInfo node : structure.nodes()) {
            if (node.type().equals("if") || node.type().equals("when")) {
                branches.add(node.nodeId() + ":true");
            }
        }
        return branches;
    }

    private List<String> nodesOfType(StatementStructure structure, String... types) {
        List<String> ids = new ArrayList<>();
        for (NodeInfo node : structure.nodes()) {
            for (String t : types) if (node.type().equals(t)) ids.add(node.nodeId());
        }
        return ids;
    }

    private List<NodeInfo> byType(StatementStructure structure, String type) {
        return structure.nodes().stream().filter(n -> n.type().equals(type)).toList();
    }

    private List<NodeInfo> children(StatementStructure structure, NodeInfo parent, String type) {
        List<NodeInfo> out = new ArrayList<>();
        for (String id : parent.childrenIds()) {
            structure.nodes().stream().filter(n -> n.nodeId().equals(id) && n.type().equals(type)).findFirst()
                    .ifPresent(out::add);
        }
        return out;
    }

    private NodeInfo firstChild(StatementStructure structure, NodeInfo parent, String type) {
        List<NodeInfo> found = children(structure, parent, type);
        return found.isEmpty() ? null : found.get(0);
    }

    private List<String> goals(StatementStructure structure, String... goalPrefixes) {
        List<String> out = new ArrayList<>();
        for (NodeInfo node : structure.nodes()) {
            for (String prefix : goalPrefixes) {
                if (node.type().equalsIgnoreCase(prefix.split("_")[0])) {
                    out.add(prefix + "@" + node.nodeId());
                }
            }
        }
        return out;
    }

    private ColumnKnowledge knowledgeFor(PlannerInput input, String name) {
        for (ColumnKnowledge k : input.knowledge() == null ? List.<ColumnKnowledge>of() : input.knowledge()) {
            if (k.columnName().equals(name)) return k;
        }
        return null;
    }

    private ColumnProfile profileFor(PlannerInput input, String name) {
        for (ColumnProfile p : input.profiles() == null ? List.<ColumnProfile>of() : input.profiles()) {
            if (p.columnName().equals(name)) return p;
        }
        return null;
    }

    // ---- path/value utilities ----

    static void setPath(Map<String, Object> params, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = params;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                Map<String, Object> child = new LinkedHashMap<>();
                current.put(parts[i], child);
                current = child;
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) next;
                current = child;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    static Map<String, Object> copy(Map<String, Object> source) {
        return deepCopy(source);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : source.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map) {
                out.put(e.getKey(), deepCopy((Map<String, Object>) v));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    private static Map<String, Object> mergeOver(Map<String, Object> baseline, Map<String, Object> sample) {
        Map<String, Object> out = copy(baseline);
        out.putAll(sample);
        return out;
    }

    /**
     * Maps a shard column name to the mapper parameters carrying it, by normalized name stem:
     * {@code member_id} matches {@code memberId}/{@code member_id}; {@code borrowed_at} matches
     * range parameters {@code borrowedFrom}/{@code borrowedTo}. Documented heuristic — exact
     * column-to-parameter knowledge comes from the Phase C context resolver.
     */
    private List<String> paramsForColumn(PlannerInput input, String column) {
        // stem on the RAW name (member_id -> "member", borrowed_at -> "borrowed"), then normalize
        String stem = normalize(column.contains("_")
                ? column.substring(0, column.lastIndexOf('_')) : column);
        String exact = normalize(column);
        List<String> out = new ArrayList<>();
        for (String name : input.parameters().keySet()) {
            String normalized = normalize(name);
            if (normalized.equals(exact) || normalized.startsWith(stem)) {
                out.add(name);
            }
        }
        return out;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String topLevelName(String expression) {
        String trimmed = expression.trim();
        int dot = trimmed.indexOf('.');
        int bracket = trimmed.indexOf('[');
        int cut = trimmed.length();
        if (dot > 0) cut = Math.min(cut, dot);
        if (bracket > 0) cut = Math.min(cut, bracket);
        return trimmed.substring(0, cut);
    }

    private static String shortId(NodeInfo node) {
        String id = node.nodeId().toLowerCase(Locale.ROOT);
        return id.length() > 24 ? id.substring(id.length() - 24) : id;
    }
}
