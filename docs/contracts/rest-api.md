# REST 资源 API 契约（Phase 1/2 实施基线）

> 状态：Phase 0 冻结的契约。对应 development-guide §9.1。
> 基路径：`/api/v1`（API 版本，非产品标志，保持不变）。认证：`Authorization: Bearer <token>`；生产仅 TLS。

## 1. 领域 Controller 拆分（Phase 2 完成）

| Controller | 主要资源/路径 |
|---|---|
| `ClientTokenController` | `POST /client-tokens/apply`、`GET /client`、`DELETE /client-tokens/current`（吊销） |
| `ProjectModuleController` | `GET/POST /projects`、`GET/POST /projects/{projectId}/modules`（Phase 5 采集 projectId/moduleId） |
| `SessionController` | `GET/POST /sessions`、`GET /sessions/{sessionId}`、`GET /sessions/{sessionId}/messages`、`POST /sessions/{sessionId}/messages`（触发 Run） |
| `ArtifactDocumentController` | `POST /artifacts/files`（multipart）、`POST /artifacts/text`、`GET /artifacts/{artifactId}/content`、`POST /artifacts/mybatis/index`、`POST /artifacts/evidence/index` |
| `KnowledgeImportController`（Phase 3） | `POST /knowledge-sources/{sourceId}/imports`（multipart Excel）、`GET …/preview`、`POST …/publish`、`POST …/rollback`、`GET /knowledge-sources/{sourceId}/versions` |
| `DatasourceProfileController`（Phase 3） | `GET/POST /datasource-profiles`、凭据仅服务端保存、响应不回显敏感字段 |
| `ProfilingJobController`（Phase 3） | `POST /profiling-jobs`、`GET /profiling-jobs/{jobId}`、`GET /datasource-profiles/{id}/snapshots` |
| `MapperStatementController` | `GET /mapper-statements?artifactId=…`、`POST /mapper-statements/analyze`（唯一 statement 分析命令，202） |
| `AnalysisRunController` | `GET /sessions/{sessionId}/runs`、`GET /runs/{runId}/events`（过渡短轮询，仅兼容旧客户端）、`POST /runs/{runId}/cancel`（可取消排队中或运行中的 AG-UI Run） |
| `AguiController` | `POST /agui/runs`（启动并持续流式返回 AG-UI 事件）、`GET /agui/runs/{runId}/stream`（`Last-Event-ID` 断线续传），见 ag-ui-mapping.md |
| `ReportController`（Phase 5） | `GET /runs/{runId}/report`、`GET /reports/{reportId}`（结构化 JSON + `Accept: text/markdown` 投影） |
| `RecommendationController` | `GET /sessions/{sessionId}/recommendations`、`POST /recommendations/{recommendationId}/decision` |

## 2. DTO 约定

- 稳定业务命名，**禁止产品版本前缀**（最终扫描由 ProductMarkerScanTest 强制）；Java record + Jackson；`@Valid` + jakarta validation。
- 时间戳：ISO-8601 UTC 字符串（`2026-07-25T10:00:00Z`）。
- ID 字符串前缀保持现风格：`client_…`、`session_…`、`run_…`、`artifact_…`、`report_…`。
- 列表响应统一分页：`GET …?page=0&size=50` → `{ "items": […], "page": 0, "size": 50, "total": N }`（Phase 2 起新端点必须分页；存量端点在 Phase 2 同批切换）。
- 大内容（Artifact 字节、报告全文、事件 payload）走独立内容接口，不塞进列表。

### 2.1 冻结的现有请求字段（契约测试钉住，改名不得变更）

- `POST /client-tokens/apply`：`clientName`、`clientType`、`deviceId` → `{ client: {…}, accessToken }`
- `POST /sessions`：`title`
- `POST /sessions/{sessionId}/messages`：`content`、`messageType`、`modelName`、`artifactIds`、`datasourceProfile` → `{ runId, sessionId, status }`
- `POST /recommendations/{recommendationId}/decision`：`decision ∈ {ACCEPTED, REJECTED}`、`category`、`reason`（REJECTED 时 reason 必填，DB CHECK 保证）→ 204
- `POST /artifacts/mybatis/index`：`xmlContent`、`sessionId`、`namespace` → `{ artifactId, documentId, chunkCount }`
- `POST /mapper-statements/analyze`：`artifactId`、`statementId`、`datasourceProfileId` 必填；`schemaName`、`projectId`、`moduleId`、`sessionId`、`maxScenarios` 可选 → 202 `{ sessionId, runId, status, streamUrl }`。服务端在一个事务内验证归属并创建 Session/Run/Job，报告由 Worker 异步生成。

## 3. 错误：Problem Details（RFC 9457）

`Content-Type: application/problem+json`：

```json
{
  "type": "https://sql-analyzer.example/problems/validation-failed",
  "title": "请求参数无效",
  "status": 400,
  "detail": "decision 必须是 ACCEPTED 或 REJECTED",
  "code": "VALIDATION_FAILED",
  "requestId": "req_01J…",
  "retryable": false,
  "errors": [ { "field": "decision", "message": "不在允许集合内" } ]
}
```

- 所有端点统一该格式；`requestId` 来自 `X-Request-Id`（缺省服务端生成），并回写响应头。
- 状态码约定：400 校验失败、401 无/坏 Token、404 不存在或资源不属于当前 client、409 幂等键冲突/版本冲突、422 业务不可执行（如取消已终态 Run）、429 超限、500 未分类。
- 现有 `{timestamp,error,message}` 在 Phase 1 随 `ApiErrorHandler` 一并切换到 Problem Details（契约测试同步更新断言）。

## 4. 幂等与去重

- 所有创建类接口接受 `Idempotency-Key` 请求头；`/mapper-statements/analyze` 当前强制该头并已实现真实 HTTP 重放/冲突/并发门禁。服务端保存 `(clientId, idempotencyKey) → response` 24h，重放返回原响应；键相同但请求体不同 → 409 `IDEMPOTENCY_CONFLICT`。
- Artifact 上传以 `sha256` 去重：同 client 同 hash 命中既有 Artifact 时返回既有记录（`sourceType` 不同则新建，hash 仅对内容去重）。
- Mapper 上传携带 `projectId/moduleId/mapperPath/contentHash/mybatisVersion`（Phase 5 起）；同 contentHash 不重复建 Artifact。

## 5. 健康与探针

- `GET /healthz`（无鉴权）→ `{ "status": "UP", "persistenceEnabled": bool, "workerEnabled": bool, "timestamp": ISO8601 }`。字段名冻结。

## 6. 只读与安全边界（全阶段不可妥协）

- 目标数据库仅只读凭据；EXPLAIN/统计查询受超时、最大行数、白名单与方言 adapter 限制。
- 默认禁止对目标库执行 Mapper 的 INSERT/UPDATE/DELETE；这些 statement 仅静态分析。
- 响应与事件中不回显数据源密码、Token 明文、个人敏感业务值；Top-K/样例参数按字段敏感级别：明文/SHA-256 哈希/不保存（Phase 3 画像策略）。
- 产品保持只读建议边界：不执行 DDL、不修改用户 Mapper、不自动提交代码。
