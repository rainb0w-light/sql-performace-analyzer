package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.analysis.ScenarioContextResolver;
import com.biz.sccba.sqlanalyzer.analysis.StatementReferenceResolver;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog;
import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog.NodeInfo;
import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog.StatementStructure;
import com.biz.sccba.sqlanalyzer.mybatis.MyBatisNodeTracing;
import com.biz.sccba.sqlanalyzer.mybatis.MyBatisStatementRuntime;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.BoundSqlPreview;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.BoundSqlPreviewRequest;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.CategorySource;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.ConditionCategory;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.FieldError;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.NodeKind;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.NodeSelection;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.ParameterMapping;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.SuggestionNode;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.SuggestionRequest;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.SuggestionSet;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.TypedValue;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.ValueType;
import com.biz.sccba.sqlanalyzer.repository.ArtifactRepository;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamInfo;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamKind;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Default-parameter suggestion and side-effect-free BoundSql preview service. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class PluginMapperPreparationService {

    private static final double LOW_CONFIDENCE = 0.25;

    private final ArtifactService artifacts;
    private final ArtifactRepository artifactRepository;
    private final DynamicNodeCatalog catalog;
    private final StatementReferenceResolver references;
    private final ScenarioContextResolver contexts;
    private final ObjectMapper objectMapper;

    public PluginMapperPreparationService(
            ArtifactService artifacts, ArtifactRepository artifactRepository,
            DynamicNodeCatalog catalog, StatementReferenceResolver references,
            ScenarioContextResolver contexts, ObjectMapper objectMapper) {
        this.artifacts = artifacts;
        this.artifactRepository = artifactRepository;
        this.catalog = catalog;
        this.references = references;
        this.contexts = contexts;
        this.objectMapper = objectMapper;
    }

    public SuggestionSet suggest(String clientId, SuggestionRequest request) {
        var artifact = artifactRepository.findByIdForClient(request.artifactId(), clientId)
                .orElseThrow(() -> new IllegalArgumentException("Artifact 不存在"));
        String sourceHash = metadataText(artifact.metadataJson(), "sourceContentHash");
        if (request.contentHash() != null && !request.contentHash().isBlank()
                && !request.contentHash().equalsIgnoreCase(artifact.sha256())
                && !request.contentHash().equalsIgnoreCase(sourceHash)) {
            throw new IllegalArgumentException("CONTENT_HASH_MISMATCH: Mapper 内容已变化");
        }
        byte[] mapper = artifacts.read(clientId, request.artifactId());
        StatementStructure statement = statement(mapper, request.statementId());
        var refs = references.resolve(mapper, "artifact:" + request.artifactId(),
                request.statementId(), null, null);
        var context = contexts.resolve(clientId, request.datasourceProfileId(), "public",
                refs, List.of(), 20);

        List<SuggestionNode> nodes = suggestions(statement, refs.parameters(), context);
        StoredSuggestionSet stored = new StoredSuggestionSet(
                request.artifactId(), request.statementId(), request.datasourceProfileId(),
                request.projectId(), request.moduleId(), artifact.sha256(),
                context.knowledgeVersion(), context.profileSnapshotId(),
                dollarWhitelisted(statement.dollarExpressions(),
                        context.plannerInput().knowledge()), nodes);
        try {
            byte[] json = objectMapper.writeValueAsBytes(stored);
            var snapshot = artifacts.ingest(clientId, null, "PLUGIN_SUGGESTION_SET",
                    "suggestion-set.json", "application/json", json,
                    "{\"sourceArtifactId\":\"" + request.artifactId() + "\"}");
            return new SuggestionSet(snapshot.id(), contextVersion(context.knowledgeVersion(),
                    context.profileSnapshotId()), nodes);
        } catch (Exception e) {
            throw new IllegalStateException("无法保存建议快照", e);
        }
    }

    public BoundSqlPreview preview(String clientId, BoundSqlPreviewRequest request) {
        StoredSuggestionSet stored = readSuggestion(clientId, request.suggestionSetId());
        byte[] mapper = artifacts.read(clientId, stored.sourceArtifactId());
        StatementStructure statement = statement(mapper, stored.statementId());
        if (!statement.dollarExpressions().isEmpty() && !stored.dollarWhitelistSatisfied()) {
            return new BoundSqlPreview("", List.of(), List.of(),
                    List.of(new FieldError(statement.dollarExpressions().get(0), "",
                            "DOLLAR_WHITELIST_REQUIRED",
                            "${} 参数缺少显式允许值，无法安全预览")), true);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        List<FieldError> errors = new ArrayList<>();
        request.parameters().forEach((path, value) -> {
            try {
                setPath(params, path, javaValue(value));
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError(path, nodeForPath(stored.nodes(), path),
                        "TYPE_MISMATCH", e.getMessage()));
            }
        });
        applySelections(params, request.selections(), stored.nodes(), errors);
        if (!errors.isEmpty()) {
            return new BoundSqlPreview("", List.of(), List.of(), errors, true);
        }

        MyBatisStatementRuntime runtime = new MyBatisStatementRuntime(null, null);
        var configuration = runtime.loadConfiguration(mapper,
                "artifact:" + stored.sourceArtifactId());
        String qualified = statement.namespace() + "." + statement.statementId();
        Set<String> hits = MyBatisNodeTracing.instrument(configuration, qualified, statement.nodes());
        var bound = runtime.bind(configuration, qualified, params);
        if (bound.isUnsupported()) {
            return new BoundSqlPreview("", List.copyOf(hits), List.of(),
                    List.of(new FieldError("", "", "UNSUPPORTED", bound.unsupported())), true);
        }
        List<ParameterMapping> mappings = bound.parameterMappings().stream()
                .map(mapping -> new ParameterMapping(mapping.property(), mapping.jdbcType()))
                .toList();
        String sql = redactDollarValues(bound.sql(), statement.dollarExpressions(), params);
        return new BoundSqlPreview(sql, List.copyOf(hits), mappings, List.of(), true);
    }

    List<SuggestionNode> suggestions(
            StatementStructure statement, Map<String, ParamInfo> params,
            ScenarioContextResolver.ContextBundle context) {
        Map<String, NodeInfo> byId = new LinkedHashMap<>();
        statement.nodes().forEach(node -> byId.put(node.nodeId(), node));
        Map<String, ParamKind> nodeKinds = new LinkedHashMap<>();
        Map<String, Set<ParamKind>> kindsByPath = new LinkedHashMap<>();
        for (NodeInfo node : statement.nodes()) {
            String path = parameterPath(node);
            ParamInfo fallback = params.get(path);
            ParamKind kind = nodeParamKind(node,
                    fallback == null ? ParamKind.UNKNOWN : fallback.kind());
            nodeKinds.put(node.nodeId(), kind);
            if (!path.isBlank()) {
                kindsByPath.computeIfAbsent(path, ignored -> new LinkedHashSet<>()).add(kind);
            }
        }
        List<SuggestionNode> result = new ArrayList<>();
        for (NodeInfo node : statement.nodes()) {
            String path = parameterPath(node);
            ParamKind kind = nodeKinds.get(node.nodeId());
            Provenance provenance = provenance(path, kind, context);
            String chooseGroup = chooseGroup(node, byId);
            boolean conflict = !path.isBlank() && kindsByPath.get(path).size() > 1;
            boolean assignable = !path.isBlank() && nodeType(node) != NodeKind.STRUCTURE
                    && !conflict;
            String reason = conflict
                    ? "共享参数类型冲突：" + kindsByPath.get(path)
                    : provenance.reason();
            result.add(new SuggestionNode(node.nodeId(), nodeType(node), node.test(), path,
                    javaType(kind), node.parentNodeId(), chooseGroup, provenance.category(),
                    provenance.categorySource(), assignable,
                    assignable && provenance.confidence() >= 0.5,
                    provenance.value(), provenance.source(), provenance.version(),
                    provenance.locator(), provenance.confidence(), reason));
        }
        return List.copyOf(result);
    }

    private Provenance provenance(String path, ParamKind kind,
                                  ScenarioContextResolver.ContextBundle context) {
        if (!path.isBlank()) {
            for (var shard : context.shards()) {
                if (matches(path, shard.shardKey()) || matches(path, shard.secondaryShardKey())) {
                    return new Provenance(defaultValue(kind, path), "BUSINESS_RULE",
                            context.knowledgeVersion(), "shard:" + path, 0.9,
                            "参数匹配已确认的分片路由键", ConditionCategory.ROUTING,
                            CategorySource.SERVER_EXPLAINED);
                }
            }
            for (ColumnStat stat : context.profileStats()) {
                if (matches(path, stat.columnName())) {
                    String value = firstValue(stat.topKJson());
                    if (value != null) {
                        return new Provenance(typed(kind, value), "PROFILE_SNAPSHOT",
                                stat.snapshotId(), stat.schemaName() + "." + stat.tableName()
                                + "." + stat.columnName() + "/top-k/0", 0.9,
                                "最新画像 Top-K", ConditionCategory.OTHER,
                                CategorySource.SERVER_EXPLAINED);
                    }
                }
            }
            for (var fact : context.semanticFacts()) {
                if (matches(path, fact.name())) {
                    String value = fact.structured().get("value") == null
                            ? null : String.valueOf(fact.structured().get("value"));
                    if (value != null && !value.isBlank()) {
                        return new Provenance(typed(kind, value), fact.evidence().sourceType(),
                                String.valueOf(fact.evidence().version()),
                                fact.evidence().locator(), fact.evidence().confidence(),
                                fact.text(), ConditionCategory.OTHER,
                                CategorySource.SERVER_EXPLAINED);
                    }
                }
            }
        }
        return new Provenance(defaultValue(kind, path), "STRUCTURAL_FALLBACK", null,
                path, LOW_CONFIDENCE, fallbackReason(kind),
                ConditionCategory.OTHER, CategorySource.STRUCTURE_FALLBACK);
    }

    private TypedValue defaultValue(ParamKind kind, String path) {
        return switch (kind) {
            case LIST -> new TypedValue(ValueType.COLLECTION, "",
                    List.of(new TypedValue(ValueType.STRING, " ", List.of(), Map.of())), Map.of());
            case INT, LONG -> typed(kind, "1");
            case DOUBLE -> typed(kind, "1.0");
            case BOOLEAN -> typed(kind, "true");
            case DATE -> typed(kind, "2026-01-01T00:00:00Z");
            case OBJECT -> new TypedValue(ValueType.OBJECT, "", List.of(), Map.of());
            case STRING, UNKNOWN -> typed(ParamKind.STRING, " ");
        };
    }

    private static TypedValue typed(ParamKind kind, String value) {
        ValueType type = switch (kind) {
            case INT, LONG -> ValueType.INTEGER;
            case DOUBLE -> ValueType.DECIMAL;
            case BOOLEAN -> ValueType.BOOLEAN;
            case DATE -> ValueType.DATE_TIME;
            case LIST -> ValueType.COLLECTION;
            case OBJECT -> ValueType.OBJECT;
            default -> ValueType.STRING;
        };
        return new TypedValue(type, value, List.of(), Map.of());
    }

    private String firstValue(String json) {
        try {
            JsonNode root = objectMapper.readTree(json == null ? "[]" : json);
            if (root.isArray() && !root.isEmpty()) {
                JsonNode value = root.get(0).path("value");
                return value.isMissingNode() || value.isNull() ? null : value.asText();
            }
        } catch (Exception ignored) {
            // Malformed profile data is ignored and falls back structurally.
        }
        return null;
    }

    private String metadataText(String json, String field) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json).path(field).asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private StoredSuggestionSet readSuggestion(String clientId, String suggestionSetId) {
        try {
            return objectMapper.readValue(artifacts.read(clientId, suggestionSetId),
                    StoredSuggestionSet.class);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("建议快照无效", e);
        }
    }

    private StatementStructure statement(byte[] mapper, String statementId) {
        var structure = catalog.scan(new String(mapper, StandardCharsets.UTF_8));
        return structure.statements().stream()
                .filter(candidate -> candidate.statementId().equals(statementId)
                        || (structure.namespace() + "." + candidate.statementId()).equals(statementId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("statement 不存在：" + statementId));
    }

    private static void applySelections(Map<String, Object> params, List<NodeSelection> selections,
                                        List<SuggestionNode> nodes, List<FieldError> errors) {
        Map<String, SuggestionNode> byId = new LinkedHashMap<>();
        nodes.forEach(node -> byId.put(node.nodeId(), node));
        for (NodeSelection selection : selections) {
            SuggestionNode node = byId.get(selection.nodeId());
            if (node == null) {
                errors.add(new FieldError("", selection.nodeId(), "UNKNOWN_NODE", "动态节点不存在"));
                continue;
            }
            if (node.parameterPath() == null || node.parameterPath().isBlank()) {
                continue;
            }
            if (!selection.selected()) {
                setPath(params, node.parameterPath(), null);
            } else if (node.kind() == NodeKind.FOREACH && selection.collectionMode() != null) {
                Object current = pathValue(params, node.parameterPath());
                List<?> values = current instanceof List<?> list ? list : List.of();
                switch (selection.collectionMode()) {
                    case EMPTY -> setPath(params, node.parameterPath(), List.of());
                    case SINGLE -> setPath(params, node.parameterPath(),
                            values.isEmpty() ? List.of(" ") : List.of(values.get(0)));
                    case MULTIPLE -> setPath(params, node.parameterPath(),
                            values.size() >= 2 ? values : List.of(" ", "  "));
                }
            }
        }
    }

    static Object javaValue(TypedValue value) {
        if (value == null || value.type() == ValueType.NULL) {
            return null;
        }
        return switch (value.type()) {
            case STRING, ENUM, DATE_TIME -> value.value();
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(value.value())
                        && !"false".equalsIgnoreCase(value.value())) {
                    throw new IllegalArgumentException("必须是 true 或 false");
                }
                yield Boolean.parseBoolean(value.value());
            }
            case INTEGER -> {
                try {
                    yield Long.parseLong(value.value());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("必须是整数");
                }
            }
            case DECIMAL -> {
                try {
                    yield new java.math.BigDecimal(value.value());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("必须是数值");
                }
            }
            case COLLECTION -> value.values().stream()
                    .map(PluginMapperPreparationService::javaValue).toList();
            case OBJECT -> {
                Map<String, Object> object = new LinkedHashMap<>();
                value.fields().forEach((key, field) -> object.put(key, javaValue(field)));
                yield object;
            }
            case NULL -> null;
        };
    }

    private static String redactDollarValues(String sql, List<String> dollarExpressions,
                                             Map<String, Object> params) {
        String redacted = sql == null ? "" : sql;
        for (String expression : dollarExpressions) {
            Object value = pathValue(params, expression);
            if (value != null && !String.valueOf(value).isBlank()) {
                redacted = redacted.replace(String.valueOf(value), "<redacted>");
            }
        }
        return redacted;
    }

    private static String parameterPath(NodeInfo node) {
        if ("foreach".equals(node.type())) {
            return node.attributes().getOrDefault("collection", "list");
        }
        return node.referencedNames().isEmpty() ? "" : topLevel(node.referencedNames().get(0));
    }

    private static ParamKind nodeParamKind(NodeInfo node, ParamKind fallback) {
        if ("foreach".equals(node.type())) return ParamKind.LIST;
        String test = node.test() == null ? "" : node.test().toLowerCase(Locale.ROOT);
        if (test.contains(".size") || test.contains(".isempty")
                || test.contains(" collection")) return ParamKind.LIST;
        if (test.matches(".*(?:<|>|<=|>=)\\s*-?\\d+(?:\\.\\d+)?.*")) {
            return test.matches(".*\\d+\\.\\d+.*") ? ParamKind.DOUBLE : ParamKind.INT;
        }
        return fallback;
    }

    private static NodeKind nodeType(NodeInfo node) {
        return switch (node.type()) {
            case "if" -> NodeKind.IF;
            case "when" -> NodeKind.CHOOSE_WHEN;
            case "otherwise" -> NodeKind.CHOOSE_OTHERWISE;
            case "foreach" -> NodeKind.FOREACH;
            default -> NodeKind.STRUCTURE;
        };
    }

    private static String chooseGroup(NodeInfo node, Map<String, NodeInfo> byId) {
        NodeInfo current = node;
        while (current != null) {
            if ("choose".equals(current.type())) {
                return current.nodeId();
            }
            current = current.parentNodeId() == null ? null : byId.get(current.parentNodeId());
        }
        return null;
    }

    private static String javaType(ParamKind kind) {
        return switch (kind) {
            case INT -> "java.lang.Integer";
            case LONG -> "java.lang.Long";
            case DOUBLE -> "java.math.BigDecimal";
            case BOOLEAN -> "java.lang.Boolean";
            case DATE -> "java.time.Instant";
            case LIST -> "java.util.List";
            case OBJECT -> "java.util.Map";
            case STRING, UNKNOWN -> "java.lang.String";
        };
    }

    private static String fallbackReason(ParamKind kind) {
        return kind == ParamKind.STRING || kind == ParamKind.UNKNOWN
                ? "无业务依据；使用 ␠ 1个空格 作为非 null 结构回退，需人工确认"
                : "无业务依据；使用类型安全的结构回退，需人工确认";
    }

    private static boolean matches(String parameter, String column) {
        if (parameter == null || column == null) {
            return false;
        }
        String left = normalize(parameter);
        String right = normalize(column);
        return left.equals(right) || left.startsWith(right) || right.startsWith(left);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String topLevel(String path) {
        int dot = path.indexOf('.');
        int bracket = path.indexOf('[');
        int end = path.length();
        if (dot > 0) {
            end = Math.min(end, dot);
        }
        if (bracket > 0) {
            end = Math.min(end, bracket);
        }
        return path.substring(0, end);
    }

    @SuppressWarnings("unchecked")
    static void setPath(Map<String, Object> params, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = params;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = current.get(parts[i]);
            if (!(child instanceof Map<?, ?>)) {
                Map<String, Object> nested = new LinkedHashMap<>();
                current.put(parts[i], nested);
                current = nested;
            } else {
                current = (Map<String, Object>) child;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    private static Object pathValue(Map<String, Object> params, String path) {
        Object current = params;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private static String nodeForPath(List<SuggestionNode> nodes, String path) {
        return nodes.stream().filter(node -> path.equals(node.parameterPath()))
                .map(SuggestionNode::nodeId).findFirst().orElse("");
    }

    private static String contextVersion(String knowledge, String profile) {
        return (knowledge == null ? "knowledge@none" : knowledge)
                + "|" + (profile == null ? "profile@none" : "profile@" + profile);
    }

    private static boolean dollarWhitelisted(
            List<String> expressions,
            List<com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ColumnKnowledge> knowledge) {
        if (expressions == null || expressions.isEmpty()) return true;
        for (String expression : expressions) {
            String name = topLevel(expression);
            boolean found = knowledge.stream().anyMatch(item -> item.columnName().equals(name)
                    && item.frequentValues() != null && !item.frequentValues().isEmpty());
            if (!found) return false;
        }
        return true;
    }

    private record Provenance(TypedValue value, String source, String version, String locator,
                              double confidence, String reason, ConditionCategory category,
                              CategorySource categorySource) {
    }

    public record StoredSuggestionSet(
            String sourceArtifactId, String statementId, String datasourceProfileId,
            String projectId, String moduleId, String contentHash, String knowledgeVersion,
            String profileSnapshotId, boolean dollarWhitelistSatisfied,
            List<SuggestionNode> nodes) {
        public StoredSuggestionSet {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }
}
