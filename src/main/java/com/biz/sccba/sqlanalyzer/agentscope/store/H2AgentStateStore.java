package com.biz.sccba.sqlanalyzer.agentscope.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Persistent H2 implementation of AgentScope's {@link AgentStateStore}
 * (docs/cloud-code-next-goal.md §3.6). Rows live in {@code agentscope.agent_state}, created by
 * the H2 Flyway baseline — this class performs no DDL. State is keyed by
 * {@code (user_id = authenticated clientId, session_id, state_key, item_index)} so sessions are
 * isolated per client and list-valued states keep their order. Data survives restarts: nothing
 * here is in-memory.
 */
public final class H2AgentStateStore implements AgentStateStore {

    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public H2AgentStateStore(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    @Override
    public void save(String userId, String sessionId, String key, State state) {
        withConnection(connection -> {
            deleteItems(connection, userId, sessionId, key);
            insertItem(connection, userId, sessionId, key, 0, write(state));
        });
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> states) {
        withConnection(connection -> {
            deleteItems(connection, userId, sessionId, key);
            int index = 0;
            for (State state : states) {
                insertItem(connection, userId, sessionId, key, index++, write(state));
            }
        });
    }

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        return withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT state_data FROM agentscope.agent_state "
                            + "WHERE user_id = ? AND session_id = ? AND state_key = ? AND item_index = 0")) {
                ps.setString(1, userId);
                ps.setString(2, sessionId);
                ps.setString(3, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(read(rs.getString(1), type));
                }
            }
        });
    }

    @Override
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> type) {
        return withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT state_data FROM agentscope.agent_state "
                            + "WHERE user_id = ? AND session_id = ? AND state_key = ? ORDER BY item_index")) {
                ps.setString(1, userId);
                ps.setString(2, sessionId);
                ps.setString(3, key);
                List<T> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(read(rs.getString(1), type));
                }
                return out;
            }
        });
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM agentscope.agent_state WHERE user_id = ? AND session_id = ?")) {
                ps.setString(1, userId);
                ps.setString(2, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getLong(1) > 0;
                }
            }
        });
    }

    @Override
    public void delete(String userId, String sessionId) {
        withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM agentscope.agent_state WHERE user_id = ? AND session_id = ?")) {
                ps.setString(1, userId);
                ps.setString(2, sessionId);
                ps.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        withConnection(connection -> {
            deleteItems(connection, userId, sessionId, key);
            return null;
        });
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        return withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT DISTINCT session_id FROM agentscope.agent_state WHERE user_id = ?")) {
                ps.setString(1, userId);
                Set<String> out = new LinkedHashSet<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(rs.getString(1));
                }
                return out;
            }
        });
    }

    // ---- internals ----

    private void deleteItems(Connection connection, String userId, String sessionId, String key)
            throws java.sql.SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM agentscope.agent_state WHERE user_id = ? AND session_id = ? AND state_key = ?")) {
            ps.setString(1, userId);
            ps.setString(2, sessionId);
            ps.setString(3, key);
            ps.executeUpdate();
        }
    }

    private void insertItem(Connection connection, String userId, String sessionId, String key,
                            int itemIndex, String json) throws java.sql.SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO agentscope.agent_state(user_id, session_id, state_key, item_index, state_data, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            Timestamp now = Timestamp.from(Instant.now());
            ps.setString(1, userId);
            ps.setString(2, sessionId);
            ps.setString(3, key);
            ps.setInt(4, itemIndex);
            ps.setString(5, json);
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            ps.executeUpdate();
        }
    }

    private String write(State state) {
        try {
            return mapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("AgentState 序列化失败", e);
        }
    }

    private <T extends State> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("AgentState 反序列化失败", e);
        }
    }

    private void withConnection(SqlConsumer consumer) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                consumer.accept(connection);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("AgentScope H2 状态存储访问失败", e);
        }
    }

    private <T> T withConnection(SqlFunction<T> function) {
        try (Connection connection = dataSource.getConnection()) {
            return function.apply(connection);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("AgentScope H2 状态存储访问失败", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("AgentScope H2 状态存储访问失败", e);
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws Exception;
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection connection) throws Exception;
    }
}
