# AG-UI over HTTPS SSE 契约（Phase 2 实施基线）

> 状态：Phase 0 冻结的契约，Phase 2 实施。对应 development-guide §2.1、§9.2。
> 依据：AgentScope Java 2.x AG-UI adapter（`RunAgentInput → Flux<AguiEvent>`，Spring Boot 以 SSE 返回）。

## 1. 端点

| 用途 | 方法/路径 | 说明 |
|---|---|---|
| 启动 MyBatis statement 分析 | `POST /api/v1/mapper-statements/analyze` | 返回 202 与 `streamUrl`；Session/Run/Job 由服务端创建 |
| 启动一次分析 Run 并流式返回 AG-UI 事件 | `POST /api/v1/agui/runs`（`Accept: text/event-stream`） | 请求体为 AG-UI `RunAgentInput`；响应为持续 SSE 流，直到 Run 终态 |
| 断线续传/补发已持久化事件 | `GET /api/v1/agui/runs/{runId}/stream`，携带 `Last-Event-ID: <eventId>` | 服务端从持久化游标补发，再续接实时事件 |
| 取消 Run | `POST /api/v1/runs/{runId}/cancel` | REST，幂等 |
| 查询 Run/事件历史（审计、UI 恢复） | `GET /api/v1/sessions/{sessionId}/runs`、`GET /api/v1/runs/{runId}/events?after=<eventId>` | REST JSON 投影，非实时通道 |

约束：

- 事件**先持久化到 `run_event`，再发送给客户端**。重连从持久化游标补发；同一事件 ID 幂等。
- 每个 SSE `id:` 即 `run_event.id`（BIGSERIAL，单调递增），客户端记录最后 ID 用于 `Last-Event-ID`。
- 流必须保持打开直到 `RUN_FINISHED`；失败/取消先发 `RUN_ERROR`，随后发唯一 `RUN_FINISHED`。服务端以心跳注释行（`: ping`，间隔 ≤15s）保活，穿越企业代理。
- 不再提供 30 秒到期的轮询式 SSE。IDEA 客户端对同一 Run 只维护一个 SSE 连接；断线后指数退避 + 抖动重连，带 `Last-Event-ID`。

## 2. 标识映射

| AG-UI 概念 | 本项目标识 |
|---|---|
| `threadId` | `analysis_session.id` |
| `runId` | `agent_run.id` |
| event `id` | `run_event.id` |
| Msg（完整会话消息） | `conversation_message`（产品审计投影，独立于事件流） |

## 3. 事件映射表

| 产品内部状态 | AG-UI 标准事件 | metadata（custom，键前缀 `spa.`） |
|---|---|---|
| Run 入队/开始 | `RUN_STARTED` | `spa.runId`、`spa.modelName` |
| Agent 文本增量 | `TEXT_MESSAGE_START` / `TEXT_MESSAGE_CONTENT` / `TEXT_MESSAGE_END` | `spa.role=assistant` |
| 工具调用开始/参数 | `TOOL_CALL_START` / `TOOL_CALL_ARGS` | `spa.tool`（analyze_sql_shape、explain、table_structure、slow_log 等） |
| 工具结果 | `TOOL_CALL_END`（result 入标准字段） | `spa.evidenceType`、`spa.evidenceArtifactId` |
| 状态快照（阶段进度：知识检索/画像/EXPLAIN/分析/报告投影） | `STATE_DELTA` / `STATE_SNAPSHOT` | `spa.stage`、`spa.stageStatus` |
| 自定义产品事件（场景矩阵更新、报告就绪、建议投影完成） | `CUSTOM`（name 形如 `spa.scenario_matrix`、`spa.report_ready`、`spa.recommendations_ready`） | 完整结构化 payload |
| Run 成功完成 | `RUN_FINISHED` | `spa.reportId` |
| Run 失败 | `RUN_ERROR`（message/code） | `spa.retryable`、`spa.retryCount` |
| Run 取消 | `RUN_ERROR`（code=`CANCELLED`）→ `RUN_FINISHED`（status=`CANCELLED`） | — |

规则：

- 产品信息只进 custom event / metadata，**不改变标准事件语义**。
- 最终 Report 与 Recommendation **单独持久化**（`analysis_report`、`recommendation` 表），通过 `spa.report_ready`/`spa.recommendations_ready` 携带 ID；客户端不从 token 流重组业务对象。
- 事件 payload 中的业务值按敏感级别脱敏（Top-K 值、样例参数），脱敏策略见 rest-api.md §6。

## 4. 与 AgentScope 的接线

- `AgentController`（AG-UI adapter）接收 `RunAgentInput`，经应用服务创建 `analysis_session`/`agent_run`/`agent_job`，由 Worker 调用共享 `HarnessAgent`（`RuntimeContext(userId, sessionId)`）。
- AgentScope `Flux<AguiEvent>` 的每个事件 → 持久化 `run_event`（type=AG-UI 事件类型，payload=标准 JSON + `spa.*` metadata）→ 写入 SSE。
- 重连补发路径读 `run_event`（同一映射的反序列化），保证离线期间事件不丢。

## 5. 客户端可靠性要求（IDEA，Phase 2/5）

- 单一持续 SSE 连接，逐事件增量解析（`id:`/`event:`/`data:` 三段式），禁止全量轮询。
- 记录 lastEventId；重连携带 `Last-Event-ID`；指数退避（初始 500ms，上限 30s，full jitter）。
- 所有上行请求带 `X-Request-Id` 与 `Idempotency-Key`（创建 Run 用 `clientKey+threadId+nonce`）。
- 终态以标准 `RUN_FINISHED`/`RUN_ERROR` 为准，禁止对原始文本做子串匹配。
