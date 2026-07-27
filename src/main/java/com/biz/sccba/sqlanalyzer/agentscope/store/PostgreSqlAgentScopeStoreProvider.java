package com.biz.sccba.sqlanalyzer.agentscope.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.harness.agent.DistributedStore;

import javax.sql.DataSource;

/**
 * PostgreSQL composition (docs/cloud-code-next-goal.md §3.6):
 * <ul>
 *   <li>AgentState — the official {@link PostgresAgentStateStore} (self-creates
 *       {@code agentscope.agentscope_sessions}; keyed by (userId, sessionId));</li>
 *   <li>remote-workspace KV — the portable {@link JdbcBaseStore} over the Flyway-managed
 *       {@code agentscope.kv_store} table.</li>
 * </ul>
 *
 * <p>The official {@code PostgresBaseStore} of agentscope-extensions-postgresql 2.0.0 is NOT
 * used: its upsert statement contains a double-comma SQL syntax error, so every
 * {@code put} fails (verified against the published 2.0.0 sources). Swapping only the KV store
 * keeps the official AgentState behavior while making USER-scoped long-term memory durable.
 */
public final class PostgreSqlAgentScopeStoreProvider implements AgentScopeStoreProvider {

    @Override
    public DistributedStore create(DataSource dataSource) {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return DistributedStore.builder()
                .agentStateStore(PostgresAgentStateStore.builder(dataSource).createIfNotExist(true).build())
                .baseStore(new JdbcBaseStore(dataSource, mapper))
                .build();
    }
}
