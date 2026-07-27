package com.biz.sccba.sqlanalyzer.agentscope.store;

import io.agentscope.core.state.State;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentScope store contract (docs/cloud-code-next-goal.md §3.6): the identical suite must pass
 * on the official PostgreSQL store and on the persistent H2 store combination. Covers state
 * round-trip, (userId = clientId, sessionId) isolation, ordered list state, BaseStore
 * compare-and-set versioning, and — crucially — recovery across a store "restart" (the pool is
 * closed and re-created over the same durable database; in-memory fakes would fail this).
 */
public abstract class AgentScopeStoreContractTestBase {

    /** A fresh store over the SAME durable database (simulates a process restart). */
    abstract DistributedStore newStore();

    /** Closes the current connection pool. */
    abstract void shutdown();

    /** Simple Jackson-friendly state for round-trip assertions. */
    public static class TestState implements State {
        private String text;
        private int n;

        public TestState() {
        }

        public TestState(String text, int n) {
            this.text = text;
            this.n = n;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public int getN() {
            return n;
        }

        public void setN(int n) {
            this.n = n;
        }
    }

    @Test
    void stateRoundTripAndCrossSessionIsolation() {
        DistributedStore store = newStore();
        String user = "client_" + UUID.randomUUID();
        String other = "client_" + UUID.randomUUID();
        String session = "session_shared_name";

        store.agentStateStore().save(user, session, "memory", new TestState("会话上下文", 7));

        var loaded = store.agentStateStore().get(user, session, "memory", TestState.class);
        assertTrue(loaded.isPresent());
        assertEquals("会话上下文", loaded.get().getText());
        assertEquals(7, loaded.get().getN());

        assertTrue(store.agentStateStore().exists(user, session));
        assertFalse(store.agentStateStore().exists(other, session),
                "the same session name under another client must be isolated (userId is the clientId)");
        assertTrue(store.agentStateStore().get(other, session, "memory", TestState.class).isEmpty());

        assertEquals(List.of(session), List.copyOf(store.agentStateStore().listSessionIds(user)));
        assertTrue(store.agentStateStore().listSessionIds(other).isEmpty());

        store.agentStateStore().delete(user, session);
        assertFalse(store.agentStateStore().exists(user, session));
    }

    @Test
    void listStateKeepsOrderAndOverwrites() {
        DistributedStore store = newStore();
        String user = "client_" + UUID.randomUUID();
        String session = "session_" + UUID.randomUUID();

        store.agentStateStore().save(user, session, "msgs",
                List.of(new TestState("first", 1), new TestState("second", 2), new TestState("third", 3)));
        var list = store.agentStateStore().getList(user, session, "msgs", TestState.class);
        assertEquals(List.of("first", "second", "third"), list.stream().map(TestState::getText).toList());

        // overwrite replaces the whole list, no stale trailing items
        store.agentStateStore().save(user, session, "msgs", List.of(new TestState("only", 9)));
        list = store.agentStateStore().getList(user, session, "msgs", TestState.class);
        assertEquals(1, list.size());
        assertEquals("only", list.get(0).getText());
    }

    @Test
    void baseStoreVersionCasAndSearch() {
        DistributedStore store = newStore();
        BaseStore kv = store.baseStore();
        List<String> ns = List.of("users", "client_" + UUID.randomUUID(), "memory");

        assertNull(kv.get(ns, "MEMORY.md"));
        kv.put(ns, "MEMORY.md", Map.of("content", "偏好：报告要中文"));
        StoreItem item = kv.get(ns, "MEMORY.md");
        assertNotNull(item);
        assertEquals("偏好：报告要中文", item.value().get("content"));
        long version = item.version();

        // compare-and-set: a stale expected version must lose
        assertFalse(kv.putIfVersion(ns, "MEMORY.md", Map.of("content", "stale write"), version - 1));
        assertEquals("偏好：报告要中文", kv.get(ns, "MEMORY.md").value().get("content"));
        assertTrue(kv.putIfVersion(ns, "MEMORY.md", Map.of("content", "fresh write"), version));
        assertEquals("fresh write", kv.get(ns, "MEMORY.md").value().get("content"));

        kv.put(List.of("users"), "other-key", Map.of("x", 1));
        assertFalse(kv.search(List.of("users"), 0, 100).isEmpty());

        kv.delete(ns, "MEMORY.md");
        assertNull(kv.get(ns, "MEMORY.md"));
    }

    @Test
    void stateSurvivesRestart() {
        String user = "client_" + UUID.randomUUID();
        String session = "session_" + UUID.randomUUID();
        List<String> ns = List.of("users", user, "memory");

        DistributedStore first = newStore();
        first.agentStateStore().save(user, session, "memory", new TestState("重启前的状态", 42));
        first.baseStore().put(ns, "MEMORY.md", Map.of("content", "重启前的记忆"));
        shutdown();

        // Same durable database, brand-new store instance = a process restart.
        DistributedStore second = newStore();
        var loaded = second.agentStateStore().get(user, session, "memory", TestState.class);
        assertTrue(loaded.isPresent(), "AgentState must be recovered after restart (no in-memory store)");
        assertEquals("重启前的状态", loaded.get().getText());
        assertEquals(42, loaded.get().getN());
        StoreItem item = second.baseStore().get(ns, "MEMORY.md");
        assertNotNull(item, "long-term memory KV must be recovered after restart");
        assertEquals("重启前的记忆", item.value().get("content"));
        shutdown();
    }
}
