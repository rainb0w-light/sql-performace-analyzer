-- Portable forward migration (docs/cloud-code-next-goal.md §3.4/§5): every business resource
-- table must trace back to a client. index_def / shard_def / metadata_conflict were created
-- without any client linkage; this migration adds tenant ownership to them on BOTH management
-- databases (applied after the deployed PostgreSQL history and after the H2 baseline).
--
-- Legacy rows already present in deployed PostgreSQL databases are backfilled to a fixed system
-- client so the NOT NULL + FK constraint can be enforced without data loss. New rows always
-- carry the authenticated client id (enforced by the repositories and contract tests).
-- Rollback: drop the three client_id columns and the system client row.

INSERT INTO sql_analyzer.client (id, name, type, created_at)
SELECT 'client_system', 'System (legacy metadata owner)', 'SYSTEM', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sql_analyzer.client WHERE id = 'client_system');

-- index_def

ALTER TABLE sql_analyzer.index_def ADD COLUMN IF NOT EXISTS client_id VARCHAR(64);
UPDATE sql_analyzer.index_def SET client_id = 'client_system' WHERE client_id IS NULL;
ALTER TABLE sql_analyzer.index_def ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE sql_analyzer.index_def
    ADD CONSTRAINT fk_index_def_client FOREIGN KEY (client_id) REFERENCES sql_analyzer.client(id);
CREATE INDEX IF NOT EXISTS idx_index_def_client_table ON sql_analyzer.index_def(client_id, table_name);

-- shard_def

ALTER TABLE sql_analyzer.shard_def ADD COLUMN IF NOT EXISTS client_id VARCHAR(64);
UPDATE sql_analyzer.shard_def SET client_id = 'client_system' WHERE client_id IS NULL;
ALTER TABLE sql_analyzer.shard_def ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE sql_analyzer.shard_def
    ADD CONSTRAINT fk_shard_def_client FOREIGN KEY (client_id) REFERENCES sql_analyzer.client(id);
CREATE INDEX IF NOT EXISTS idx_shard_def_client_logical ON sql_analyzer.shard_def(client_id, logical_table);

-- metadata_conflict

ALTER TABLE sql_analyzer.metadata_conflict ADD COLUMN IF NOT EXISTS client_id VARCHAR(64);
UPDATE sql_analyzer.metadata_conflict SET client_id = 'client_system' WHERE client_id IS NULL;
ALTER TABLE sql_analyzer.metadata_conflict ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE sql_analyzer.metadata_conflict
    ADD CONSTRAINT fk_metadata_conflict_client FOREIGN KEY (client_id) REFERENCES sql_analyzer.client(id);
CREATE INDEX IF NOT EXISTS idx_metadata_conflict_client_status ON sql_analyzer.metadata_conflict(client_id, status);
