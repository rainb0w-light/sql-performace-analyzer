package com.biz.sccba.sqlanalyzer.agentscope.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.harness.agent.DistributedStore;

import javax.sql.DataSource;

/**
 * Composes the persistent H2 state store and base store into one {@link DistributedStore}.
 * Both stores read/write Flyway-managed tables in the {@code agentscope} schema, so H2 session
 * state and USER-scoped long-term memory survive restarts exactly like the PostgreSQL path.
 */
public final class H2AgentScopeStoreProvider implements AgentScopeStoreProvider {

    @Override
    public DistributedStore create(DataSource dataSource) {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return DistributedStore.builder()
                .agentStateStore(new H2AgentStateStore(dataSource, mapper))
                .baseStore(new JdbcBaseStore(dataSource, mapper))
                .build();
    }
}
