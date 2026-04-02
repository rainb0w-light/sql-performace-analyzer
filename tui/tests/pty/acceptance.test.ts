/**
 * TUI 验收测试 - WebSocket E2E 测试
 *
 * 注意：由于 Ink 应用需要 TTY 环境（raw mode），无法在 PTY 测试中直接启动 TUI。
 * 我们使用 WebSocket E2E 测试来验证 /analyze 命令的完整流程。
 *
 * 测试场景:
 * 1. 通过 WebSocket 连接到后端
 * 2. 发送 /analyze 命令
 * 3. 等待分析报告
 * 4. 使用 ReportValidator 验证报告内容
 *
 * 运行方式：bun test tests/pty/acceptance.test.ts --timeout 120000
 * 前提条件：后端服务运行在 ws://localhost:18881/ws
 */

import { describe, it, expect, afterEach, beforeEach } from 'bun:test';
import { Client, IMessage } from '@stomp/stompjs';
import { ReportValidator } from '../../src/test-utils/ReportValidator';

const WS_URL = process.env.WS_URL || 'ws://localhost:18881/ws';
const LLM_TIMEOUT = 90000; // LLM 调用可能需要较长时间

describe('TUI 验收测试', { timeout: 120000 }, () => {
  let stompClient: Client;
  let messages: any[];

  beforeEach(async () => {
    messages = [];
    stompClient = new Client({
      brokerURL: WS_URL,
      connectHeaders: {},
      debug: () => {},
      reconnectDelay: 0,
    });

    // 等待连接建立
    await new Promise<void>((resolve, reject) => {
      stompClient.onConnect = () => {
        console.log('[WebSocket] 已连接到', WS_URL);
        resolve();
      };
      stompClient.onError = (error) => {
        console.error('[WebSocket] 连接错误:', error);
        reject(error);
      };
      stompClient.activate();

      // 连接超时保护
      setTimeout(() => reject(new Error('STOMP 连接超时')), 15000);
    });

    // 订阅响应消息
    stompClient.subscribe('/topic/response', (frame: IMessage) => {
      const data = JSON.parse(frame.body);
      console.log('[WS] 收到消息:', data.type);
      messages.push(data);
    });

    // 订阅会话更新消息
    stompClient.subscribe('/topic/session/*', (frame: IMessage) => {
      const data = JSON.parse(frame.body);
      console.log('[WS] 收到会话更新:', data.type);
      messages.push(data);
    });

    // 等待订阅生效
    await new Promise(resolve => setTimeout(resolve, 500));
  });

  afterEach(() => {
    if (stompClient && stompClient.connected) {
      stompClient.deactivate();
    }
    messages = [];
  });

  const sendCommand = (command: string, sessionId: string = 'test-acceptance'): Promise<void> => {
    return new Promise((resolve) => {
      stompClient.publish({
        destination: '/app/message',
        body: JSON.stringify({
          type: 'TUI_COMMAND',
          sessionId: sessionId,
          payload: { command },
        }),
      });
      // 给消息一点时间发送
      setTimeout(resolve, 100);
    });
  };

  const waitForMessage = (
    predicate: (msg: any) => boolean,
    timeout: number = 30000,
    description: string = '消息'
  ): Promise<any> => {
    return new Promise((resolve, reject) => {
      const startTime = Date.now();

      const check = () => {
        const found = messages.find(predicate);
        if (found) {
          resolve(found);
          return;
        }

        if (Date.now() - startTime > timeout) {
          console.log(`[Timeout] 等待 ${description} 超时`);
          reject(new Error(`等待 ${description} 超时`));
          return;
        }

        setTimeout(check, 500);
      };

      check();
    });
  };

  /**
   * 测试用例 1: WebSocket 连接测试
   * 验证能够成功连接到后端 STOMP 服务
   */
  it('AC-001: WebSocket 连接建立', async () => {
    console.log('\n[AC-001] 开始测试：WebSocket 连接');
    expect(stompClient.connected).toBe(true);
    console.log('[AC-001] 测试通过：WebSocket 连接成功');
  });

  /**
   * 测试用例 2: /help 命令测试
   * 验证帮助命令能够正常返回帮助信息
   */
  it('AC-002: /help 命令返回帮助信息', async () => {
    console.log('\n[AC-002] 开始测试：/help 命令');

    await sendCommand('/help');

    const result = await waitForMessage(
      (msg) => msg.type === 'TUI_COMMAND_RESULT',
      10000,
      '/help 响应'
    );

    expect(result.payload).toBeDefined();
    const message = result.payload.message || result.payload;
    expect(message).toContain('GoldenDB SQL Analyzer');
    expect(message).toContain('/analyze');
    expect(message).toContain('/sql');

    console.log('[AC-002] 测试通过：/help 命令返回完整帮助信息');
  });

  /**
   * 测试用例 3: /analyze 命令 - 缺少参数错误处理
   * 验证当 /analyze 命令缺少文件路径参数时，能够正确返回错误提示
   */
  it('AC-003: /analyze 命令缺少参数时返回错误提示', async () => {
    console.log('\n[AC-003] 开始测试：/analyze 参数校验');

    await sendCommand('/analyze');

    const result = await waitForMessage(
      (msg) => msg.type === 'TUI_COMMAND_RESULT',
      10000,
      '/analyze 错误响应'
    );

    console.log('[AC-003] 收到消息:', JSON.stringify(result.payload, null, 2));

    expect(result.payload?.success).toBe(false);
    const message = result.payload.message || result.payload;
    // 后端返回的错误消息是中文
    expect(message).toContain('请指定');
    expect(message.toLowerCase()).toContain('mapper');

    console.log('[AC-003] 测试通过：参数校验正确');
  });

  /**
   * 测试用例 4: /analyze 命令 - 分析 TransactionMapper.xml 文件
   * 完整的验收测试场景：
   * 1. 发送 /analyze 命令和文件路径
   * 2. 等待 ANALYSIS_START 消息
   * 3. 等待 TUI_COMMAND_RESULT 消息（包含分析报告）
   * 4. 验证报告内容（使用 ReportValidator 评分）
   */
  it('AC-004: /analyze 命令分析 TransactionMapper.xml 并生成报告', async () => {
    console.log('\n[AC-004] 开始测试：/analyze 完整流程');
    const validator = new ReportValidator();
    const filePath = 'src/test/resources/mapper/TransactionMapper.xml';

    // 清空之前的消息
    messages = [];

    // 发送 /analyze 命令
    console.log(`[AC-004] 发送命令：/analyze ${filePath}`);
    await sendCommand(`/analyze ${filePath}`);

    // 阶段 1: 等待 ANALYSIS_START 消息
    const startMsg = await waitForMessage(
      (msg) => msg.type === 'ANALYSIS_START',
      10000,
      'ANALYSIS_START'
    );
    expect(startMsg).toBeDefined();
    console.log('[AC-004] 分析已开始:', startMsg.payload?.description || '正在分析...');

    // 阶段 2: 等待分析响应（完整的分析报告）
    console.log('[AC-004] 等待分析报告 (最多 120 秒)...');

    const anyResultMsg = await waitForMessage(
      (msg) => msg.type === 'TUI_COMMAND_RESULT',
      120000,
      'TUI_COMMAND_RESULT'
    );

    const message = anyResultMsg.payload?.message || '';
    console.log(`[AC-004] 收到分析报告，长度：${message.length} 字符`);

    // 阶段 3: 验证报告内容（使用 ReportValidator 评分）
    console.log('[AC-004] 验证报告内容...');

    // 验证包含基本元素
    expect(message).toContain('TransactionMapper');
    expect(message).toContain('分析报告');

    // 使用 ReportValidator 进行评分
    const score = validator.validate(message);
    console.log(`[AC-004] 报告评分：${score.score}/100 (通过线：60 分)`);

    // 验证通过分数线
    expect(score.score).toBeGreaterThanOrEqual(60);
    expect(score.passed).toBe(true);

    console.log('[AC-004] 测试通过！');
  });

  /**
   * 测试用例 5: /sql 命令 - 分析单条 SQL 语句
   * 验证 /sql 命令能够分析单条 SQL 语句并返回分析结果
   */
  it('AC-005: /sql 命令分析单条 SQL 语句', async () => {
    console.log('\n[AC-005] 开始测试：/sql 命令');

    const sql = 'SELECT * FROM users WHERE id = 1';
    await sendCommand(`/sql ${sql}`);

    // 等待分析完成
    const completeMsg = await waitForMessage(
      (msg) => msg.type === 'ANALYSIS_COMPLETE' || msg.type === 'TUI_COMMAND_RESULT',
      30000,
      '/sql 响应'
    );

    const content = completeMsg.payload?.report || completeMsg.payload?.message || '';
    expect(content.length).toBeGreaterThan(0);

    console.log('[AC-005] 测试通过：/sql 命令返回分析结果');
  });

  /**
   * 测试用例 6: 连续命令执行测试
   * 验证能够连续发送多个命令并获取响应
   */
  it('AC-006: 连续执行多个命令', async () => {
    console.log('\n[AC-006] 开始测试：连续命令执行');

    // 清空消息
    messages = [];

    // 命令 1: /help
    console.log('[AC-006] 执行命令 1: /help');
    await sendCommand('/help');
    await waitForMessage(
      (msg) => msg.type === 'TUI_COMMAND_RESULT',
      10000,
      '/help 响应'
    );

    // 命令 2: /session list
    console.log('[AC-006] 执行命令 2: /session list');
    await sendCommand('/session list');
    await waitForMessage(
      (msg) => msg.type === 'TUI_COMMAND_RESULT',
      10000,
      '/session list 响应'
    );

    // 验证两个命令都有响应
    const commandResults = messages.filter(
      (msg) => msg.type === 'TUI_COMMAND_RESULT'
    );
    expect(commandResults.length).toBeGreaterThanOrEqual(2);

    console.log('[AC-006] 测试通过：连续命令执行成功');
  });

  /**
   * 测试用例 7: /analyze 命令 - 分析 UserMapper.xml 文件
   * 验证能够分析包含复杂动态 SQL 和关联查询的 Mapper 文件
   */
  it('AC-007: /analyze 命令分析 UserMapper.xml 并生成报告', async () => {
    console.log('\n[AC-007] 开始测试：/analyze UserMapper.xml');
    const validator = new ReportValidator();
    const filePath = 'src/test/resources/mapper/UserMapper.xml';

    // 清空之前的消息
    messages = [];

    // 发送 /analyze 命令
    console.log(`[AC-007] 发送命令：/analyze ${filePath}`);
    await sendCommand(`/analyze ${filePath}`);

    // 阶段 1: 等待 ANALYSIS_START 消息
    const startMsg = await waitForMessage(
      (msg) => msg.type === 'ANALYSIS_START',
      10000,
      'ANALYSIS_START'
    );
    expect(startMsg).toBeDefined();
    console.log('[AC-007] 分析已开始:', startMsg.payload?.description || '正在分析...');

    // 阶段 2: 等待分析响应（完整的分析报告）
    console.log('[AC-007] 等待分析报告 (最多 120 秒)...');

    const anyResultMsg = await waitForMessage(
      (msg) => msg.type === 'TUI_COMMAND_RESULT',
      120000,
      'TUI_COMMAND_RESULT'
    );

    const message = anyResultMsg.payload?.message || '';
    console.log(`[AC-007] 收到分析报告，长度：${message.length} 字符`);

    // 阶段 3: 验证报告内容（使用 ReportValidator 评分）
    console.log('[AC-007] 验证报告内容...');

    // 验证包含基本元素（UserMapper 文件名称或解析结果）
    expect(message).toMatch(/(UserMapper|解析|Mapper|XML)/i);

    // 使用 ReportValidator 进行评分
    const score = validator.validate(message);
    console.log(`[AC-007] 报告评分：${score.score}/100 (通过线：60 分)`);

    // 验证通过分数线
    expect(score.score).toBeGreaterThanOrEqual(60);
    expect(score.passed).toBe(true);

    console.log('[AC-007] 测试通过！');
  });
});
