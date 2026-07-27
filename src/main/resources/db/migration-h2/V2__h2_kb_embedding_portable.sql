-- H2-only portable embedding store for the vendor-neutral KnowledgeRetriever
-- (docs/cloud-code-next-goal.md §3.3/§5.4). PostgreSQL uses the PgVector adapter instead, so
-- this table has no PostgreSQL counterpart and is excluded from SchemaParityTest by design.
-- Embeddings are stored as portable JSON float arrays; similarity is computed in the JVM over
-- the owning client's candidate set.

CREATE TABLE IF NOT EXISTS sql_analyzer.kb_embedding_portable (
    id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    source_id VARCHAR(64),
    version_no INTEGER,
    kind VARCHAR(40),
    name VARCHAR(600),
    locator VARCHAR(300),
    content TEXT NOT NULL,
    embedding TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_embedding_portable_client
    ON sql_analyzer.kb_embedding_portable(client_id);
