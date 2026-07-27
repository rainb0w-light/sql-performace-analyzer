-- Portable forward migration (docs/cloud-code-next-goal.md §3.6): the AgentScope
-- remote-workspace KV table used by the product's JdbcBaseStore on BOTH management databases.
-- The official PostgresBaseStore of agentscope-extensions-postgresql 2.0.0 is not used (its
-- upsert SQL is syntactically invalid); the equivalent table schema lives here, under Flyway
-- control, in the framework's agentscope schema. AgentState tables remain provider-managed
-- (official PostgresAgentStateStore on PostgreSQL; H2 baseline on H2).
-- Rollback: drop table agentscope.kv_store.

CREATE SCHEMA IF NOT EXISTS agentscope;

CREATE TABLE IF NOT EXISTS agentscope.kv_store (
    namespace_path VARCHAR(2048) NOT NULL,
    item_key VARCHAR(255) NOT NULL,
    value_json TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT,
    PRIMARY KEY (namespace_path, item_key)
);
