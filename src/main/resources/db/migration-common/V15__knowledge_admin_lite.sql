-- Lightweight knowledge administration control plane.
-- Portable forward migration shared by H2 and PostgreSQL.

ALTER TABLE sql_analyzer.knowledge_version ADD COLUMN content_hash VARCHAR(64);
ALTER TABLE sql_analyzer.knowledge_version ADD COLUMN file_name VARCHAR(500);
ALTER TABLE sql_analyzer.knowledge_version ADD COLUMN media_type VARCHAR(200);
ALTER TABLE sql_analyzer.knowledge_version ADD COLUMN file_size BIGINT;
ALTER TABLE sql_analyzer.knowledge_version ADD COLUMN chunk_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sql_analyzer.knowledge_version ADD COLUMN processing_error_code VARCHAR(80);
ALTER TABLE sql_analyzer.knowledge_version ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX uq_knowledge_version_source_hash
    ON sql_analyzer.knowledge_version(source_id, content_hash);
CREATE INDEX idx_knowledge_version_status
    ON sql_analyzer.knowledge_version(source_id, status, created_at DESC);
CREATE INDEX idx_artifact_client_source_hash
    ON sql_analyzer.artifact(client_id, source_type, sha256);

CREATE TABLE sql_analyzer.knowledge_operation_log (
    id VARCHAR(64) PRIMARY KEY,
    trace_id VARCHAR(100) NOT NULL,
    client_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.client(id),
    actor_id VARCHAR(100) NOT NULL,
    actor_type VARCHAR(40) NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    source_id VARCHAR(64),
    version_id VARCHAR(64),
    run_id VARCHAR(64),
    session_id VARCHAR(64),
    request_summary_json TEXT NOT NULL DEFAULT '{}',
    response_status VARCHAR(20) NOT NULL,
    error_code VARCHAR(80),
    duration_ms BIGINT NOT NULL DEFAULT 0,
    result_count INTEGER,
    token_consumed BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_knowledge_operation_client_time
    ON sql_analyzer.knowledge_operation_log(client_id, created_at DESC, id DESC);
CREATE INDEX idx_knowledge_operation_client_type
    ON sql_analyzer.knowledge_operation_log(client_id, operation_type, created_at DESC);
CREATE INDEX idx_knowledge_operation_trace
    ON sql_analyzer.knowledge_operation_log(client_id, trace_id);
