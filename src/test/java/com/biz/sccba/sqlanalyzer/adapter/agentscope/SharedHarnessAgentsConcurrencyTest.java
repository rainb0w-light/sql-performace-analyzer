package com.biz.sccba.sqlanalyzer.adapter.agentscope;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.remote.store.InMemoryStore;
import io.agentscope.harness.agent.memory.MemoryConfig;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 runtime contracts (development-guide §2.2), verified without an LLM or database:
 *
 * - one shared HarnessAgent per stable model configuration (NOT one agent per session);
 * - same (userId, sessionId) calls are serialized; different sessions run in parallel;
 * - session state persists in the shared store and is recovered by a fresh agent instance
 *   (restart simulation);
 * - long-term memory workspace is USER-scoped, with throttled flush and retention policy.
 *
 * The in-memory store pair stands in for PostgresDistributedStore (covered by the Docker-gated
 * IT); the framework serialization/recovery semantics are identical.
 */
class SharedHarnessAgentsConcurrencyTest {

    /**
     * A distributed store that happens to keep state in memory: HarnessAgent rejects the
     * framework's local InMemoryAgentStateStore together with a RemoteFilesystemSpec, so this
     * forwarding wrapper (not instanceof the local classes) stands in for PostgresAgentStateStore
     * while preserving identical save/restore semantics for the tests.
     */
    static final class DistributedInMemoryStateStore implements AgentStateStore {
        final InMemoryAgentStateStore delegate = new InMemoryAgentStateStore();

        @Override
        public void save(String userId, String sessionId, String key, State value) {
            delegate.save(userId, sessionId, key, value);
        }

        @Override
        public void save(String userId, String sessionId, String key, List<? extends State> values) {
            delegate.save(userId, sessionId, key, values);
        }

        @Override
        public <T extends State> java.util.Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
            return delegate.get(userId, sessionId, key, type);
        }

        @Override
        public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
            return delegate.getList(userId, sessionId, key, itemType);
        }

        @Override
        public boolean exists(String userId, String sessionId) {
            return delegate.exists(userId, sessionId);
        }

        @Override
        public void delete(String userId, String sessionId) {
            delegate.delete(userId, sessionId);
        }

        @Override
        public void delete(String userId, String sessionId, String key) {
            delegate.delete(userId, sessionId, key);
        }

        @Override
        public java.util.Set<String> listSessionIds(String userId) {
            return delegate.listSessionIds(userId);
        }
    }

    /** Canned Model: counts concurrency, records received message counts, can be held on a latch. */
    static final class RecordingModel implements Model {
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maxActive = new AtomicInteger();
        final AtomicInteger responses = new AtomicInteger();
        final List<Integer> receivedMessageCounts = Collections.synchronizedList(new ArrayList<>());
        final AtomicBoolean hold = new AtomicBoolean(false);
        final CountDownLatch released = new CountDownLatch(1);

        @Override
        public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            receivedMessageCounts.add(messages.size());
            return Flux.defer(() -> {
                        awaitIfHeld();
                        return Flux.just(ChatResponse.builder()
                                .id("resp_" + responses.incrementAndGet())
                                .content(List.of(TextBlock.builder().text("ok").build()))
                                .usage(new ChatUsage(10, 1, 0.0))
                                .finishReason("stop")
                                .build());
                    })
                    .doFinally(signal -> active.decrementAndGet());
        }

        private void awaitIfHeld() {
            if (!hold.get()) return;
            try {
                released.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public String getModelName() {
            return "recording-model";
        }
    }

    private static RuntimeContext ctx(String userId, String sessionId) {
        return RuntimeContext.builder().userId(userId).sessionId(sessionId).build();
    }

    record TestRig(SharedHarnessAgents shared, DistributedInMemoryStateStore stateStore, RecordingModel model) {}

    private static TestRig rig(RecordingModel model) {
        DistributedInMemoryStateStore stateStore = new DistributedInMemoryStateStore();
        InMemoryStore baseStore = new InMemoryStore();
        DistributedStore store = DistributedStore.builder()
                .agentStateStore(stateStore)
                .baseStore(baseStore)
                .build();
        SharedHarnessAgents shared = new SharedHarnessAgents(
                key -> model, new Toolkit(), store, IsolationScope.USER,
                m -> MemoryConfig.builder().model(m).flushTrigger(MemoryConfig.FlushTrigger.never()).build());
        return new TestRig(shared, stateStore, model);
    }

    @Test
    void oneSharedAgentPerModelConfiguration() {
        TestRig rig = rig(new RecordingModel());
        assertSame(rig.shared().agentFor("modelA"), rig.shared().agentFor("modelA"),
                "same model key must reuse one shared HarnessAgent instance");
        assertNotSame(rig.shared().agentFor("modelA"), rig.shared().agentFor("modelB"),
                "different model configurations get distinct instances");
    }

    @Test
    void sameSessionCallsAreSerialized() throws Exception {
        RecordingModel model = new RecordingModel();
        TestRig rig = rig(model);
        var agent = rig.shared().agentFor("m");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            model.hold.set(true);
            Future<Msg> first = pool.submit(() -> agent.call("first", ctx("user1", "session1"))
                    .block(Duration.ofSeconds(30)));
            awaitActive(model, 1);

            Future<Msg> second = pool.submit(() -> agent.call("second", ctx("user1", "session1"))
                    .block(Duration.ofSeconds(30)));
            Thread.sleep(500);
            assertEquals(1, model.active.get(),
                    "a second call on the SAME session must wait, not run concurrently");

            model.hold.set(false);
            model.released.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
            assertEquals(1, model.maxActive.get(), "same-session calls must never overlap");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void differentSessionsRunInParallel() throws Exception {
        RecordingModel model = new RecordingModel();
        TestRig rig = rig(model);
        var agent = rig.shared().agentFor("m");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            model.hold.set(true);
            Future<Msg> a = pool.submit(() -> agent.call("for A", ctx("user1", "sessionA"))
                    .block(Duration.ofSeconds(30)));
            Future<Msg> b = pool.submit(() -> agent.call("for B", ctx("user1", "sessionB"))
                    .block(Duration.ofSeconds(30)));
            awaitActive(model, 2);
            assertEquals(2, model.maxActive.get(),
                    "calls on DIFFERENT sessions of one shared agent must overlap");

            model.hold.set(false);
            model.released.countDown();
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sessionStateRecoversAcrossRestart() {
        RecordingModel model = new RecordingModel();
        DistributedInMemoryStateStore stateStore = new DistributedInMemoryStateStore();
        InMemoryStore baseStore = new InMemoryStore();
        DistributedStore store = DistributedStore.builder()
                .agentStateStore(stateStore).baseStore(baseStore).build();

        try (SharedHarnessAgents processA = new SharedHarnessAgents(k -> model, new Toolkit(), store)) {
            Msg r1 = processA.agentFor("m").call("hello one", ctx("user1", "session1"))
                    .block(Duration.ofSeconds(30));
            assertTrue(r1 != null && r1.getTextContent() != null);
        }
        assertTrue(stateStore.exists("user1", "session1"),
                "agent state must be persisted to the shared store after a call");

        // Simulated restart: a brand-new agent instance re-attaches to the same store.
        try (SharedHarnessAgents processB = new SharedHarnessAgents(k -> model, new Toolkit(), store)) {
            Msg r2 = processB.agentFor("m").call("hello two", ctx("user1", "session1"))
                    .block(Duration.ofSeconds(30));
            assertTrue(r2 != null);
        }

        List<Integer> counts = model.receivedMessageCounts;
        // At least the two business calls (the harness may issue its own maintenance calls,
        // e.g. memory consolidation on close); the recovery property is that the post-restart
        // call sees a strictly longer conversation than the first business call.
        assertTrue(counts.size() >= 2, "expected at least the two business model calls, got " + counts);
        int first = counts.get(0);
        int last = counts.get(counts.size() - 1);
        assertTrue(last > first,
                "the restarted agent must restore prior conversation: first call saw "
                        + first + " messages, post-restart call saw " + last);
    }

    @Test
    void userScopedMemoryPolicyIsConfigured() {
        RecordingModel model = new RecordingModel();
        TestRig rig = rig(model);
        assertEquals(IsolationScope.USER, rig.shared().memoryIsolationScope(),
                "long-term memory workspace must be USER-isolated");

        MemoryConfig defaults = SharedHarnessAgents.defaultMemoryConfig(model);
        assertEquals(MemoryConfig.FlushMode.THROTTLED, defaults.flushTrigger().mode(),
                "memory flush must be throttled to avoid per-call LLM cost");
        assertTrue(defaults.flushTrigger().minGap().toMinutes() >= 5);
        assertTrue(defaults.dailyFileRetentionDays() > 0, "daily memory retention must be bounded");
        assertTrue(defaults.sessionRetentionDays() > 0, "session log retention must be bounded");

        // USER scope maps namespaces by userId (shared across that user's sessions); falls back
        // to sessionId when userId is absent.
        var ns = IsolationScope.USER.toNamespaceFactory();
        assertEquals(List.of("user1"), ns.getNamespace(ctx("user1", "sessionA")));
        assertEquals(List.of("user1"), ns.getNamespace(ctx("user1", "sessionB")),
                "same user, different sessions must share the memory namespace");
        assertEquals(List.of("user2"), ns.getNamespace(ctx("user2", "sessionA")));
        assertEquals(List.of("sessionA"),
                ns.getNamespace(RuntimeContext.builder().sessionId("sessionA").build()));
    }

    private static void awaitActive(RecordingModel model, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000L;
        while (System.currentTimeMillis() < deadline) {
            if (model.active.get() >= expected) return;
            Thread.sleep(20);
        }
        throw new AssertionError("timed out waiting for " + expected + " active model calls; got "
                + model.active.get());
    }
}
