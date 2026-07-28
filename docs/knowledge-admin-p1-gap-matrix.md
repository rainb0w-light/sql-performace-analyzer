# 轻量知识管理 P1：现状—目标—缺口—迁移—测试矩阵

> 基线：`ea30131`。本矩阵是 `docs/knowledge-and-baseline-administration-design.md`
> 的实施审计记录，不扩展轻量后台的产品边界。

| 链路 | 现状 | P1 目标 | 实施缺口 | 迁移/兼容策略 | 自动化测试 |
|---|---|---|---|---|---|
| 原文件 | `ArtifactService` 已按租户保存不可变字节和 SHA-256 | 所有文件先落 Artifact | 管理上传未统一调用 Artifact；缺少大小与类型限制 | 复用 Artifact，不另建文件存储；文件名只展示 | 四类 Reader fixture；空文件、超限、伪扩展名负例 |
| 受控 Excel | `ExcelKnowledgeParser` 解析固定 Sheet，并生成 Sheet/行 locator | 保持现有模板和兼容 API | 新后台不能被结构化编辑器绑死 | 原 `/api/v1/knowledge-sources/imports` 继续走受控解析；新 Admin 上传默认走非结构化 Reader | 既有 `KnowledgeImportFlowTest`/`LibraryExcelKnowledgeTest` 保持通过 |
| 非结构化文件 | 无 | `.md/.txt/.pdf/.xlsx` 经 AgentScope Reader/薄 Adapter 生成 Chunk | 缺 Reader Adapter、真实 MIME/扩展名联合校验、超时、Chunk/解压上限 | TextReader/PDFReader/TikaReader；Reader 无页码或单元格信息时只保存稳定 `chunk:N` | Markdown、Text、PDF、Excel fixture；locator 稳定性 |
| 草稿状态 | `knowledge_version.status=DRAFT` | `UPLOADED/PARSING/READY/FAILED/PUBLISHING/ACTIVE` | 状态集合和处理元数据不足 | 前向迁移扩展版本元数据；兼容读取旧 `DRAFT/PUBLISHED/ROLLED_BACK` | 草稿不可检索；失败状态含稳定错误码 |
| 内容幂等 | Artifact 有 SHA-256，但知识版本无幂等查询 | 同 `client/source/contentHash` 返回已有草稿 | 缺版本 content hash 与唯一约束 | 前向迁移新增 `content_hash` 和租户安全查询；旧记录允许 null | 重复上传不新增版本 |
| 发布 | 当前先切换版本，再 `indexSafely`；索引异常被吞 | 索引成功后原子切换；失败保留旧 ACTIVE；发布幂等 | 顺序错误、吞错、检索未固定 currentVersion | 索引在切换前完成；切换仍由数据库事务完成；旧 rollback API 保持 | 索引失败旧版可用；发布重放；跨租户负例 |
| H2 检索 | JSON embedding + JVM cosine，查询可包含历史版本 | 只返回 currentVersion | 缺 ACTIVE join/filter | H2 查询 JOIN `knowledge_source.current_version_id` 和 `knowledge_version.version_no` | H2 ACTIVE 过滤与 A/B 隔离 |
| PostgreSQL 检索 | AgentScope SimpleKnowledge + PgVector，payload 含租户/版本 | 与 H2 相同的 currentVersion 语义 | 无管理层 active version 固定 | 应用层统一按当前 source/version 调同一 `KnowledgeRetriever` 端口 | PgVector Testcontainers ACTIVE 过滤 |
| 抽检 | 只有 Agent `/knowledge/search` | Admin 抽检与 Agent 使用同一端口，Top-K=5/10 | 缺统一查询门面、脱敏投影与耗时 | 新增 active-version 查询门面，两个 Controller 共用 | 同端口实例；Top-K 白名单；P95 门禁 |
| 认证/RBAC | Bearer 仅解析 clientId | ADMIN/VIEWER/AGENT_CLIENT | 缺角色身份和管理 API 授权 | 角色由已认证 client 身份派生；未知/既有 Plugin clientType 默认 AGENT_CLIENT | 三角色正负例；请求体 clientId 无效 |
| 操作日志 | 无 | 七类脱敏日志，可关联 trace/run/session | 缺表、端口、写入与筛选 | 双库共用前向 Flyway；只存 query 长度/哈希 | Token/Query/全文不入库；成功/失败完整 |
| 统计 | 无 | 今日操作、检索成功率、P50/P95、热门 Top5、最近10、按操作者 | 缺聚合查询；热门 source 需请求内去重 | 基于操作日志聚合；命中 sourceId 集合先去重后写摘要 | 固定日志数据精确断言 |
| CSV | 无 | 同租户/权限/筛选，稳定顺序，有上限 | 缺导出和公式注入防护 | 与列表共用 filter；危险首字符前置单引号 | `= + - @` 注入、上限、跨租户 |
| 错误契约 | `ApiErrorHandler` 已输出 Problem Details | Admin API 同一契约 | 缺授权/限流等稳定异常映射 | 复用统一 handler，补 403/413/422 映射 | API Contract Test |
| Web | 无前端框架 | 三个一级页面 | 缺页面和导航 | 使用 Spring Boot 静态资源，不引入重型框架 | 页面/导航契约扫描 |

## 已审计边界

- `KnowledgeImportController` / `KnowledgeImportService`：受控 Excel、DRAFT/PUBLISHED、
  preview/publish/rollback 兼容链路。
- `KnowledgeQueryController` / `KnowledgeRetriever`：结构化事实、H2 Portable Embedding、
  PostgreSQL PgVector 两条路径。
- `ArtifactService`：原始字节先落库、按 clientId 读取、SHA-256 身份。
- `BearerClients` / `TokenService`：Bearer 哈希存储和 clientId 派生；既有 Token 无角色。
- `knowledge_source` / `knowledge_version`、Spring Data JDBC Repository，以及
  PostgreSQL 历史迁移 + H2 baseline + 双库 `migration-common` 前向迁移拓扑。

## Locator 冻结

- 受控 Excel 继续使用解析器真实产生的 `Sheet!rowN`。
- 新 `.md/.txt/.pdf/.xlsx` 非结构化入口使用 AgentScope Reader 返回的 Chunk；
  2.0.0 Reader 没有返回可靠页码、Sheet/行/列，因此最低 locator 固定为 `chunk:N`，
  不推测页码或单元格坐标。
