CREATE TABLE IF NOT EXISTS v2_client (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(50) NOT NULL,
    device_id VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS v2_client_token (
    id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL REFERENCES v2_client(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    token_prefix VARCHAR(24) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_v2_client_token_active ON v2_client_token(token_hash, status);

CREATE TABLE IF NOT EXISTS v2_analysis_session (
    id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL REFERENCES v2_client(id),
    title VARCHAR(300) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_v2_session_client_updated
    ON v2_analysis_session(client_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS v2_conversation_message (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES v2_analysis_session(id),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    message_type VARCHAR(50) NOT NULL DEFAULT 'TEXT',
    run_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_v2_message_session_created
    ON v2_conversation_message(session_id, created_at);

CREATE TABLE IF NOT EXISTS v2_agent_run (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES v2_analysis_session(id),
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    model_name VARCHAR(200),
    context_snapshot_id VARCHAR(64),
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS v2_agent_job (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES v2_agent_run(id),
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    payload TEXT NOT NULL DEFAULT '{}',
    leased_by VARCHAR(200),
    lease_until TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_v2_job_poll
    ON v2_agent_job(status, created_at);

CREATE TABLE IF NOT EXISTS v2_run_event (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES v2_agent_run(id),
    type VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_v2_run_event_run_id ON v2_run_event(run_id, id);

CREATE TABLE IF NOT EXISTS v2_artifact (
    id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL REFERENCES v2_client(id),
    session_id VARCHAR(64) REFERENCES v2_analysis_session(id),
    source_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(500),
    media_type VARCHAR(200),
    sha256 CHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'INGESTED',
    metadata TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_v2_artifact_client_created
    ON v2_artifact(client_id, created_at DESC);

CREATE TABLE IF NOT EXISTS v2_artifact_content (
    artifact_id VARCHAR(64) NOT NULL REFERENCES v2_artifact(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    content BYTEA NOT NULL,
    PRIMARY KEY (artifact_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS v2_document (
    id VARCHAR(64) PRIMARY KEY,
    artifact_id VARCHAR(64) NOT NULL REFERENCES v2_artifact(id),
    document_type VARCHAR(80) NOT NULL,
    parser_name VARCHAR(120) NOT NULL,
    parser_version VARCHAR(40) NOT NULL,
    normalized_text TEXT NOT NULL,
    structured_data TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(30) NOT NULL DEFAULT 'PARSED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS v2_document_chunk (
    id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL REFERENCES v2_document(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    chunk_type VARCHAR(80) NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    metadata TEXT NOT NULL DEFAULT '{}',
    UNIQUE(document_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS v2_recommendation (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES v2_agent_run(id),
    session_id VARCHAR(64) NOT NULL REFERENCES v2_analysis_session(id),
    type VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    problem TEXT NOT NULL DEFAULT '',
    impact TEXT NOT NULL DEFAULT '',
    priority VARCHAR(30) NOT NULL DEFAULT 'MEDIUM',
    evidence TEXT NOT NULL DEFAULT '{}',
    suggested_sql TEXT,
    suggested_ddl TEXT,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PROPOSED',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS v2_recommendation_feedback (
    id VARCHAR(64) PRIMARY KEY,
    recommendation_id VARCHAR(64) NOT NULL REFERENCES v2_recommendation(id),
    client_id VARCHAR(64) NOT NULL REFERENCES v2_client(id),
    decision VARCHAR(20) NOT NULL,
    category VARCHAR(80),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT recommendation_decision_allowed CHECK (decision IN ('ACCEPTED','REJECTED')),
    CONSTRAINT rejected_feedback_requires_reason CHECK (
        decision <> 'REJECTED' OR length(trim(coalesce(reason, ''))) > 0
    )
);
