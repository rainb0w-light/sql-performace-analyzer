# SQL Performance Analyzer — 架构

> 正式架构文档。接口契约见 [contracts/](contracts/)，部署与运维见 [operations.md](operations.md)。

## 1. 定位

私有部署的慢 SQL 治理 Agent：用户在 IntelliJ IDEA 中选择一个 MyBatis Mapper statement，系统使用 MyBatis 官方运行时生成一组有业务含义的静态 SQL 场景，结合业务语义、数据画像、索引/分片元数据和执行计划，产出可追溯的性能分析报告与优化建议。

产品保持**只读建议边界**：不执行 DDL、不修改用户 Mapper、不自动提交代码、不写目标数据库。

## 2. 协议

| 场景 | 协议 |
|---|---|
| 启动分析并流式消费推理事件 | AG-UI over HTTPS + SSE（`POST /api/v1/agui/runs`、`GET /api/v1/agui/runs/{runId}/stream`） |
| Session / Mapper / 知识 / 数据源 / 报告等资源 | 版本化 HTTPS JSON REST（`/api/v1`，见 contracts/rest-api.md） |
| Excel、Mapper 内容上传 | REST（JSON/multipart），SHA-256 去重 |
| 认证 | Bearer Token；生产仅 TLS |

AG-UI 事件先持久化到 `run_event` 再发送；客户端以事件 ID 作为游标（`Last-Event-ID`）断线续传，指数退避 + 随机抖动重连，终态以标准 `RUN_FINISHED`/`RUN_ERROR` 事件类型判定。详见 contracts/ag-ui-mapping.md。

## 3. AgentScope 运行模型

- 每个稳定的模型配置一个**共享** `HarnessAgent` 实例；每次调用传 `RuntimeContext(userId=clientId, sessionId=analysis_session.id)`。框架按 `(userId, sessionId)` 串行同 Session、并行不同 Session，并在调用前后经 `PostgresDistributedStore` 恢复/持久化 `AgentState`。
- AG-UI 适配器不携带 userId，服务端通过委托 Agent 从受信的 `forwardedProps` 注入 clientId，保证状态槽与记忆隔离按稳定账户标识而非设备标识。
- 三类信息严格分离：
  | 信息 | 存储 | 隔离 |
  |---|---|---|
  | 当前会话上下文/压缩摘要/工具状态 | `AgentStateStore`（PostgresDistributedStore） | 单 Session |
  | 用户偏好、已确认约束、反馈决策 | Harness 长期记忆（远端 workspace，`IsolationScope.USER`） | 用户级，跨 Session 共享 |
  | 表/字段业务定义、规则、枚举、索引、分片、画像 | 结构化 PostgreSQL 事实层 + RAG 检索层 | 项目/数据源级，版本化 |
- 长期记忆 flush 节流（默认 15 分钟一次）、合并间隔 30 分钟、保留期：每日记忆 90 天、会话原始日志 180 天。只记忆偏好/已确认约束/反馈，禁止记忆原始样本、凭据、个人敏感信息或未经确认的推断。
- 产品投影表 `analysis_session` / `conversation_message` / `agent_run` / `run_event` 保持可查询、可审计，不被 AgentState 替代。

## 4. MyBatis 原生运行时与场景规划

- 不可妥协：最终 SQL 100% 来自 `XMLMapperBuilder -> MappedStatement.getBoundSql(parameterObject)`；不实现自定义动态 SQL 解释器，不手工拼接 `<if>/<choose>/<where>/<foreach>`，不用正则求值条件。无法解析（自定义 LanguageDriver、缺失项目类型、坏 XML）一律标记 `UNSUPPORTED`，不静默降级。
- `DynamicNodeCatalog` 只做结构化扫描（标签位置、test 原文、父子关系），用于覆盖目标与 UI 定位；求值交给 MyBatis OGNL/SqlNode。
- `ScenarioPlanner` 以业务语义生成参数场景：主路径、`<if>` true/false、`<choose>` 各分支与 otherwise、`<foreach>` 空/单/多/受控大列表、范围 min/分位/max/越界、枚举高频/低频/未知、分片键命中/缺失。组合用**覆盖目标贪心压缩**（covering-array 策略）替代无上限笛卡尔积，默认每 statement ≤ 20 个场景；主路径、用户样例与 `${}` 白名单路径为保留优先级。
- `ScenarioEngine`：逐场景调用 getBoundSql → 空白归一化 + SQL 指纹去重（不改变语义）→ `${}` 强制注入风险标记（仅显式白名单取值可豁免）。
- SQL 文本与参数列表始终分离：`#{}` 保持占位符，报告展示脱敏参数表。

## 5. 业务知识与画像

- Excel 导入：原始文件先存 Artifact；Apache POI 确定性解析受控模板（tables/columns/rules/enums/sharding/aliases）；行级错误带 Sheet/行/列/原因，不静默丢弃；预览（DRAFT）→ 发布 → 版本化 → 回滚。发布后的结构化事实进入 PostgreSQL，描述文本同步到 SimpleKnowledge + PgVector（需启用向量配置），检索结果带 sourceId/版本/定位/采集时间/置信度。
- 冲突优先级：人工已发布规则 > Excel 已发布版本 > 系统目录采集 > Agent 推断；自动采集与人工记录冲突不覆盖，进入待确认差异。
- 数据库画像：确定性方言模板（LLM 不生成采样 SQL），有界采样、语句超时、租约/重试/幂等/取消/审计；快照不可变；敏感字段 Top-K 按策略 PLAINTEXT/HASHED/OMITTED。
- 元数据：索引（类型/列顺序/基数/使用统计）、一级/二级分片键、路由与拓扑，带来源、版本、校验状态与确认人。

## 6. 报告

结构化 JSON Schema（contracts/report-schema.json）：基本信息、结论摘要（严重度/瓶颈/置信度）、业务语义证据、场景矩阵与 BoundSql、索引/分片、数据分布、执行计划、风险（全表扫描/回表/排序/临时表/跨分片/热点/`${}`）、优化建议（问题/证据/影响/建议 SQL-DDL/优先级/置信度）、限制与缺失证据、审计（runId/sessionId/知识版本/画像快照/模型/时间）。同时提供 Markdown 展示投影。

- 执行计划只对已启用、已绑定只读目标数据源的 `SELECT/WITH` BoundSql 场景执行普通 `EXPLAIN`。MyBatis 参数按 mapping 顺序使用 PreparedStatement 绑定，绝不插值进 SQL；`${}` 风险场景不发送到目标库。
- `INSERT/UPDATE/DELETE` 只进行静态分析，禁止发送 DML，禁止 `EXPLAIN ANALYZE`。目标库、凭据、权限或参数不可用时，报告仍生成，并在 `limits` 中记录降级原因。
- AgentScope 增强是可选的报告后置审阅：输入为已通过 Schema 校验、含 EXPLAIN evidence 的确定性报告，不接收目标库密码，不覆盖确定性风险/建议。模型失败只写入 `agentEnhancement.status=FAILED`，不阻断报告持久化。

## 7. 模块

```text
IDEA Plugin (PSI 识别 statement → REST + AG-UI SSE)
 ├─ controller/        领域 REST Controller + ApiErrorHandler(Problem Details) + AguiController(SSE)
 ├─ agui/              AguiRunService / AguiExecutor / AguiEventStreamer（持久化先行）
 ├─ service/           Session / Token / Artifact(Pipeline) / AgentWorker
 ├─ adapter/agentscope 共享 HarnessAgent + RuntimeContext 路由 + USER 记忆
 ├─ scenario/          ScenarioPlanner / ScenarioEngine（官方 BoundSql）
 ├─ mybatis/           MyBatisStatementRuntime / DynamicNodeCatalog
 ├─ knowledge/         Excel 解析 / 导入 / 查询 / 向量索引
 ├─ metadata/          索引/分片元数据与冲突
 ├─ profiling/         方言适配器 / ProfilingService / ProfilingWorker
 └─ adapter/postgresql 全部 DAO（sql_analyzer schema）
```

## 8. 安全与只读边界

- 目标数据库仅只读凭据；EXPLAIN/统计受超时、最大行数、白名单与方言适配限制。
- EXPLAIN 与 Agent 增强默认关闭，分别由 `SQL_ANALYZER_EXPLAIN_ENABLED` 和
  `SQL_ANALYZER_AGENT_ENHANCEMENT_ENABLED` 显式开启。
- Token 在 IDEA 端存入 PasswordSafe；服务端只存 SHA-256 哈希。数据源密码不落库，经环境变量引用解析。
- 默认禁止对目标库执行 Mapper 的 INSERT/UPDATE/DELETE（仅静态分析）；产品不执行 DDL、不修改代码。
- 错误统一 RFC 9457 Problem Details（code/message/requestId/field errors/retryable）。

## 9. 测试门禁

- 后端：契约/单元/Flyway 迁移/并发与恢复（共享 Agent 串行/并行/重启恢复）/记忆隔离；Docker 门禁（PostgreSQL + MySQL 目标）验证知识发布/回滚、画像与敏感策略、AG-UI 全流程。
- MyBatis fixture 矩阵覆盖 static/多 if/嵌套 OGNL/choose/where-trim-set/foreach 空单多/bind/sql-include/databaseId/#{}-${}/多 statement/自定义 driver 拒绝/坏 XML/缺失 include/未知类型。
- 插件：HTTP/SSE 契约（含断线续传、去重、退避）、渲染与场景矩阵映射、描述符结构；`runIde` UI 冒烟（macOS GUI 门禁）。
- 交付门禁：`scripts/acceptance.sh --local`（+ `--external` 全量）；产品标志扫描 allowlist 仅含历史 Flyway 版本与官方文档 URL。
