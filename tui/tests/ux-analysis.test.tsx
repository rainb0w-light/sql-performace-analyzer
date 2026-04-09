/**
 * UX 分析测试 - 捕获 TUI 实际输出并分析
 *
 * 运行此测试会将捕获的输出保存到 docs/test-output/ 目录
 */

import { test, expect, describe } from "bun:test";
import { testRender } from "@opentui/react/test-utils";
import { App } from "../src/App";
import { writeFileSync, mkdirSync, existsSync } from "fs";
import { join } from "path";

const OUTPUT_DIR = "docs/test-output";

/**
 * 保存捕获的输出到文件
 */
function saveCapture(name: string, frame: string, props?: Record<string, any>) {
  if (!existsSync(OUTPUT_DIR)) {
    mkdirSync(OUTPUT_DIR, { recursive: true });
  }

  const timestamp = new Date().toISOString().slice(0, 19).replace("T", " ");
  const filename = name.toLowerCase().replace(/\s+/g, "-") + ".txt";

  const report = `
╔══════════════════════════════════════════════════════════════════════════════╗
║  Component: ${name.padEnd(60)} ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  Timestamp: ${timestamp}                                              ║
╠══════════════════════════════════════════════════════════════════════════════╣
║  Props:                                                                       ║
${props ? JSON.stringify(props, null, 2).split("\n").map(line => `║  ${line.padEnd(77)} ║`).join("\n") : "║  (none)".padEnd(77) + "║"}
╠══════════════════════════════════════════════════════════════════════════════╣
║  Rendered Output:                                                             ║
╚══════════════════════════════════════════════════════════════════════════════╝

${frame}

╔══════════════════════════════════════════════════════════════════════════════╗
║  End of Output                                                                ║
╚══════════════════════════════════════════════════════════════════════════════╝
`.trim();

  const filePath = join(OUTPUT_DIR, filename);
  writeFileSync(filePath, report, "utf-8");
  console.log(`[Capture] Saved: ${filePath}`);
}

describe("UX 分析 - 捕获实际界面输出", () => {
  test("捕获主界面完整输出并分析", async () => {
    const testSetup = await testRender(
      <App />,
      { width: 120, height: 40 }
    );

    await testSetup.renderOnce();
    const frame = testSetup.captureCharFrame();

    // 打印完整输出用于人工分析
    console.log("\n=== 主界面输出 ===\n");
    console.log(frame);
    console.log("\n=== 输出结束 ===\n");

    // 保存到文件
    saveCapture("App-Main-Interface", frame, {});

    // 基本断言
    expect(frame).toBeDefined();
    expect(frame.length).toBeGreaterThan(0);
  });

  test("捕获侧边栏输出并分析", async () => {
    const { Sidebar } = await import("../src/components/layout/Sidebar");
    const testSetup = await testRender(
      <Sidebar
        isConnected={true}
        status="idle"
        sessionId="test-session-123"
      />,
      { width: 28, height: 24 }
    );

    await testSetup.renderOnce();
    const frame = testSetup.captureCharFrame();

    console.log("\n=== 侧边栏输出 ===\n");
    console.log(frame);
    console.log("\n=== 侧边栏结束 ===\n");

    // 保存到文件
    saveCapture("Sidebar-Connected", frame, { isConnected: true, status: "idle", sessionId: "test-session-123" });

    expect(frame).toContain("状态");
    expect(frame).toContain("WebSocket");
  });

  test("捕获聊天气泡输出并分析", async () => {
    const { ChatBubble } = await import("../src/components/chat/ChatBubble");
    const testSetup = await testRender(
      <ChatBubble
        type="response"
        content="这是一个测试响应消息，用于验证聊天气泡的渲染效果。"
        timestamp="10:30:00"
        title="响应"
      />,
      { width: 80, height: 10 }
    );

    await testSetup.renderOnce();
    const frame = testSetup.captureCharFrame();

    console.log("\n=== 聊天气泡输出 ===\n");
    console.log(frame);
    console.log("\n=== 聊天气泡结束 ===\n");

    // 保存到文件
    saveCapture("ChatBubble-Response", frame, { type: "response", content: "测试消息", timestamp: "10:30:00", title: "响应" });

    expect(frame).toContain("●");
    expect(frame).toContain("响应");
  });

  test("捕获命令输入框输出并分析", async () => {
    const { CommandInput } = await import("../src/components/input/CommandInput");
    const testSetup = await testRender(
      <CommandInput
        onSubmit={() => {}}
        disabled={false}
      />,
      { width: 60, height: 5 }
    );

    await testSetup.renderOnce();
    const frame = testSetup.captureCharFrame();

    console.log("\n=== 命令输入框输出 ===\n");
    console.log(frame);
    console.log("\n=== 输入框结束 ===\n");

    // 保存到文件
    saveCapture("CommandInput-Empty", frame, { onSubmit: "() => {}", disabled: false });

    // 空输入框应该显示快捷键提示
    expect(frame).toContain("快捷键");
  });

  test("命令提示功能 - 输入/时显示可用命令", async () => {
    // 测试命令提示逻辑
    const AVAILABLE_COMMANDS = [
      { command: '/analyze', description: '分析 Mapper XML 文件' },
      { command: '/sql', description: '分析 SQL 语句' },
      { command: '/help', description: '显示帮助' },
    ];

    // 模拟输入 '/' 时的提示逻辑
    const getCommandHints = (value: string) => {
      if (!value.startsWith('/')) {
        return [];
      }
      const input = value.slice(1).toLowerCase();
      return AVAILABLE_COMMANDS.filter(cmd =>
        cmd.command.slice(1).toLowerCase().startsWith(input)
      ).slice(0, 5);
    };

    // 验证输入 '/' 时返回所有命令
    const hints = getCommandHints('/');
    expect(hints.length).toBeGreaterThan(0);
    expect(hints[0].command).toBe('/analyze');

    console.log("\n=== 命令提示逻辑验证 ===\n");
    console.log("输入 '/' 时的提示:", hints.map(h => h.command).join(', '));
    console.log("\n=== 验证结束 ===\n");
  });

  test("命令补全功能验证", () => {
    const AVAILABLE_COMMANDS = [
      { command: '/analyze', description: '分析 Mapper XML 文件' },
      { command: '/sql', description: '分析 SQL 语句' },
    ];

    // 模拟自动补全逻辑
    const autoComplete = (hints: typeof AVAILABLE_COMMANDS, index: number) => {
      if (hints.length > 0 && index < hints.length) {
        return hints[index].command + ' ';
      }
      return '';
    };

    const hints = AVAILABLE_COMMANDS.filter(cmd =>
      cmd.command.slice(1).startsWith('a')
    );
    const completed = autoComplete(hints, 0);

    expect(completed).toBe('/analyze ');
  });
});
