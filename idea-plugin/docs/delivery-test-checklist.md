# IDEA Plugin P1 交付测试清单

## 自动化门禁

在仓库根目录执行：

```bash
./gradlew clean test bootJar --no-daemon --console=plain
cd idea-plugin
../gradlew clean check buildPlugin verifyPluginStructure --no-daemon --console=plain
cd ..
bash scripts/acceptance.sh --local
```

Plugin `contractTest` 覆盖：

- UI reducer 与业务 Run/SSE 连接正交状态；
- `<if>`、`<choose>`、`<foreach>`、嵌套、共享参数和类型冲突；
- 默认建议来源、低置信度文字标签和 `␠ 1个空格`；
- BoundSql preview、临时规则 preview、Run confirm 的 consumer contract；
- statement/module/project 数据源优先级、缺失与歧义；
- 五类强制守卫、required/main path/guard 场景不可排除；
- SSE Last-Event-ID 续传、去重、取消、401 与有界重连；
- 网络/429/retryable 5xx 幂等重试及 validation/401 非重试；
- 报告上下文指纹、风险—场景—证据导航和真实 EXPLAIN 门禁；
- PasswordSafe/Token 泄露负例、Action/Keymap、DML 固定只读提示；
- Markdown/标准 JSON 导出、本地缓存上限和无服务端硬删除。

## runIde 自动冒烟

在已登录 macOS 桌面会话且 Terminal/Codex 有 Accessibility 权限时执行：

```bash
cd idea-plugin
../gradlew uiSmoke --no-daemon --console=plain
```

脚本启动隔离 `runIde` sandbox，经 `View -> Tool Windows -> SQL Analyzer` 打开 Tool
Window，并确认 IntelliJ 主窗口和可访问 UI 树仍存在。无 GUI/Accessibility 时以环境前置条件失败，
不得记成功。

## 真实 IntelliJ 人工验收

记录每项的 IntelliJ build、Light/Darcula、缩放、结果与证据：

1. 空状态显示“报告/场景矩阵/证据/运行日志”四 Tab；不显示 Token、namespace、
   statementId、runId 文本输入框。
2. XML statement 的 Gutter、右键和 Alt/Option+Enter Intention 均可发起；Action 出现在
   Keymap 且没有占用 Ctrl/Cmd+Shift+A。
3. 单个静态 `@Select/@Update/@Insert/@Delete` Java 方法可识别；动态注解表达式或多个注解
   安全禁用。
4. 数据源缺失/歧义显示持久守卫卡；module 名来自 PSI 且不可编辑。
5. 两个以上动态条件显示一次主场景；IF 复选、CHOOSE 单选、FOREACH 空/单/多值和父子禁用正确。
6. 低置信度同时显示警告图标、文字和原因，默认不勾选；空格 fallback 显示 `␠ 1个空格`。
7. “预览 BoundSql”仅显式点击请求，显示脱敏 SQL、命中 nodeId 和逐字段错误，不创建 Run。
8. 五类强制守卫均不能绕过；required/main path/guard 场景不可排除。
9. Run 业务状态和 SSE 连接状态分别显示；断线后沿 Last-Event-ID 恢复，不重复事件。
10. 取消在 SSE 断线时仍可用且 single-flight；NOT_CANCELLABLE 恢复运行状态。
11. 报告显示卡片摘要、风险、建议、限制；风险—场景—证据可往返；无 EXPLAIN 时完整计划禁用。
12. Recommendation 拒绝空原因不可提交；成功后显示服务端审计反馈。
13. Mapper/data source/knowledge/profile 任一指纹变化显示“报告已过期”。
14. “重新分析”三个选项分别沿用参数、刷新上下文、切换数据源，并创建新 Run。
15. 导出只有 Markdown 和标准 JSON；无 PDF；后端未给授权 URL 时无分享链接。
16. 历史由服务端筛选；清理本地缓存明确不删除服务端报告。
17. Token 只显示状态；仅后端返回 `expiresAt` 时显示时间；401 后主场景/临时规则草稿保留。
18. UPDATE/INSERT/DELETE 在运行前、中、后持续显示固定只读横幅。
19. Tab/方向键/Enter/Esc 可操作；状态不只靠颜色；Light/Darcula 与 100%/150% 缩放无截断。
20. 网络与解析期间编辑器/Tool Window 保持响应，EDT 无阻塞。

实际记录写入 `idea-plugin/docs/p1-runide-acceptance-record.md`。
