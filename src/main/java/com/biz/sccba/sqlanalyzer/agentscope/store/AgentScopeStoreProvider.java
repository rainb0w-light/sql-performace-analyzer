package com.biz.sccba.sqlanalyzer.agentscope.store;

import io.agentscope.harness.agent.DistributedStore;

import javax.sql.DataSource;

/**
 * Chooses the AgentScope {@link DistributedStore} for the active management database
 * (docs/cloud-code-next-goal.md §3.6). PostgreSQL uses the official
 * {@code PostgresDistributedStore}; H2 uses a persistent {@code AgentStateStore + BaseStore}
 * combination backed by Flyway-managed tables in the {@code agentscope} schema — never an
 * in-memory store, so session state survives restarts on both databases and is isolated by
 * {@code (userId = authenticated clientId, sessionId)}.
 */
public interface AgentScopeStoreProvider {

    DistributedStore create(DataSource dataSource);
}
