# SQL Performance Analyzer — 部署与运维

> 架构见 [architecture.md](architecture.md)，接口契约见 [contracts/](contracts/)。

## 1. 运行方式

### Docker Compose（PostgreSQL + Agent）

```bash
export DEEPSEEK_API_KEY='your-key'
docker compose up -d postgres agent
curl http://localhost:18881/healthz
```

### 本地开发（默认文件型 H2）

```bash
./gradlew bootRun
curl http://localhost:18881/healthz
```

无需 PostgreSQL 或 Docker。首次启动时，Flyway 从 `db/migration-h2` 和
`db/migration-common` 初始化数据库；数据默认持久化到
`~/.sql-performance-analyzer/data/management.mv.db`，服务重启后保留。

当管理库为 H2 时，完全没有 datasource profile 的客户端首次读取数据源列表会得到
唯一的 `Local H2 Static Analysis` 只读 profile。它不会覆盖已有真实数据源，也不会在
PostgreSQL 模式启用。其目标路径默认为
`~/.sql-performance-analyzer/data/local-target.mv.db`，仅在实际连接时创建。

`/healthz` 无需认证，默认返回 `persistenceEnabled=true`、
`workerEnabled=true`，不含密钥或凭据。若只需要无持久化健康检查，可显式设置
`SQL_ANALYZER_PERSISTENCE_ENABLED=false SQL_ANALYZER_WORKER_ENABLED=false`。

## 2. 配置（环境变量）

| 变量 | 默认 | 说明 |
|---|---|---|
| `SERVER_PORT` | 18881 | HTTP 端口 |
| `SQL_ANALYZER_PERSISTENCE_ENABLED` | true | 启用管理库持久化（REST/AG-UI 资源 API 随之启用） |
| `SQL_ANALYZER_JDBC_URL` | `jdbc:h2:file:${SQL_ANALYZER_H2_DATA_PATH};AUTO_SERVER=TRUE` | 管理库连接；部署时可覆盖为 PostgreSQL |
| `SQL_ANALYZER_H2_DATA_PATH` | `~/.sql-performance-analyzer/data/management` | H2 数据文件基路径，实际文件后缀为 `.mv.db` |
| `SQL_ANALYZER_JDBC_USERNAME` / `SQL_ANALYZER_JDBC_PASSWORD` | sa/空 | 管理库凭据；PostgreSQL 部署必须覆盖 |
| `SQL_ANALYZER_POSTGRES_JDBC_URL/USERNAME/PASSWORD` | — | 兼容旧部署变量；优先级低于通用 `SQL_ANALYZER_JDBC_*` |
| `SQL_ANALYZER_POSTGRES_POOL_SIZE` | 10 | HikariCP 连接池 |
| `SQL_ANALYZER_WORKER_ENABLED` | true | 启用 Agent Job 与画像 Worker |
| `SQL_ANALYZER_WORKER_POLL_DELAY_MS` | 500 | Worker 轮询间隔 |
| `SQL_ANALYZER_H2_DATASOURCE_BOOTSTRAP_ENABLED` | true | H2 本地模式为无数据源客户端创建唯一静态分析 profile |
| `SQL_ANALYZER_H2_TARGET_DATA_PATH` | `~/.sql-performance-analyzer/data/local-target` | 本地目标 H2 文件基路径 |
| `SQL_ANALYZER_H2_TARGET_JDBC_URL` / `_USERNAME` | 文件型 H2 / sa | 本地目标 profile 连接覆盖 |
| `SQL_ANALYZER_MAX_CONCURRENT_RUNS` | 10 | 单客户端并发 Run 上限 |
| `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` / `DEEPSEEK_TEMPERATURE` | — | LLM 模型配置（OpenAI 兼容协议） |
| `SQL_ANALYZER_KNOWLEDGE_VECTOR_ENABLED` | false | 启用 SimpleKnowledge + PgVector 检索层（需要 pgvector 扩展） |
| `SQL_ANALYZER_KNOWLEDGE_PGVECTOR_JDBC_URL/USERNAME/PASSWORD` | 同管理库 | 向量库连接 |
| `SQL_ANALYZER_KNOWLEDGE_VECTOR_DIMENSIONS` | 1024 | 向量维度（与 embedding 模型一致） |
| `SQL_ANALYZER_EMBEDDING_API_KEY` / `_BASE_URL` / `_MODEL` | — | OpenAI 兼容 embedding 端点 |
| `SQL_ANALYZER_KNOWLEDGE_MAX_FILE_BYTES` | 10485760 | 单个知识文件字节上限 |
| `SQL_ANALYZER_KNOWLEDGE_MAX_CHUNKS` | 1000 | 单版本解析 Chunk 上限 |
| `SQL_ANALYZER_KNOWLEDGE_PARSE_TIMEOUT_MS` | 30000 | Reader 解析超时 |
| `SQL_ANALYZER_KNOWLEDGE_MAX_EXPANDED_BYTES` | 52428800 | `.xlsx` 压缩展开上限 |
| `SQL_ANALYZER_KNOWLEDGE_LOG_RETENTION_DAYS` | 90 | 知识操作日志默认保留天数 |
| `SQL_ANALYZER_KNOWLEDGE_EXPORT_LIMIT` | 5000 | 单次 CSV 导出行数上限 |
| `SQL_ANALYZER_PROFILING_SAMPLE_ROWS` | 50000 | 画像有界采样行数 |
| `SQL_ANALYZER_PROFILING_TOP_K` | 10 | Top-K 频率值数量 |
| `SQL_ANALYZER_PROFILING_STATEMENT_TIMEOUT_MS` | 10000 | 画像语句超时 |

目标数据库密码不落库：`datasource_profile.credential_env` 记录凭据所在的**环境/属性名**，运行时解析。

## 3. 数据库与迁移策略

- 业务对象位于 `sql_analyzer` schema（无版本表名）。AgentScope 自有状态表位于 `agentscope` schema（框架自建）。
- Flyway 历史文件（`src/main/resources/db/migration/`）**不可修改**：默认所有环境已执行过历史迁移；任何对象变更必须新增 forward migration，并附回滚说明（见 `docs/migrations/`）。
- 管理库只运行 Flyway 迁移与 DAO 写入；对目标数据库只读（EXPLAIN/统计模板固定、有界采样、语句超时）。

## 4. IDEA 插件

```bash
cd idea-plugin
../gradlew buildPlugin        # 生成 build/distributions/*.zip
```

- 安装 zip 后，在 Mapper XML 的 `<select>/<insert>/<update>/<delete>` 标签上右键 **Analyze SQL Performance** 发起标准分析；Tool Window 提供流式事件、场景矩阵和合并的“报告”页签，建议可一键接受/拒绝。
- 后端地址、默认数据源与 Session 为项目级设置；数据源绑定按 module 记忆；Token 存入 IDE PasswordSafe（不写明文 XML）。
- 相同内容的 Mapper 以 SHA-256 去重，不重复上传。

## 5. 验收门禁

```bash
scripts/acceptance.sh --local      # 后端测试+bootJar、插件契约+打包+结构校验、遗留架构扫描、git diff --check
scripts/acceptance.sh --external   # 预检 + PostgreSQL 集成门禁 + runIde UI 冒烟（需 Docker 与 macOS GUI）
bash scripts/preflight.sh --all    # 环境预检（Docker / PostgreSQL / GUI）
```

CI（`.github/workflows/verify.yml`）自动执行后端 PostgreSQL 集成门禁与插件契约打包，并上传 JAR/ZIP 产物。

启用私有 PostgreSQL 门禁（无 Docker 环境）：

```bash
RUN_POSTGRES_INTEGRATION_TESTS=true \
SQL_ANALYZER_TEST_POSTGRES_JDBC_URL='jdbc:postgresql://localhost:5432/sql_analyzer_test' \
SQL_ANALYZER_TEST_POSTGRES_USERNAME='postgres' \
SQL_ANALYZER_TEST_POSTGRES_PASSWORD='postgres' \
./gradlew clean test
```
