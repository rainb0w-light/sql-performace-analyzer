-- Phase 3: structured business knowledge, index/sharding metadata, datasource profiles and
-- read-only profiling snapshots (development-guide §7). Every fact carries source + version +
-- validity; only rows of the PUBLISHED version of a source are active. Rollback: docs/migrations/V7-rollback.md.

-- ---------- Business knowledge ----------

CREATE TABLE sql_analyzer.knowledge_source (
    id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.client(id),
    name VARCHAR(300) NOT NULL,
    source_type VARCHAR(40) NOT NULL DEFAULT 'EXCEL',
    current_version_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sql_analyzer.knowledge_version (
    id VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.knowledge_source(id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',           -- DRAFT | PUBLISHED | ROLLED_BACK | FAILED
    artifact_id VARCHAR(64) REFERENCES sql_analyzer.artifact(id),
    preview_json TEXT NOT NULL DEFAULT '{}',                -- parsed preview (rebuildable from artifact)
    error_json TEXT NOT NULL DEFAULT '[]',                  -- row-level errors: sheet/row/column/reason
    published_by VARCHAR(64),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_id, version_no)
);

CREATE INDEX idx_knowledge_version_source ON sql_analyzer.knowledge_version(source_id, version_no DESC);

CREATE TABLE sql_analyzer.kb_table_def (
    id VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.knowledge_source(id) ON DELETE CASCADE,
    version_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.knowledge_version(id) ON DELETE CASCADE,
    datasource VARCHAR(200),
    schema_name VARCHAR(200),
    table_name VARCHAR(300) NOT NULL,
    business_name VARCHAR(300),
    purpose TEXT,
    owner VARCHAR(200),
    data_domain VARCHAR(200),
    sheet_locator VARCHAR(200),                             -- provenance: Sheet/row
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kb_table_lookup ON sql_analyzer.kb_table_def(table_name, active);

CREATE TABLE sql_analyzer.kb_column_def (
    id VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.knowledge_source(id) ON DELETE CASCADE,
    version_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.knowledge_version(id) ON DELETE CASCADE,
    table_name VARCHAR(300) NOT NULL,
    column_name VARCHAR(300) NOT NULL,
    business_meaning TEXT,
    data_type VARCHAR(100),
    enum_domain VARCHAR(300),
    is_sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    sensitivity_policy VARCHAR(20) NOT NULL DEFAULT 'HASHED', -- PLAINTEXT | HASHED | OMITTED
    sheet_locator VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kb_column_lookup ON sql_analyzer.kb_column_def(table_name, column_name, active);

CREATE TABLE sql_analyzer.kb_rule (
    id VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.knowledge_source(id) ON DELETE CASCADE,
    version_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.knowledge_version(id) ON DELETE CASCADE,
    rule_key VARCHAR(200),
    target VARCHAR(300),
    description TEXT,
    constraint_expr TEXT,
    priority INTEGER NOT NULL DEFAULT 100,
    effective_from TIMESTAMPTZ,
    sheet_locator VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kb_rule_lookup ON sql_analyzer.kb_rule(target, active);

CREATE TABLE sql_analyzer.kb_enum_value (
    id VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.knowledge_source(id) ON DELETE CASCADE,
    version_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.knowledge_version(id) ON DELETE CASCADE,
    enum_code VARCHAR(300) NOT NULL,
    display_name VARCHAR(300),
    meaning TEXT,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    sheet_locator VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kb_enum_lookup ON sql_analyzer.kb_enum_value(enum_code, active);

CREATE TABLE sql_analyzer.kb_alias (
    id VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.knowledge_source(id) ON DELETE CASCADE,
    version_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.knowledge_version(id) ON DELETE CASCADE,
    alias_type VARCHAR(40) NOT NULL,                        -- TABLE | COLUMN | TERM
    alias_name VARCHAR(300) NOT NULL,
    target_name VARCHAR(300) NOT NULL,
    sheet_locator VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kb_alias_lookup ON sql_analyzer.kb_alias(alias_name, active);

-- ---------- Index / sharding metadata ----------

CREATE TABLE sql_analyzer.index_def (
    id VARCHAR(64) PRIMARY KEY,
    datasource VARCHAR(200),
    schema_name VARCHAR(200),
    table_name VARCHAR(300) NOT NULL,
    index_name VARCHAR(300) NOT NULL,
    index_type VARCHAR(40) NOT NULL DEFAULT 'NORMAL',       -- NORMAL | UNIQUE | PRIMARY | FUNCTIONAL | PREFIX
    columns_json TEXT NOT NULL DEFAULT '[]',                -- ordered [{"column":..,"direction":"ASC|DESC"}]
    cardinality BIGINT,
    usage_count BIGINT,
    source VARCHAR(40) NOT NULL DEFAULT 'MANUAL',           -- SYSTEM_CATALOG | GOLDENDB_ADMIN | EXCEL | MANUAL
    confirmed_by VARCHAR(200),
    valid_from TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 1,
    checksum CHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_index_def_lookup ON sql_analyzer.index_def(table_name);

CREATE TABLE sql_analyzer.shard_def (
    id VARCHAR(64) PRIMARY KEY,
    datasource VARCHAR(200),
    logical_table VARCHAR(300) NOT NULL,
    physical_pattern VARCHAR(300),
    shard_key VARCHAR(300),
    secondary_shard_key VARCHAR(300),
    algorithm VARCHAR(200),
    routing_expr TEXT,
    topology_json TEXT NOT NULL DEFAULT '{}',
    source VARCHAR(40) NOT NULL DEFAULT 'MANUAL',           -- SYSTEM_CATALOG | GOLDENDB_ADMIN | EXCEL | MANUAL
    confirmed_by VARCHAR(200),
    valid_from TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shard_def_lookup ON sql_analyzer.shard_def(logical_table);

-- Auto-collected metadata never overwrites manual records: differences land here for confirmation.
CREATE TABLE sql_analyzer.metadata_conflict (
    id VARCHAR(64) PRIMARY KEY,
    entity_type VARCHAR(40) NOT NULL,                       -- INDEX | SHARD
    entity_key VARCHAR(600) NOT NULL,
    existing_json TEXT NOT NULL DEFAULT '{}',
    incoming_json TEXT NOT NULL DEFAULT '{}',
    source VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',          -- PENDING | RESOLVED | DISMISSED
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_metadata_conflict_status ON sql_analyzer.metadata_conflict(status, created_at);

-- ---------- Datasource profiles (read-only targets; secret via env reference) ----------

CREATE TABLE sql_analyzer.datasource_profile (
    id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.client(id),
    name VARCHAR(300) NOT NULL,
    dialect VARCHAR(40) NOT NULL DEFAULT 'MYSQL',           -- MYSQL | GOLDENDB
    jdbc_url VARCHAR(600) NOT NULL,
    username VARCHAR(200),
    credential_env VARCHAR(200),                            -- env var name holding the password; never stored
    read_only BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------- Profiling ----------

CREATE TABLE sql_analyzer.profiling_job (
    id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.client(id),
    datasource_profile_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.datasource_profile(id),
    config_json TEXT NOT NULL DEFAULT '{}',                 -- schema/table scope, sampling budget, periods
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',           -- QUEUED | RUNNING | COMPLETED | FAILED | CANCELLED
    leased_by VARCHAR(200),
    lease_until TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_profiling_job_poll ON sql_analyzer.profiling_job(status, created_at);

CREATE TABLE sql_analyzer.profile_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.profiling_job(id),
    datasource_profile_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.datasource_profile(id),
    status VARCHAR(30) NOT NULL DEFAULT 'RUNNING',
    config_json TEXT NOT NULL DEFAULT '{}',
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ
);

CREATE TABLE sql_analyzer.profile_column_stat (
    id VARCHAR(64) PRIMARY KEY,
    snapshot_id VARCHAR(64) NOT NULL REFERENCES sql_analyzer.profile_snapshot(id) ON DELETE CASCADE,
    schema_name VARCHAR(200),
    table_name VARCHAR(300) NOT NULL,
    column_name VARCHAR(300) NOT NULL,
    null_ratio DOUBLE PRECISION,
    approx_distinct BIGINT,
    min_value TEXT,
    max_value TEXT,
    top_k_json TEXT NOT NULL DEFAULT '[]',                  -- values per sensitivity policy (plain/hashed/omitted)
    buckets_json TEXT NOT NULL DEFAULT '[]',
    quantiles_json TEXT NOT NULL DEFAULT '[]',
    collected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_profile_column_stat_snapshot ON sql_analyzer.profile_column_stat(snapshot_id);
