package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.repository.AnalysisReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Server-side, tenant-scoped history filtering without a Plugin report-delete API. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class PluginReportHistoryService {

    private static final int SCAN_BATCH = 200;

    private final AnalysisReportRepository reports;
    private final ObjectMapper objectMapper;

    public PluginReportHistoryService(AnalysisReportRepository reports, ObjectMapper objectMapper) {
        this.reports = reports;
        this.objectMapper = objectMapper;
    }

    public HistoryPage search(String clientId, Filter filter) {
        int page = Math.max(0, filter.page());
        int size = Math.max(1, Math.min(filter.size(), 100));
        int fromIndex = page * size;
        int matched = 0;
        int offset = 0;
        List<HistoryItem> pageItems = new ArrayList<>();
        while (true) {
            List<AnalysisReportRepository.Report> batch =
                    reports.listForClientPage(clientId, offset, SCAN_BATCH);
            if (batch.isEmpty()) break;
            for (var report : batch) {
                HistoryItem item = project(report);
                if (!matches(item, filter)) continue;
                if (matched >= fromIndex && pageItems.size() < size) {
                    pageItems.add(item);
                }
                matched++;
            }
            if (batch.size() < SCAN_BATCH) break;
            offset += batch.size();
        }
        int pages = matched == 0 ? 0 : (matched + size - 1) / size;
        return new HistoryPage(List.copyOf(pageItems), page, size, matched, pages);
    }

    private HistoryItem project(AnalysisReportRepository.Report report) {
        try {
            JsonNode root = objectMapper.readTree(report.reportJson());
            JsonNode subject = root.path("subject");
            JsonNode audit = root.path("audit");
            return new HistoryItem(report.id(), report.runId(), report.sessionId(),
                    subject.path("projectId").asText(""),
                    nullable(subject, "moduleId"), subject.path("namespace").asText(""),
                    subject.path("statementId").asText(report.statementId()),
                    subject.path("contentHash").asText(""),
                    audit.path("datasourceProfileId").asText(""),
                    audit.path("knowledgeVersion").asText(""),
                    audit.path("profileSnapshotId").asText(""),
                    audit.path("contextFingerprint").asText(""),
                    audit.path("stale").asBoolean(false), report.severity(),
                    report.createdAt(), report.reportJson());
        } catch (Exception e) {
            return new HistoryItem(report.id(), report.runId(), report.sessionId(), "",
                    null, report.namespace(), report.statementId(), "", "", "", "",
                    "", false, report.severity(), report.createdAt(), report.reportJson());
        }
    }

    private static boolean matches(HistoryItem item, Filter filter) {
        if (!blank(filter.projectId()) && !filter.projectId().equals(item.projectId())) return false;
        if (!blank(filter.moduleId()) && !filter.moduleId().equals(item.moduleId())) return false;
        if (!blank(filter.statement())
                && !(item.namespace() + "." + item.statementId()).contains(filter.statement())) return false;
        if (!blank(filter.datasourceProfileId())
                && !filter.datasourceProfileId().equals(item.datasourceProfileId())) return false;
        if (!blank(filter.severity())
                && !filter.severity().equalsIgnoreCase(item.severity())) return false;
        if (filter.completedFrom() != null && item.completedAt().isBefore(filter.completedFrom())) return false;
        if (filter.completedTo() != null && item.completedAt().isAfter(filter.completedTo())) return false;
        return filter.stale() == null || filter.stale() == item.stale();
    }

    public static Instant instant(String value, String field) {
        if (blank(value)) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " 必须是 ISO-8601 时间");
        }
    }

    private static String nullable(JsonNode object, String field) {
        JsonNode value = object.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Filter(String projectId, String moduleId, String statement,
                         String datasourceProfileId, String severity,
                         Instant completedFrom, Instant completedTo, Boolean stale,
                         int page, int size) {
    }

    public record HistoryItem(String reportId, String runId, String sessionId,
                              String projectId, String moduleId, String namespace,
                              String statementId, String contentHash,
                              String datasourceProfileId, String knowledgeVersion,
                              String profileSnapshotId, String contextFingerprint,
                              boolean stale, String severity, Instant completedAt,
                              String reportJson) {
    }

    public record HistoryPage(List<HistoryItem> items, int page, int size,
                              long totalElements, int totalPages) {
    }
}
