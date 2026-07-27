-- Forward-only isolation hardening. Historical migrations remain immutable.
ALTER TABLE sql_analyzer.shard_def
    ADD COLUMN IF NOT EXISTS schema_name VARCHAR(200) NOT NULL DEFAULT 'public';

CREATE INDEX IF NOT EXISTS idx_index_def_scope
    ON sql_analyzer.index_def(client_id, datasource, schema_name, table_name, index_name);

CREATE INDEX IF NOT EXISTS idx_shard_def_scope
    ON sql_analyzer.shard_def(client_id, datasource, schema_name, logical_table);

CREATE INDEX IF NOT EXISTS idx_profile_snapshot_scope
    ON sql_analyzer.profile_snapshot(datasource_profile_id, status, started_at);
