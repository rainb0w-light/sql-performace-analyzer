package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.domain.knowledge.KnowledgeOperation;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeOperationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcKnowledgeOperationRepository implements KnowledgeOperationRepository {

    private static final String COLUMNS = """
            id, trace_id, client_id, actor_id, actor_type, operation_type, source_id, version_id,
            run_id, session_id, request_summary_json, response_status, error_code, duration_ms,
            result_count, token_consumed, created_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcKnowledgeOperationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public KnowledgeOperation append(KnowledgeOperation operation) {
        jdbc.update("""
                INSERT INTO sql_analyzer.knowledge_operation_log(
                    id, trace_id, client_id, actor_id, actor_type, operation_type, source_id,
                    version_id, run_id, session_id, request_summary_json, response_status,
                    error_code, duration_ms, result_count, token_consumed, created_at)
                VALUES (:id, :traceId, :clientId, :actorId, :actorType, :operationType, :sourceId,
                    :versionId, :runId, :sessionId, :requestSummary, :responseStatus,
                    :errorCode, :durationMs, :resultCount, :tokenConsumed, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", operation.id()).addValue("traceId", operation.traceId())
                .addValue("clientId", operation.clientId()).addValue("actorId", operation.actorId())
                .addValue("actorType", operation.actorType()).addValue("operationType", operation.operationType())
                .addValue("sourceId", operation.sourceId()).addValue("versionId", operation.versionId())
                .addValue("runId", operation.runId()).addValue("sessionId", operation.sessionId())
                .addValue("requestSummary", operation.requestSummaryJson())
                .addValue("responseStatus", operation.responseStatus()).addValue("errorCode", operation.errorCode())
                .addValue("durationMs", operation.durationMs()).addValue("resultCount", operation.resultCount())
                .addValue("tokenConsumed", operation.tokenConsumed()).addValue("createdAt", operation.createdAt()));
        return operation;
    }

    @Override
    public Page find(String clientId, Filter filter, int page, int size) {
        Query query = query(clientId, filter);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sql_analyzer.knowledge_operation_log " + query.where(),
                query.params(), Long.class);
        MapSqlParameterSource pageParams = copy(query.params())
                .addValue("limit", size).addValue("offset", (long) page * size);
        List<KnowledgeOperation> items = jdbc.query(
                "SELECT " + COLUMNS + " FROM sql_analyzer.knowledge_operation_log "
                        + query.where() + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
                pageParams, JdbcKnowledgeOperationRepository::map);
        return new Page(items, page, size, total);
    }

    @Override
    public List<KnowledgeOperation> findForExport(String clientId, Filter filter, int limit) {
        Query query = query(clientId, filter);
        MapSqlParameterSource params = copy(query.params()).addValue("limit", limit);
        return jdbc.query("SELECT " + COLUMNS + " FROM sql_analyzer.knowledge_operation_log "
                        + query.where() + " ORDER BY created_at DESC, id DESC LIMIT :limit",
                params, JdbcKnowledgeOperationRepository::map);
    }

    private static Query query(String clientId, Filter filter) {
        StringBuilder where = new StringBuilder("WHERE client_id = :clientId");
        MapSqlParameterSource params = new MapSqlParameterSource("clientId", clientId);
        if (filter != null) {
            append(where, params, "created_at >= :from", "from", filter.from());
            append(where, params, "created_at < :to", "to", filter.to());
            append(where, params, "actor_id = :actorId", "actorId", blankToNull(filter.actorId()));
            append(where, params, "operation_type = :operationType", "operationType",
                    blankToNull(filter.operationType()));
            append(where, params, "response_status = :status", "status", blankToNull(filter.status()));
            append(where, params, "source_id = :sourceId", "sourceId", blankToNull(filter.sourceId()));
            append(where, params, "trace_id = :traceId", "traceId", blankToNull(filter.traceId()));
        }
        return new Query(where.toString(), params);
    }

    private static void append(StringBuilder where, MapSqlParameterSource params,
                               String predicate, String name, Object value) {
        if (value != null) {
            where.append(" AND ").append(predicate);
            params.addValue(name, value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static MapSqlParameterSource copy(MapSqlParameterSource source) {
        MapSqlParameterSource copy = new MapSqlParameterSource();
        source.getValues().forEach(copy::addValue);
        return copy;
    }

    private static KnowledgeOperation map(ResultSet rs, int row) throws SQLException {
        Number resultCount = (Number) rs.getObject("result_count");
        Number tokenConsumed = (Number) rs.getObject("token_consumed");
        return new KnowledgeOperation(
                rs.getString("id"), rs.getString("trace_id"), rs.getString("client_id"),
                rs.getString("actor_id"), rs.getString("actor_type"), rs.getString("operation_type"),
                rs.getString("source_id"), rs.getString("version_id"), rs.getString("run_id"),
                rs.getString("session_id"), rs.getString("request_summary_json"),
                rs.getString("response_status"), rs.getString("error_code"), rs.getLong("duration_ms"),
                resultCount == null ? null : resultCount.intValue(),
                tokenConsumed == null ? null : tokenConsumed.longValue(),
                rs.getTimestamp("created_at").toInstant());
    }

    private record Query(String where, MapSqlParameterSource params) {}
}
