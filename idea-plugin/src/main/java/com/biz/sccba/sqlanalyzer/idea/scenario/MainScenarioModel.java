package com.biz.sccba.sqlanalyzer.idea.scenario;

import com.biz.sccba.sqlanalyzer.idea.contract.PluginApiDtos.*;

import java.util.*;

/**
 * UI selection model for the user main scenario. It consumes server-provided dynamic-node facts;
 * it is not a MyBatis parser and never produces SQL.
 */
public final class MainScenarioModel {
    public static final double LOW_CONFIDENCE_THRESHOLD = 0.65d;
    public static final String VISIBLE_SPACE_FALLBACK = "\u2420 1个空格";

    private final SuggestionSet suggestionSet;
    private final Map<String, SuggestionNode> nodes = new LinkedHashMap<>();
    private final Map<String, Boolean> selected = new LinkedHashMap<>();
    private final Map<String, CollectionMode> collectionModes = new LinkedHashMap<>();
    private final Map<String, TypedValue> parameters = new LinkedHashMap<>();
    private final List<String> conflicts;

    public MainScenarioModel(SuggestionSet suggestionSet) {
        this.suggestionSet = Objects.requireNonNull(suggestionSet, "suggestionSet");
        for (SuggestionNode node : suggestionSet.nodes()) {
            nodes.put(node.nodeId(), normalized(node));
        }
        conflicts = detectTypeConflicts(nodes.values());
        for (SuggestionNode node : nodes.values()) {
            boolean enabled = node.assignable() && node.suggestedEnabled() && !isLowConfidence(node);
            selected.put(node.nodeId(), enabled);
            if (node.kind() == NodeKind.FOREACH) collectionModes.put(node.nodeId(), CollectionMode.SINGLE);
            if (node.parameterPath() != null && !node.parameterPath().isBlank() && node.suggestedValue() != null) {
                parameters.putIfAbsent(node.parameterPath(), node.suggestedValue());
            } else {
                TypedValue fallback = fallback(node);
                if (fallback != null && node.parameterPath() != null && !node.parameterPath().isBlank()) {
                    parameters.putIfAbsent(node.parameterPath(), fallback);
                }
            }
        }
        enforceParentAndChooseRules();
    }

    public List<SuggestionNode> nodes() { return List.copyOf(nodes.values()); }
    public boolean requiresConfirmation() {
        return nodes.values().stream().filter(SuggestionNode::assignable).count() >= 2;
    }
    public boolean selected(String nodeId) { return Boolean.TRUE.equals(selected.get(nodeId)); }
    public boolean enabled(String nodeId) {
        SuggestionNode node = nodes.get(nodeId);
        if (node == null || !node.assignable()) return false;
        return node.parentNodeId() == null || node.parentNodeId().isBlank() || selected(node.parentNodeId());
    }
    public CollectionMode collectionMode(String nodeId) { return collectionModes.get(nodeId); }
    public TypedValue parameter(String path) { return parameters.get(path); }
    public List<String> conflicts() { return conflicts; }
    public boolean valid() { return conflicts.isEmpty(); }

    public void select(String nodeId, boolean value) {
        SuggestionNode node = nodes.get(nodeId);
        if (node == null || !node.assignable() || (value && !enabled(nodeId))) return;
        selected.put(nodeId, value);
        if (value && node.chooseGroupId() != null && !node.chooseGroupId().isBlank()) {
            nodes.values().stream()
                    .filter(other -> !other.nodeId().equals(nodeId))
                    .filter(other -> node.chooseGroupId().equals(other.chooseGroupId()))
                    .forEach(other -> selected.put(other.nodeId(), false));
        }
        enforceParentAndChooseRules();
    }

    public void collectionMode(String nodeId, CollectionMode mode) {
        SuggestionNode node = nodes.get(nodeId);
        if (node != null && node.kind() == NodeKind.FOREACH && mode != null) {
            collectionModes.put(nodeId, mode);
            if (mode == CollectionMode.EMPTY) selected.put(nodeId, false);
            else if (enabled(nodeId)) selected.put(nodeId, true);
        }
    }

    public void parameter(String path, TypedValue value) {
        if (path != null && !path.isBlank() && value != null) parameters.put(path, value);
    }

    public MainScenario snapshot() {
        List<NodeSelection> selections = nodes.values().stream()
                .map(node -> new NodeSelection(node.nodeId(), selected(node.nodeId()), collectionModes.get(node.nodeId())))
                .toList();
        return new MainScenario(suggestionSet.suggestionSetId(), selections, parameters);
    }

    public static boolean isLowConfidence(SuggestionNode node) {
        return node.confidence() < LOW_CONFIDENCE_THRESHOLD
                || node.source() == null || node.source().isBlank()
                || "TYPE_FALLBACK".equals(node.source());
    }

    public static String displayValue(SuggestionNode node, TypedValue value) {
        if (value == null) return "";
        if (value.type() == ValueType.STRING && " ".equals(value.value())
                && isPureStringNonNull(node)) return VISIBLE_SPACE_FALLBACK;
        if (value.type() == ValueType.COLLECTION) return value.values().toString();
        return value.value();
    }

    private void enforceParentAndChooseRules() {
        boolean changed;
        do {
            changed = false;
            for (SuggestionNode node : nodes.values()) {
                if (node.parentNodeId() != null && !node.parentNodeId().isBlank()
                        && !selected(node.parentNodeId()) && selected(node.nodeId())) {
                    selected.put(node.nodeId(), false);
                    changed = true;
                }
            }
        } while (changed);
    }

    private static SuggestionNode normalized(SuggestionNode node) {
        ConditionCategory category = node.category() == null ? structuralFallback(node) : node.category();
        CategorySource source = node.category() == null ? CategorySource.STRUCTURE_FALLBACK
                : (node.categorySource() == null ? CategorySource.SERVER_EXPLAINED : node.categorySource());
        return new SuggestionNode(node.nodeId(), node.kind(), node.testExpression(), node.parameterPath(),
                node.parameterType(), node.parentNodeId(), node.chooseGroupId(), category, source,
                node.assignable(), node.suggestedEnabled(), node.suggestedValue(), node.source(),
                node.version(), node.locator(), node.confidence(), node.reason());
    }

    private static ConditionCategory structuralFallback(SuggestionNode node) {
        if (node.kind() == NodeKind.STRUCTURE || node.kind() == NodeKind.CHOOSE_OTHERWISE) {
            return ConditionCategory.OTHER;
        }
        return ConditionCategory.FILTER;
    }

    private static TypedValue fallback(SuggestionNode node) {
        if (isPureStringNonNull(node)) return TypedValue.scalar(ValueType.STRING, " ");
        if (node.kind() == NodeKind.FOREACH) return TypedValue.collection(List.of());
        return null;
    }

    private static boolean isPureStringNonNull(SuggestionNode node) {
        if (node.parameterType() == null || !node.parameterType().equals("java.lang.String")) return false;
        String path = node.parameterPath() == null ? "" : node.parameterPath().trim();
        String expression = node.testExpression() == null ? "" : node.testExpression().replaceAll("\\s+", "");
        return expression.equals(path + "!=null") || expression.equals("null!=" + path);
    }

    private static List<String> detectTypeConflicts(Collection<SuggestionNode> nodes) {
        Map<String, String> types = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (SuggestionNode node : nodes) {
            if (!node.assignable() || node.parameterPath() == null || node.parameterPath().isBlank()) continue;
            String type = node.parameterType() == null ? "" : node.parameterType();
            String prior = types.putIfAbsent(node.parameterPath(), type);
            if (prior != null && !prior.equals(type)) {
                out.add(node.parameterPath() + ": " + prior + " / " + type
                        + "（节点 " + node.nodeId() + "）");
            }
        }
        return List.copyOf(out);
    }
}
