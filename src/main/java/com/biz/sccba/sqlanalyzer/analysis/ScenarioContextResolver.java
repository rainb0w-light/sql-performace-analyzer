package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Version;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeQueryService;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeQueryService.Fact;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeRetriever;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import com.biz.sccba.sqlanalyzer.repository.MetadataRepository;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ColumnKnowledge;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ColumnProfile;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.IndexInfo;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ShardInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side scenario context assembly (docs/cloud-code-next-goal.md §8): given ONLY the
 * authenticated clientId and the statement reference, loads the tenant's published business
 * knowledge, latest profile snapshot, index and shard definitions, and builds the PlannerInput.
 * IDEA clients never assemble trusted knowledge inputs — anything they send besides mapper,
 * statementId and optional samples is ignored.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ScenarioContextResolver {

    /** Alias type marking ${} interpolation whitelists: aliasName = parameter, targetName = allowed value. */
    public static final String DOLLAR_WHITELIST_ALIAS = "DOLLAR_WHITELIST";

    private final KnowledgeQueryService knowledge;
    private final KnowledgeSourceRepository knowledgeSources;
    private final MetadataRepository metadata;
    private final ProfilingRepository profiling;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<KnowledgeRetriever> retrieverProvider;

    public ScenarioContextResolver(KnowledgeQueryService knowledge, KnowledgeSourceRepository knowledgeSources,
                                   MetadataRepository metadata, ProfilingRepository profiling,
                                   ObjectMapper objectMapper,
                                   ObjectProvider<KnowledgeRetriever> retrieverProvider) {
        this.knowledge = knowledge;
        this.knowledgeSources = knowledgeSources;
        this.metadata = metadata;
        this.profiling = profiling;
        this.objectMapper = objectMapper;
        this.retrieverProvider = retrieverProvider;
    }

    public record ContextBundle(PlannerInput plannerInput, StatementReferenceResolver.References references,
                                List<Fact> semanticFacts, List<IndexInfo> indexes, List<ShardInfo> shards,
                                List<ColumnStat> profileStats,
                                String knowledgeVersion, String profileSnapshotId) {}

    public ContextBundle resolve(String clientId, StatementReferenceResolver.References references,
                                 List<Map<String, Object>> userSamples, int maxScenarios) {
        return resolve(clientId, null, "public", references, userSamples, maxScenarios);
    }

    public ContextBundle resolve(String clientId, String datasourceProfileId, String schemaName,
                                 StatementReferenceResolver.References references,
                                 List<Map<String, Object>> userSamples, int maxScenarios) {
        List<Fact> facts = new ArrayList<>();
        List<ColumnKnowledge> columnKnowledge = new ArrayList<>();
        List<IndexInfo> indexes = new ArrayList<>();
        List<ShardInfo> shards = new ArrayList<>();

        // Business semantics for every referenced table (+ alias resolution).
        for (String table : references.tables()) {
            facts.addAll(knowledge.resolveTables(clientId, table));
            facts.addAll(knowledge.columns(clientId, table, null));
            facts.addAll(knowledge.rules(clientId, table));
            var tableIndexes = datasourceProfileId == null
                    ? metadata.indexesForTable(clientId, table)
                    : metadata.indexesForTable(clientId, datasourceProfileId,
                    defaultSchema(schemaName), table);
            for (var index : tableIndexes) {
                indexes.add(new IndexInfo(index.indexName(), jsonColumns(index.columnsJson())));
                facts.add(new Fact("INDEX", index.tableName() + "." + index.indexName(),
                        "索引 " + index.indexName() + "(" + String.join(",", jsonColumns(index.columnsJson())) + ")，来源 " + index.source(),
                        Map.of("indexType", index.indexType(), "columnsJson", index.columnsJson()),
                        new KnowledgeQueryService.Evidence(index.source().equals("MANUAL") ? "MANUAL_RULE" : "SYSTEM_CATALOG",
                                index.id(), index.version(), index.tableName(), index.validFrom(), 0.95)));
            }
            var shard = datasourceProfileId == null
                    ? metadata.findShard(clientId, table)
                    : metadata.findShard(clientId, datasourceProfileId,
                    defaultSchema(schemaName), table);
            shard.ifPresent(def -> {
                shards.add(new ShardInfo(def.shardKey(), def.secondaryShardKey()));
                facts.add(new Fact("SHARD", def.logicalTable(),
                        "分片 " + def.logicalTable() + "：主分片键 " + def.shardKey()
                                + "，二级分片键 " + def.secondaryShardKey() + "，来源 " + def.source(),
                        Map.of("shardKey", String.valueOf(def.shardKey()),
                                "secondaryShardKey", String.valueOf(def.secondaryShardKey())),
                        new KnowledgeQueryService.Evidence(def.source().equals("MANUAL") ? "MANUAL_RULE" : "SYSTEM_CATALOG",
                                def.id(), def.version(), def.logicalTable(), def.validFrom(), 0.95)));
            });
        }

        // ${} whitelists from published aliases (DOLLAR_WHITELIST: parameter -> allowed value).
        for (String dollar : references.dollarExpressions()) {
            String param = topLevel(dollar);
            List<String> allowed = new ArrayList<>();
            for (var alias : knowledge.aliases(clientId, param)) {
                if (DOLLAR_WHITELIST_ALIAS.equals(alias.structured().get("aliasType"))) {
                    allowed.add(String.valueOf(alias.structured().get("target")));
                    facts.add(alias);
                }
            }
            if (!allowed.isEmpty()) {
                columnKnowledge.add(new ColumnKnowledge(param, false, allowed, List.of()));
            }
        }

        appendSemanticRetrieval(clientId, references, facts);

        // Latest profile snapshot of the client: map column stats onto parameters by name stem.
        String profileSnapshotId = null;
        List<ColumnStat> stats = datasourceProfileId == null
                ? profiling.latestStatsForClient(clientId)
                : profiling.latestStatsForDatasource(clientId, datasourceProfileId);
        List<ColumnProfile> profiles = new ArrayList<>();
        if (!stats.isEmpty()) {
            profileSnapshotId = stats.get(0).snapshotId();
            for (String param : references.parameters().keySet()) {
                ColumnStat match = stats.stream()
                        .filter(s -> stemMatches(param, s.columnName()))
                        .findFirst().orElse(null);
                if (match == null) continue;
                columnKnowledge.add(enumKnowledgeFromProfile(param, match));
                profiles.add(new ColumnProfile(param, jsonStringValues(match.topKJson(), "value"),
                        jsonStringValues(match.quantilesJson(), "value"),
                        match.minValue(), match.maxValue(), match.nullRatio(), match.approxDistinct()));
            }
        }

        String knowledgeVersion = latestKnowledgeVersion(clientId);

        PlannerInput input = new PlannerInput(references.parameters(), columnKnowledge, profiles,
                indexes, shards, userSamples == null ? List.of() : userSamples,
                knowledgeVersion, profileSnapshotId, maxScenarios);
        return new ContextBundle(input, references, facts, indexes, shards, List.copyOf(stats),
                knowledgeVersion, profileSnapshotId);
    }

    private void appendSemanticRetrieval(String clientId,
                                         StatementReferenceResolver.References references,
                                         List<Fact> facts) {
        KnowledgeRetriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null || !retriever.available()) return;
        String query = String.join(" ", references.tables()) + " "
                + String.join(" ", references.parameters().keySet());
        if (query.isBlank()) query = references.statementId();
        for (var source : knowledgeSources.listSources(clientId)) {
            if (source.currentVersionId() == null) continue;
            var active = knowledgeSources.findVersionForClient(clientId, source.currentVersionId())
                    .orElse(null);
            if (active == null) continue;
            for (var hit : retriever.search(clientId, query, source.id(), active.versionNo(), 5)) {
                facts.add(new Fact(hit.kind(), hit.name(), hit.text(),
                        Map.of("score", hit.score(), "retrieval", "SEMANTIC"),
                        new KnowledgeQueryService.Evidence("EXCEL_PUBLISHED", hit.sourceId(),
                                hit.versionNo(), hit.locator(), active.publishedAt(), hit.confidence())));
            }
        }
    }

    private static String defaultSchema(String schemaName) {
        return schemaName == null || schemaName.isBlank() ? "public" : schemaName;
    }

    private ColumnKnowledge enumKnowledgeFromProfile(String param, ColumnStat stat) {
        List<String> values = jsonStringValues(stat.topKJson(), "value");
        List<String> rare = values.size() > 1 ? List.of(values.get(values.size() - 1)) : List.of();
        List<String> frequent = values.isEmpty() ? List.of() : List.of(values.get(0));
        return new ColumnKnowledge(param, false, frequent, rare);
    }

    private String latestKnowledgeVersion(String clientId) {
        List<Version> published = new ArrayList<>();
        Map<String, String> sourceNames = new LinkedHashMap<>();
        for (var source : knowledgeSources.listSources(clientId)) {
            if (source.currentVersionId() == null) continue;
            knowledgeSources.findVersionForClient(clientId, source.currentVersionId()).ifPresent(v -> {
                published.add(v);
                sourceNames.put(v.id(), source.name());
            });
        }
        return published.stream()
                .max(Comparator.comparingInt(Version::versionNo))
                .map(v -> sourceNames.get(v.id()) + "@" + v.versionNo())
                .orElse(null);
    }

    private List<String> jsonColumns(String columnsJson) {
        List<String> out = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(columnsJson == null ? "[]" : columnsJson);
            for (JsonNode entry : node) {
                out.add(entry.path("column").asText());
            }
        } catch (Exception ignored) {
            // malformed columns_json yields no columns; the fact still records the raw value
        }
        return out;
    }

    private List<String> jsonStringValues(String json, String field) {
        List<String> out = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(json == null ? "[]" : json);
            for (JsonNode entry : node) {
                String value = entry.path(field).asText(null);
                if (value != null && !value.isBlank()) out.add(value);
            }
        } catch (Exception ignored) {
            // unparseable profile JSON contributes no values
        }
        return out;
    }

    /** Parameter ↔ column name stem match (memberId ↔ member_id, dueBefore ↔ due_at). */
    static boolean stemMatches(String parameterName, String columnName) {
        String p = normalize(parameterName);
        String c = normalize(columnName);
        if (p.equals(c)) return true;
        String pStem = stem(parameterName);
        String cStem = stem(columnName);
        return p.startsWith(cStem) || c.startsWith(pStem);
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String stem(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        int underscore = lower.lastIndexOf('_');
        if (underscore > 0) return normalize(lower.substring(0, underscore));
        // camelCase: cut before the last hump (dueBefore -> due)
        for (int i = lower.length() - 1; i > 1; i--) {
            if (Character.isUpperCase(name.charAt(i))) return normalize(name.substring(0, i));
        }
        return normalize(lower);
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
