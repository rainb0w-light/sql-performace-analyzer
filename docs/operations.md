# SQL Performance Analyzer — 部署与运维

> 架构见 [architecture.md](architecture.md)，接口契约见 [contracts/](contracts/)。

## 1. 运行方式

### Docker Compose（PostgreSQL + Agent）

```bash
export DEEPSEEK_API_KEY='your-key'
docker compose up -d postgres agent
curl http://localhost:18881/healthz
```

### 无持久化开发

```bash
./gradlew bootRun
curl http://localhost:18881/healthz
```

`/healthz` 无需认证，返回进程状态与 persistence/worker 开关，不含密钥或凭据。

## 2. 配置（环境变量）

| 变量 | 默认 | 说明 |
|---|---|---|
| `SERVER_PORT` | 18881 | HTTP 端口 |
| `SQL_ANALYZER_PERSISTENCE_ENABLED` | false | 启用管理库持久化（REST/AG-UI 资源 API 随之启用） |
| `SQL_ANALYZER_POSTGRES_JDBC_URL` | jdbc:postgresql://localhost:5432/sql_analyzer | 管理库连接 |
| `SQL_ANALYZER_POSTGRES_USERNAME` / `SQL_ANALYZER_POSTGRES_PASSWORD` | postgres/postgres | 管理库凭据 |
| `SQL_ANALYZER_POSTGRES_POOL_SIZE` | 10 | HikariCP 连接池 |
| `SQL_ANALYZER_WORKER_ENABLED` | false | 启用 Agent Job 与画像 Worker |
| `SQL_ANALYZER_WORKER_POLL_DELAY_MS` | 500 | Worker 轮询间隔 |
| `SQL_ANALYZER_MAX_CONCURRENT_RUNS` | 10 | 单客户端并发 Run 上限 |
| `SQL_ANALYZER_EXPLAIN_ENABLED` | false | 对安全 SELECT/WITH 场景执行普通只读 EXPLAIN；禁止 EXPLAIN ANALYZE |
| `SQL_ANALYZER_AGENT_ENHANCEMENT_ENABLED` | false | 使用 AgentScope 对确定性报告做可选后置审阅 |
| `SQL_ANALYZER_AGENT_ENHANCEMENT_MODEL` | 默认模型 | AgentScope 报告审阅使用的模型配置名 |
| `SQL_ANALYZER_AGENT_ENHANCEMENT_MAX_OUTPUT_CHARS` | 20000 | Agent 补充内容最大持久化字符数 |
| `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` / `DEEPSEEK_TEMPERATURE` | — | LLM 模型配置（OpenAI 兼容协议） |
| `SQL_ANALYZER_KNOWLEDGE_VECTOR_ENABLED` | false | 启用 SimpleKnowledge + PgVector 检索层（需要 pgvector 扩展） |
| `SQL_ANALYZER_KNOWLEDGE_PGVECTOR_JDBC_URL/USERNAME/PASSWORD` | 同管理库 | 向量库连接 |
| `SQL_ANALYZER_KNOWLEDGE_VECTOR_DIMENSIONS` | 1024 | 向量维度（与 embedding 模型一致） |
| `SQL_ANALYZER_EMBEDDING_API_KEY` / `_BASE_URL` / `_MODEL` | — | OpenAI 兼容 embedding 端点 |
| `SQL_ANALYZER_PROFILING_SAMPLE_ROWS` | 50000 | 画像有界采样行数 |
| `SQL_ANALYZER_PROFILING_TOP_K` | 10 | Top-K 频率值数量 |
| `SQL_ANALYZER_PROFILING_STATEMENT_TIMEOUT_MS` | 10000 | 画像语句超时 |

目标数据库密码不落库：`datasource_profile.credential_env` 记录凭据所在的**环境/属性名**，运行时解析。

### 普通 EXPLAIN 与 Agent 增强

生产环境建议先只开启 EXPLAIN：

```bash
export SQL_ANALYZER_EXPLAIN_ENABLED=true
export LIBRARY_DB_PASSWORD='read-only-password'
```

要求 `datasource_profile` 使用只读账户，且 `credential_env=LIBRARY_DB_PASSWORD`。服务端只会对
MyBatis 官方 BoundSql 生成的安全 `SELECT/WITH` 场景执行普通 `EXPLAIN`，参数通过
PreparedStatement 绑定。以下情况只记录降级原因，不会使报告失败：

- 数据源或凭据不可用。
- 账户缺少 EXPLAIN 权限。
- MyBatis 参数无法完整绑定。
- 场景包含未受信任的 `${}`。
- statement 为 INSERT/UPDATE/DELETE。

确认确定性报告稳定后，可选择开启 AgentScope 后置审阅：

```bash
export SQL_ANALYZER_AGENT_ENHANCEMENT_ENABLED=true
export SQL_ANALYZER_AGENT_ENHANCEMENT_MODEL=deepseek
```

Agent 只接收已校验的标准报告和 evidence，不接收目标库密码，也不覆盖确定性结论。模型超时或失败时
`agentEnhancement.status` 为 `FAILED`，报告、Recommendation 和 AG-UI 终态仍按确定性结果完成。

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
