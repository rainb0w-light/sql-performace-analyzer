# IDEA Plugin UI/UX P1 runIde 验收记录

日期：2026-07-28

分支/提交：`codex/idea-plugin-ui-ux` / `a0a030c`（最终功能提交）

IDE：IntelliJ IDEA Community 2025.1.3（runIde sandbox）

环境：macOS，当前用户图形会话

## 自动 UI 冒烟

- 命令：`../gradlew uiSmoke --no-daemon --console=plain`
- 结果：`BLOCKED-ENV`
- 证据：
  - `uiSmoke` 返回 `No accessible macOS GUI session is available`（exit 2）；
  - 直接 `runIde` 返回 `Unable to detect graphics environment`；
  - Computer Use 读取 IntelliJ/App 列表均超时，无法取得可访问树或截图；
  - 直接启动日志：`/tmp/sql-analyzer-p1-runide.log`。

Plugin 的 `contractTest`、`verifyPluginStructure`、`buildPlugin` 和仓库
`scripts/acceptance.sh --local` 均已真实通过。因当前执行环境没有可访问图形会话，以下项目不冒充人工
成功；需在有 GUI + Accessibility 权限的 macOS 会话复验。

最终自动化结果：

- Backend：165 tests，0 failure/error，29 skipped；
- Plugin：45 tests，0 failure/error；
- Plugin 包：`idea-plugin/build/distributions/sql-performance-analyzer-idea-plugin-0.1.0.zip`；
- `scripts/acceptance.sh --local`：PASS（2026-07-28 18:47 Asia/Shanghai）。

## 人工检查

| 检查项 | 结果 | 记录 |
|---|---|---|
| Tool Window 打开与四 Tab | BLOCKED-ENV | Plugin 结构/源码门禁通过；无 GUI 可视复验 |
| 上下文栏、状态栏、Token 不明文 | BLOCKED-ENV | Token 泄露负例测试通过；无 GUI 可视复验 |
| Light/Darcula 与缩放 | BLOCKED-ENV | 无可访问图形会话 |
| 键盘 Tab/方向键/Enter/Esc | BLOCKED-ENV | Action/控件结构门禁通过；无 GUI 可操作复验 |
| XML Gutter/右键/Intention | BLOCKED-ENV | descriptor 与 PSI 结构门禁通过；无编辑器可操作复验 |
| Java 注解唯一解析/安全降级 | BLOCKED-ENV | Java PSI consumer 结构门禁通过；无编辑器可操作复验 |
| 多动态条件控件与 BoundSql preview | BLOCKED-EXTERNAL | 基线后端尚未部署 P1 suggest/preview 契约；由 Fake Gateway 自动测试覆盖 |
| 数据源/`${}`/成本/UNSUPPORTED 守卫 | BLOCKED-EXTERNAL | 基线后端尚未返回 P1 结构化守卫；reducer/model 自动测试覆盖 |
| SSE 续传/取消/终态 | BLOCKED-EXTERNAL | 需要可运行 Worker；Fake Gateway 自动测试覆盖 |
| 报告卡片/深链/过期/导出 | BLOCKED-ENV | ViewModel/navigation/fingerprint/export 自动测试通过；无 GUI 可视复验 |
| DML 固定只读横幅 | BLOCKED-ENV | 安全 UI 结构门禁通过；无 GUI 可视复验 |

## 外部集成阻塞

`origin/master@ea30131` 不提供以下真实端点：default parameter suggest、BoundSql preview、
transient rule impact preview、Run confirm、Run status、服务端报告筛选、Java 注解 Mapper index。
Plugin 对 404/Problem Details 显示“服务端 P1 能力尚未部署”，不使用本地硬编码生产结果。
