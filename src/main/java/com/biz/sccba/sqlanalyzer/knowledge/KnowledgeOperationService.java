package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.domain.knowledge.KnowledgeOperation;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeOperationRepository;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeOperationRepository.Filter;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeOperationRepository.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class KnowledgeOperationService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("UPLOAD", "PARSE", "PUBLISH", "SAMPLE", "RETRIEVE", "INDEX", "EXPORT_LOGS");
    private static final Set<String> ALLOWED_SUMMARY_KEYS = Set.of(
            "fileName", "fileSize", "mediaType", "contentHash", "topK", "queryLength",
            "queryHash", "scope", "hitSourceIds", "filters", "exportLimit", "idempotencyKeyHash");

    private final KnowledgeOperationRepository repository;
    private final ObjectMapper mapper;
    private final int exportLimit;
    private final Clock clock;

    @Autowired
    public KnowledgeOperationService(KnowledgeOperationRepository repository, ObjectMapper mapper,
                                     @Value("${sql-analyzer.knowledge.operations.export-limit:5000}")
                                     int exportLimit) {
        this(repository, mapper, exportLimit, Clock.systemUTC());
    }

    KnowledgeOperationService(KnowledgeOperationRepository repository, ObjectMapper mapper,
                              int exportLimit, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.exportLimit = Math.max(1, exportLimit);
        this.clock = clock;
    }

    public KnowledgeOperation record(String traceId, String clientId, String actorId, String actorType,
                                     String operationType, String sourceId, String versionId,
                                     String runId, String sessionId, Map<String, ?> summary,
                                     String status, String errorCode, long durationMs,
                                     Integer resultCount, Long tokenConsumed) {
        if (!ALLOWED_TYPES.contains(operationType)) throw new IllegalArgumentException("未知知识操作类型");
        if (!Set.of("SUCCESS", "FAILED").contains(status)) throw new IllegalArgumentException("未知操作状态");
        String safeTrace = traceId == null || traceId.isBlank() ? "trace_" + UUID.randomUUID() : traceId;
        KnowledgeOperation operation = new KnowledgeOperation(
                "klog_" + UUID.randomUUID(), safeTrace, clientId, actorId, actorType, operationType,
                sourceId, versionId, runId, sessionId, json(sanitize(summary)), status, errorCode,
                Math.max(0, durationMs), resultCount, tokenConsumed, Instant.now(clock));
        return repository.append(operation);
    }

    public Page list(String clientId, Filter filter, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page 不能小于 0");
        if (size < 1 || size > 200) throw new IllegalArgumentException("size 必须在 1..200");
        return repository.find(clientId, filter, page, size);
    }

    public Stats stats(String clientId) {
        Instant from = LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<KnowledgeOperation> today = repository.findForExport(
                clientId, new Filter(from, Instant.now(clock).plusSeconds(1), null,
                        null, null, null, null), 100_000);
        long management = today.stream().filter(operation ->
                Set.of("UPLOAD", "PUBLISH", "SAMPLE").contains(operation.operationType())).count();
        List<KnowledgeOperation> retrieves = today.stream()
                .filter(operation -> "RETRIEVE".equals(operation.operationType())).toList();
        long retrieveSuccess = retrieves.stream()
                .filter(operation -> "SUCCESS".equals(operation.responseStatus())).count();
        List<Long> durations = retrieves.stream().filter(operation ->
                        "SUCCESS".equals(operation.responseStatus()))
                .map(KnowledgeOperation::durationMs).sorted().toList();

        Map<String, Long> popular = new LinkedHashMap<>();
        for (KnowledgeOperation retrieve : retrieves) {
            Set<String> unique = Set.copyOf(hitSources(retrieve.requestSummaryJson()));
            unique.forEach(source -> popular.merge(source, 1L, Long::sum));
        }
        List<Count> popularTop5 = popular.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(5).map(entry -> new Count(entry.getKey(), entry.getValue())).toList();

        Map<String, Long> actors = new LinkedHashMap<>();
        today.stream().filter(operation ->
                        Set.of("UPLOAD", "PUBLISH", "SAMPLE").contains(operation.operationType()))
                .forEach(operation -> actors.merge(operation.actorId(), 1L, Long::sum));
        List<Count> byActor = actors.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new Count(entry.getKey(), entry.getValue())).toList();
        List<KnowledgeOperation> recent = today.stream()
                .sorted(Comparator.comparing(KnowledgeOperation::createdAt).reversed()
                        .thenComparing(KnowledgeOperation::id, Comparator.reverseOrder()))
                .limit(10).toList();
        return new Stats(management, retrieves.size(),
                retrieves.isEmpty() ? 0 : (double) retrieveSuccess / retrieves.size(),
                percentile(durations, 0.50), percentile(durations, 0.95),
                popularTop5, recent, byActor);
    }

    public byte[] exportCsv(String clientId, Filter filter) {
        List<KnowledgeOperation> rows = repository.findForExport(clientId, filter, exportLimit + 1);
        if (rows.size() > exportLimit) throw new IllegalArgumentException("导出数量超过上限，请缩小筛选范围");
        StringBuilder csv = new StringBuilder();
        csv.append("id,traceId,actorId,actorType,operationType,sourceId,versionId,status,errorCode,")
                .append("durationMs,resultCount,createdAt,requestSummary\n");
        for (KnowledgeOperation row : rows) {
            List<String> cells = List.of(row.id(), row.traceId(), row.actorId(), row.actorType(),
                    row.operationType(), nullable(row.sourceId()), nullable(row.versionId()),
                    row.responseStatus(), nullable(row.errorCode()), String.valueOf(row.durationMs()),
                    row.resultCount() == null ? "" : row.resultCount().toString(),
                    row.createdAt().toString(), row.requestSummaryJson());
            csv.append(cells.stream().map(KnowledgeCsv::csvCell)
                    .collect(java.util.stream.Collectors.joining(","))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static Map<String, Object> querySummary(String query, int topK, String scope,
                                                    List<String> hitSourceIds) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("queryLength", query == null ? 0 : query.length());
        summary.put("queryHash", sha256(query == null ? "" : query));
        summary.put("topK", topK);
        summary.put("scope", scope == null ? "ALL_ACTIVE" : scope);
        summary.put("hitSourceIds", hitSourceIds == null ? List.of() : hitSourceIds.stream().distinct().toList());
        return summary;
    }

    private Map<String, Object> sanitize(Map<String, ?> summary) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (summary == null) return safe;
        summary.forEach((key, value) -> {
            if (ALLOWED_SUMMARY_KEYS.contains(key)) safe.put(key, value);
        });
        return safe;
    }

    private String json(Map<String, Object> value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("操作摘要无法序列化", exception);
        }
    }

    private List<String> hitSources(String json) {
        try {
            Map<String, Object> summary = mapper.readValue(json, new TypeReference<>() {});
            Object sources = summary.get("hitSourceIds");
            if (!(sources instanceof List<?> list)) return List.of();
            List<String> out = new ArrayList<>();
            list.forEach(item -> {
                if (item != null && !String.valueOf(item).isBlank()) out.add(String.valueOf(item));
            });
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算 Query hash", exception);
        }
    }

    public record Count(String key, long count) {}

    public record Stats(long todayManagementOperations, long agentRetrievals,
                        double agentRetrievalSuccessRate, long retrieveP50Ms, long retrieveP95Ms,
                        List<Count> popularSourcesTop5, List<KnowledgeOperation> recentOperations,
                        List<Count> operationsByActor) {}
}
