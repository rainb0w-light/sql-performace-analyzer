# OpenTUI 测试指南

本目录包含基于 OpenTUI 测试框架的完整验收测试套件。

## 测试文件结构

```
tui/tests/
├── acceptance.test.tsx  # 组件级单元测试
├── e2e.test.tsx         # 端到端交互测试
└── README.md            # 本文件
```

## 测试能力说明

### 1. 组件单元测试 (`acceptance.test.tsx`)

使用 `@opentui/react/test-utils` 的 `testRender` 进行组件测试：

**特点：**
- 快速执行（无真实 TUI 启动）
- 支持快照测试
- 可模拟键盘/鼠标输入
- 可捕获渲染帧进行断言

**测试覆盖：**
- `ChatHistory` - 消息列表渲染
- `Sidebar` - 侧边栏状态显示
- `CommandInput` - 输入框交互
- `App` - 整体布局

**运行命令：**
```bash
bun test tests/acceptance.test.tsx
```

### 2. 端到端测试 (`e2e.test.tsx`)

使用 `SimplePTYRunner` 进行真实 TUI 交互测试：

**特点：**
- 启动真实的 TUI 应用
- 模拟真实键盘输入
- 捕获实际输出验证
- 验证完整用户流程

**测试场景：**
1. TUI 启动并显示主界面
2. 执行 `/help` 命令
3. SQL 分析报告生成
4. 文件分析报告生成
5. 连续命令执行
6. 错误命令处理
7. 长时间分析任务
8. 侧边栏状态显示

**运行命令：**
```bash
bun test tests/e2e.test.tsx --timeout 120000
```

## 测试 API 参考

### OpenTUI 测试渲染器

```typescript
import { testRender } from "@opentui/react/test-utils"

const testSetup = await testRender(
  <MyComponent />,
  { width: 80, height: 24 }
)

// 捕获输出
const frame = testSetup.captureCharFrame()

// 模拟输入
testSetup.mockInput.typeText("hello")
testSetup.mockInput.pressEnter()

// 重新渲染
await testSetup.renderOnce()
```

### SimplePTYRunner

```typescript
import { SimplePTYRunner } from "../src/test-utils/SimplePTYRunner"

const runner = new SimplePTYRunner({
  cols: 120,
  rows: 40,
  timeout: 30000,
})

// 启动进程
await runner.start("bun", ["run", "src/main.tsx"])

// 发送按键
await runner.sendKeys("/help")
await runner.sendKeys("Enter")

// 等待文本出现
const found = await runner.waitForText("帮助", 5000)

// 获取输出
const buffer = runner.getBuffer()
```

### ReportValidator

```typescript
import { ReportValidator } from "../src/test-utils/ReportValidator"

const validator = new ReportValidator()
const result = validator.validate(reportContent)

console.log(result.score)  // 0-100
console.log(result.passed) // true/false
```

## 测试开发最佳实践

### 1. 组件测试模式

```typescript
import { test, expect, afterEach } from "bun:test"
import { testRender } from "@opentui/react/test-utils"

let testSetup: Awaited<ReturnType<typeof testRender>>

afterEach(() => {
  testSetup?.renderer.destroy()
})

test("组件渲染测试", async () => {
  testSetup = await testRender(<MyComponent />, {
    width: 60,
    height: 20,
  })

  await testSetup.renderOnce()
  const frame = testSetup.captureCharFrame()

  expect(frame).toContain("预期内容")
})
```

### 2. E2E 测试模式

```typescript
import { test, expect, afterEach } from "bun:test"
import { SimplePTYRunner } from "../src/test-utils/SimplePTYRunner"

let runner: SimplePTYRunner

afterEach(() => {
  runner?.stop()
})

test("完整用户流程", async () => {
  runner = new SimplePTYRunner({
    cols: 120,
    rows: 40,
  })

  await runner.start("bun", ["run", "src/main.tsx"])
  await runner.sleep(2000)

  await runner.sendKeys("/help")
  await runner.sendKeys("Enter")

  expect(await runner.waitForText("帮助", 5000)).toBe(true)
}, 10000)
```

### 3. 快照测试

```typescript
test("布局快照", async () => {
  testSetup = await testRender(<MyComponent />, {
    width: 80,
    height: 24,
  })

  await testSetup.renderOnce()
  expect(testSetup.captureCharFrame()).toMatchSnapshot()
})
```

更新快照：
```bash
bun test --update-snapshots
```

### 4. 报告质量验证

```typescript
const report = extractReportFromBuffer(runner.getBuffer())

const validator = new ReportValidator()
const result = validator.validate(report)

// 验证报告质量
expect(result.score).toBeGreaterThan(60)
expect(result.passed).toBe(true)

// 查看详细评分
console.log(result.details.map(d => 
  `${d.criterion}: ${d.passed ? '✓' : '✗'} (${d.weight}分)`
))
```

## 运行所有测试

```bash
# 运行所有测试
bun test

# 运行特定测试文件
bun test tests/acceptance.test.tsx
bun test tests/e2e.test.tsx

# 更新快照
bun test --update-snapshots

# 运行特定测试（按名称过滤）
bun test --filter "ChatHistory"
```

## 测试验收标准

### 组件测试标准
- [ ] 所有组件渲染正确
- [ ] 交互事件正常工作
- [ ] 快照测试通过
- [ ] 边界情况处理正确

### E2E 测试标准
- [ ] TUI 正常启动
- [ ] 命令执行有响应
- [ ] 报告生成完整
- [ ] 错误处理正常
- [ ] 报告质量评分 > 60

## 故障排查

### 测试失败常见原因

1. **组件测试失败**
   - 检查组件 props 是否正确
   - 验证主题/样式是否加载
   - 确认 OpenTUI 组件使用正确

2. **E2E 测试超时**
   - 增加 `timeout` 参数
   - 检查后端服务是否运行
   - 验证 WebSocket 连接

3. **报告验证失败**
   - 检查 SQL 分析 API 是否正常
   - 验证报告模板完整性
   - 调整验证器权重配置

## 参考资料

- [OpenTUI Testing Guide](../../docs/opentui-testing-guide.md)
- [OpenTUI Test Renderer](https://github.com/anomalyco/opentui/tree/main/packages/core/docs/testing)
- [Bun Test Runner](https://bun.sh/docs/cli/test)
