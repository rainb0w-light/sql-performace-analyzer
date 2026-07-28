# IDEA Plugin UI/UX P1 实现矩阵

基线：`origin/master@ea30131`
权威设计：`docs/idea-plugin-ui-ux-design.md`
范围：仅 `idea-plugin/**`；Knowledge worktree 保持只读、无修改。

## 编码前审计结论

| 设计要求 | 现有实现 | 缺口 | 计划修改文件 | 验收测试 |
|---|---|---|---|---|
| XML Gutter、右键、Intention；可唯一解析的 Java 注解 Mapper | 仅 XML `EditorPopupMenu` Action；XML PSI 能取 module/namespace/id | 无 Gutter、Intention、Java PSI；Action 仅识别 XML | `actions/*`、`mybatis/*`、`plugin.xml` | XML/Java PSI fixture；descriptor/action/keymap 测试 |
| module 必须来自 PSI；数据源 statement > module > project | `ModuleUtilCore` 获取 module；只有 module > project，随后自动写回 module | 无 Run 临时选择；歧义仅抛文本异常；错误地把单次解析写回默认 | `context/*`、`settings/ProjectAnalyzerSettings.java`、Tool Window | 优先级、缺失、歧义、不可伪造 module 测试 |
| 两个以上动态条件进入主场景确认 | 无 | 无动态节点 DTO、模型或 UI | `contract/*`、`scenario/*`、`ui/MainScenarioPanel.java` | 两条件阈值、共享参数、类型冲突 |
| `<if>` 复选、`<choose>` 单选、`<foreach>` 空/单/多值；嵌套联动 | 无 | 全部缺失 | `scenario/*`、`ui/MainScenarioPanel.java` | 控件类型、choose 互斥、foreach、父子禁用 |
| 五类可解释分类、建议来源/版本/locator/置信度/原因、空格 fallback | 无 | 全部缺失 | `contract/SuggestionDtos.java`、`scenario/MainScenarioModel.java` | 分类回退、低置信度文字标识、`␠ 1个空格` |
| 后端官方 MyBatis BoundSql 预览；Plugin 不拼 SQL | Plugin 只读取最终 Report 的 `boundSql` | 无 preview Client/DTO/校验展示 | `client/BackendClient.java`、`contract/PluginApiDtos.java`、主场景 UI | Fake Gateway 契约；验证请求不创建 Run |
| 四个主 Tab、持续上下文栏、完整业务状态机 | 三个 Tab；顶部是 endpoint/token/datasource 文本框；状态为任意字符串 | 缺“证据”；无上下文栏、只读横幅、reducer；业务状态与连接状态耦合 | `state/*`、`ui/SqlAnalyzerToolWindowFactory.java`、`ui/components/*` | reducer 全迁移；Run/SSE 正交；DML 横幅 |
| 五类强制守卫；required/main path 不可排除 | 数据源错误抛异常；其余无 | 无结构化守卫与确认模型 | `state/Guard.java`、`scenario/ScenarioReviewModel.java`、UI | 五类守卫、required 不可排除 |
| Run 类型化临时规则与显式影响预览 | 无 | 全部缺失 | `contract/*`、`rules/*`、`ui/TransientRulesPanel.java`、Client | 类型校验；仅显式 preview；差异投影 |
| 卡片报告、深链导航、真实 EXPLAIN 才启用完整计划 | 报告是一个 `JTextArea`；场景是平面表；建议是字符串列表 | 无卡片、证据 Tab、稳定 ID 导航/返回路径、计划守卫 | `report/*`、`navigation/*`、四 Tab UI | risk/scenario/evidence/locator 双向导航；计划启用条件 |
| Recommendation 服务端审计，拒绝需原因 | 已调用 decision API；拒绝弹输入框 | 不显示反馈操作者/时间/当前决定；错误未分类 | 报告卡片、Client DTO | 接受/拒绝 body；拒绝空原因禁止 |
| 三种重新分析、完整指纹过期、Markdown/JSON 导出 | 无 | 全部缺失 | `report/ContextFingerprint.java`、`report/ReportExportService.java`、Tool Window | 指纹变化、三 action、Markdown/JSON；无 PDF |
| 历史筛选、有界本地缓存、无硬删除 | 仅加载 session 消息文本 | 无服务端 Report 历史模型、筛选、本地上限/清理 | `history/*`、Client、设置/UI | 查询参数、LRU 上限、清理不调用 DELETE |
| Token 仅 PasswordSafe；真实 expiresAt；401 保留草稿 | PasswordSafe 已有，但 Tool Window 把 Token 放入明文可编辑字段并回填 | 明文 UI 暴露；无 auth 状态/expiresAt/草稿保留 | `settings/TokenStore.java`、`auth/*`、Tool Window、Configurable | PasswordSafe 结构门禁、Token 日志/XML 负例、401 reducer |
| 网络/429/retryable 5xx 有界退避；非重试错误不盲重试；复用幂等键 | 每次 POST 生成新幂等键；普通 Client 不重试；SSE 无限重连 | 无 Problem Details 分类；幂等重试会换 key；SSE 无上限 | `client/*` | 429/5xx 有界重试、401/validation/unsupported/conflict 不重试、key/cursor 复用 |
| SSE 断线续传、去重、取消；状态不从日志猜 | 已有 Last-Event-ID 与数值 ID 去重；取消 REST 已有 | 连接状态未结构化；无限重连；Action 通过 status 文本和 renderer 驱动完成 | `client/AguiSseClient.java`、`state/*`、Coordinator | 断线续传、重复 ID、取消 single-flight、连接/Run 正交 |
| IntelliJ 原生组件/主题/缩放/键盘/EDT | 部分 JBLabel/JBTable；网络在 `CompletableFuture` | 大量原生 Swing 文本框/按钮；无 Configurable、Gutter/Intention；无可访问名称与键盘验收 | UI components、actions、`plugin.xml` | descriptor、组件语义/Tab 顺序、无硬编码颜色、EDT 门禁 |
| 自动化、runIde 和人工验收记录 | 旧 smoke 仅检查基础按钮；contractTest 已接 acceptance | smoke 文案与产品 UI 不匹配；缺人工 P1 清单 | tests、`scripts/run-ide-ui-smoke.*`、`docs/delivery-test-checklist.md` | `contractTest`、`check/buildPlugin/verifyPluginStructure`、acceptance、runIde |

## 冻结边界

- Plugin 不实现或复制 MyBatis 动态 SQL 解析器；动态 nodeId、参数类型、命中节点和 BoundSql 均信任后端官方 MyBatis 运行时契约。
- Plugin 只做 PSI 定位、类型化 UI 数据建模、字段级客户端预校验、状态投影与安全守卫展示。
- P1 缺失服务端能力先按 `idea-plugin/docs/p1-plugin-api-contract.md` 冻结，并由 Fake Gateway 覆盖；不得用本地硬编码成功结果冒充后端。
- 不修改 Knowledge/Admin Web、Profiling、Metadata、Flyway、RBAC 或 `BearerClients`。
