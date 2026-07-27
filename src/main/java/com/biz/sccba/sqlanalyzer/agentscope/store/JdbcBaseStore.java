package com.biz.sccba.sqlanalyzer.agentscope.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Portable JDBC implementation of AgentScope's remote-workspace {@link BaseStore}
 * (docs/cloud-code-next-goal.md §3.6), backed by {@code agentscope.kv_store} (Flyway-managed,
 * no DDL here — the table is created by the common forward migration on PostgreSQL and by the
 * H2 baseline on H2). Plain standard SQL (row-lock upsert, LIMIT/OFFSET search) runs identically
 * on both management databases.
 *
 * <p>Used instead of the official {@code PostgresBaseStore} on PostgreSQL as well: the official
 * 2.0.0 upsert statement contains a double-comma syntax error and every put fails with
 * "syntax error at or near ','". The official {@code PostgresAgentStateStore} remains in use
 * for AgentState. Values are JSON maps; {@code version} provides the compare-and-set semantics
 * USER-scoped long-term memory relies on (putIfVersion).
 */
public final class JdbcBaseStore implements BaseStore {

    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public JdbcBaseStore(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    @Override
    public StoreItem get(List<String> path, String key) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT value_json, version FROM agentscope.kv_store WHERE namespace_path = ? AND item_key = ?")) {
            ps.setString(1, namespace(path));
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new StoreItem(key, read(rs.getString(1)), rs.getLong(2));
            }
        } catch (Exception e) {
            throw new IllegalStateException("AgentScope H2 KV 存储访问失败", e);
        }
    }

    @Override
    public void put(List<String> path, String key, Map<String, Object> value) {
        try (Connection c = dataSource.getConnection()) {
            boolean original = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (existsLocked(c, path, key)) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE agentscope.kv_store SET value_json = ?, version = version + 1, updated_at = ? "
                                    + "WHERE namespace_path = ? AND item_key = ?")) {
                        ps.setString(1, write(value));
                        ps.setLong(2, System.currentTimeMillis());
                        ps.setString(3, namespace(path));
                        ps.setString(4, key);
                        ps.executeUpdate();
                    }
                } else {
                    insert(c, path, key, value);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(original);
            }
        } catch (Exception e) {
            throw new IllegalStateException("AgentScope H2 KV 存储访问失败", e);
        }
    }

    @Override
    public boolean putIfVersion(List<String> path, String key, Map<String, Object> value, long expectedVersion) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE agentscope.kv_store SET value_json = ?, version = version + 1, updated_at = ? "
                             + "WHERE namespace_path = ? AND item_key = ? AND version = ?")) {
            ps.setString(1, write(value));
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, namespace(path));
            ps.setString(4, key);
            ps.setLong(5, expectedVersion);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            throw new IllegalStateException("AgentScope H2 KV 存储访问失败", e);
        }
    }

    @Override
    public List<StoreItem> search(List<String> path, int offset, int limit) {
        String prefix = namespace(path);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT item_key, value_json, version FROM agentscope.kv_store "
                             + "WHERE namespace_path = ? OR namespace_path LIKE ? "
                             + "ORDER BY namespace_path, item_key LIMIT ? OFFSET ?")) {
            ps.setString(1, prefix);
            ps.setString(2, prefix + "/%");
            ps.setInt(3, Math.max(1, limit));
            ps.setInt(4, Math.max(0, offset));
            List<StoreItem> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new StoreItem(rs.getString(1), read(rs.getString(2)), rs.getLong(3)));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("AgentScope H2 KV 存储访问失败", e);
        }
    }

    @Override
    public void delete(List<String> path, String key) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM agentscope.kv_store WHERE namespace_path = ? AND item_key = ?")) {
            ps.setString(1, namespace(path));
            ps.setString(2, key);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("AgentScope H2 KV 存储访问失败", e);
        }
    }

    private boolean existsLocked(Connection c, List<String> path, String key) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM agentscope.kv_store WHERE namespace_path = ? AND item_key = ? FOR UPDATE")) {
            ps.setString(1, namespace(path));
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insert(Connection c, List<String> path, String key, Map<String, Object> value)
            throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO agentscope.kv_store(namespace_path, item_key, value_json, version, updated_at) "
                        + "VALUES (?, ?, ?, 0, ?)")) {
            ps.setString(1, namespace(path));
            ps.setString(2, key);
            ps.setString(3, write(value));
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private static String namespace(List<String> path) {
        return String.join("/", path == null ? List.of() : path);
    }

    private String write(Map<String, Object> value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalStateException("KV 值序列化失败", e);
        }
    }

    private Map<String, Object> read(String json) {
        try {
            return mapper.readValue(json == null ? "{}" : json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("KV 值反序列化失败", e);
        }
    }
}
