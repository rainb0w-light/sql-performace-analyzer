package com.biz.sccba.sqlanalyzer.adapter.agentscope;

import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.MemoryConfig.FlushTrigger;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * One shared, stably-configured {@link HarnessAgent} per model configuration (development-guide §2.2).
 *
 * <p>AgentScope Java 2.x serializes calls on the {@code (userId, sessionId)} slot key inside the
 * shared instance: same-session calls run FIFO one-at-a-time, different sessions run in parallel.
 * Session state is restored from the {@link DistributedStore} at call start and persisted at call
 * end, so a single instance serves all clients and sessions; no per-session agent cache is needed
 * (the framework's former per-instance running gate is deprecated in 2.0.0).
 *
 * <p>Long-term memory: the workspace filesystem is a remote store namespaced with
 * {@link IsolationScope#USER}, so daily memory and MEMORY.md are shared across one user's
 * sessions and isolated between users. Flush is throttled and retention is bounded.
 */
public final class SharedHarnessAgents implements AutoCloseable {

    public static final String AGENT_ID = "slow-sql-governance";

    /** Throttle long-term memory flush to at most once per this gap (avoids per-call LLM cost). */
    public static final Duration DEFAULT_FLUSH_MIN_GAP = Duration.ofMinutes(15);
    /** Daily memory ledger retention before archival. */
    public static final int DEFAULT_DAILY_RETENTION_DAYS = 90;
    /** Raw per-session JSONL log retention (audit trail) before pruning. */
    public static final int DEFAULT_SESSION_RETENTION_DAYS = 180;

    static final String SYSTEM_PROMPT = """
            你是慢 SQL 治理 Agent。只做分析和建议，不执行 DDL，不修改用户代码。
            你必须基于用户提供的 SQL、MyBatis 解析结果、执行计划、表结构和日志证据回答。
            输出中文 Markdown，明确问题、证据、影响、优先级和可执行的优化建议。
            记忆纪律：只允许记住用户偏好、已确认的业务约束和反馈决策；
            绝不记录原始数据样本、凭据、个人敏感信息或未经用户确认的模型推断。
            """;

    static final String MEMORY_FLUSH_PROMPT = """
            从本轮对话中提取值得长期保留的事实，仅限：用户偏好、用户明确确认的业务约束、建议反馈决策。
            禁止写入：原始数据样本、Top-K 业务值、凭据或密钥、个人敏感信息、未经确认的推断。
            如无符合范围的新事实，则不写入任何内容。
            """;

    private final Function<String, Model> modelResolver;
    private final Toolkit toolkit;
    private final DistributedStore distributedStore;
    private final IsolationScope memoryIsolationScope;
    private final Function<Model, MemoryConfig> memoryConfigFactory;
    private final ConcurrentHashMap<String, HarnessAgent> agentsByModel = new ConcurrentHashMap<>();

    /** Production wiring: USER-scoped memory with throttled flush and bounded retention. */
    public SharedHarnessAgents(Function<String, Model> modelResolver, Toolkit toolkit,
                               DistributedStore distributedStore) {
        this(modelResolver, toolkit, distributedStore, IsolationScope.USER,
                SharedHarnessAgents::defaultMemoryConfig);
    }

    public SharedHarnessAgents(Function<String, Model> modelResolver, Toolkit toolkit,
                               DistributedStore distributedStore, IsolationScope memoryIsolationScope,
                               Function<Model, MemoryConfig> memoryConfigFactory) {
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver");
        this.toolkit = Objects.requireNonNull(toolkit, "toolkit");
        this.distributedStore = Objects.requireNonNull(distributedStore, "distributedStore");
        this.memoryIsolationScope = Objects.requireNonNull(memoryIsolationScope, "memoryIsolationScope");
        this.memoryConfigFactory = Objects.requireNonNull(memoryConfigFactory, "memoryConfigFactory");
    }

    /** Production memory policy: throttled flush, 30-min consolidation gap, bounded retention. */
    public static MemoryConfig defaultMemoryConfig(Model model) {
        return MemoryConfig.builder()
                .model(model)
                .flushPrompt(MEMORY_FLUSH_PROMPT)
                .flushTrigger(FlushTrigger.throttled(DEFAULT_FLUSH_MIN_GAP))
                .consolidationMinGap(Duration.ofMinutes(30))
                .dailyFileRetentionDays(DEFAULT_DAILY_RETENTION_DAYS)
                .sessionRetentionDays(DEFAULT_SESSION_RETENTION_DAYS)
                .build();
    }

    /** Returns the shared agent for a model key; builds it lazily on first use. */
    public HarnessAgent agentFor(String modelKey) {
        String key = modelKey == null || modelKey.isBlank() ? "default" : modelKey;
        return agentsByModel.computeIfAbsent(key, this::build);
    }

    private HarnessAgent build(String modelKey) {
        Model model = modelResolver.apply(modelKey);
        return HarnessAgent.builder()
                .agentId(AGENT_ID)
                .name("Slow SQL Governance Agent")
                .description("Read-only SQL performance analysis agent")
                .sysPrompt(SYSTEM_PROMPT)
                .model(model)
                .toolkit(toolkit)
                .distributedStore(distributedStore)
                .filesystem(new RemoteFilesystemSpec().isolationScope(memoryIsolationScope))
                .memory(memoryConfigFactory.apply(model))
                .compaction(CompactionConfig.builder().build())
                .maxIters(18)
                .disableFilesystemTools()
                .disableShellTool()
                .disableDynamicSkills()
                .disableSubagents()
                .disableToolsConfig()
                .build();
    }

    public IsolationScope memoryIsolationScope() {
        return memoryIsolationScope;
    }

    @Override
    public void close() {
        agentsByModel.values().forEach(HarnessAgent::close);
        agentsByModel.clear();
    }
}
