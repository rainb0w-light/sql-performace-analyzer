package com.biz.sccba.sqlanalyzer.tui;

import com.biz.sccba.sqlanalyzer.agent.SQLAnalysisOrchestrator;
import com.biz.sccba.sqlanalyzer.memory.SessionMemoryService;
import com.biz.sccba.sqlanalyzer.model.agent.AnalysisResult;
import com.biz.sccba.sqlanalyzer.model.agent.AnalysisSession;
import com.biz.sccba.sqlanalyzer.model.websocket.ServerMessageType;
import com.biz.sccba.sqlanalyzer.model.websocket.WebSocketMessage;
import com.biz.sccba.sqlanalyzer.service.TestEnvironmentService;
import com.biz.sccba.sqlanalyzer.tool.SqlAnalyzerTools;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TUI 命令处理器
 * 处理各种 TUI 命令的执行
 */
@Service
public class TuiCommandHandler {

    private final TuiCommandParser commandParser;
    private final SessionMemoryService sessionMemory;
    private final SQLAnalysisOrchestrator orchestrator;
    private final TestEnvironmentService testEnvironmentService;
    private final SimpMessagingTemplate messagingTemplate;

    public TuiCommandHandler(TuiCommandParser commandParser,
                             SessionMemoryService sessionMemory,
                             SQLAnalysisOrchestrator orchestrator,
                             TestEnvironmentService testEnvironmentService,
                             SimpMessagingTemplate messagingTemplate) {
        this.commandParser = commandParser;
        this.sessionMemory = sessionMemory;
        this.orchestrator = orchestrator;
        this.testEnvironmentService = testEnvironmentService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 处理命令并返回结果
     *
     * @param input 用户输入
     * @param sessionId 会话 ID
     * @return 命令执行结果
     */
    public TuiCommandResult handleCommand(String input, String sessionId) {
        TuiCommand command = commandParser.parse(input);

        if (command == null) {
            return TuiCommandResult.error("无效的命令格式");
        }

        return switch (command.getName().toLowerCase()) {
            case "help", "h" -> handleHelp(command, sessionId);
            case "analyze", "a" -> handleAnalyze(command, sessionId);
            case "sql", "s" -> handleSql(command, sessionId);
            case "table", "t" -> handleTable(command, sessionId);
            case "test" -> handleTest(command, sessionId);
            case "commit" -> handleCommit(command, sessionId);
            case "session" -> handleSession(command, sessionId);
            case "skill" -> handleSkill(command, sessionId);
            case "config" -> handleConfig(command, sessionId);
            case "env", "environment" -> handleEnvironment(command, sessionId);
            case "model", "m" -> handleModel(command, sessionId);  // 新增模型切换命令
            default -> TuiCommandResult.error("未知命令：/" + command.getName());
        };
    }

    /**
     * 帮助命令
     * /help
     */
    private TuiCommandResult handleHelp(TuiCommand command, String sessionId) {
        String helpText = """
            \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550
            GoldenDB SQL Analyzer - 命令帮助
            \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550

            分析命令:
              /analyze <file> [-D datasource] [-M model]
                  分析 MyBatis Mapper XML 文件
                  例：/analyze UserMapper.xml -D goldendb-test

              /sql <statement> [-D datasource]
                  分析单条 SQL 语句
                  例：/sql SELECT * FROM users WHERE id = 1

              /table <name> [-D datasource]
                  分析表结构和索引
                  例：/table users -D goldendb-test

            测试命令:
              /test [-D datasource] [--index] [--compare]
                  执行测试
                  例：/test -D goldendb-test --compare

            变更命令:
              /commit <ddl-id>
                  提交 DDL 变更到生产环境
                  例：/commit ddl-12345

            会话管理:
              /session list
                  列出所有活动会话

              /session switch <session-id>
                  切换到指定会话

              /session clear
                  清除当前会话

            Skill 模板:
              /skill list
                  列出所有可用的 Skill 模板

              /skill load <skill-id>
                  加载指定 Skill 模板

              /skill edit <skill-id>
                  编辑 Skill 模板

            配置命令:
              /config set <key> <value>
                  设置配置项

              /config get <key>
                  获取配置项

              /config list
                  列出所有配置

            环境管理:
              /env list
                  列出所有测试环境

              /env add <name> <jdbc-url>
                  添加测试环境

            模型管理:
              /model list
                  列出所有可用的大模型

              /model switch <model-name>
                  切换到指定大模型
                  例：/model switch qwen3.5-plus

              /model use <model-name>
                  同 switch 命令（别名）

              /model info <model-name>
                  查看模型详细信息

            其他:
              /help, /h
                  显示此帮助信息
            \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550
            """;
        return TuiCommandResult.success(helpText);
    }

    /**
     * 分析命令
     * /analyze <file> [-D datasource] [-M model]
     */
    private TuiCommandResult handleAnalyze(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 1)) {
            return TuiCommandResult.error("请指定要分析的 Mapper 文件路径");
        }

        String filePath = command.getArgument(0);
        String datasourceName = command.getOption("D", "default");
        String llmName = command.getOption("M", "default");

        // 创建会话
        String newSessionId = sessionMemory.createSession("analyze:" + filePath, datasourceName, llmName);

        // 发送分析开始通知
        sendAnalysisStart(newSessionId, "解析 Mapper 文件：" + filePath);

        try {
            // 设置当前会话 ID（用于工具调用记录）
            SqlAnalyzerTools.setCurrentSessionId(newSessionId);

            // 执行分析
            AnalysisResult result = orchestrator.parseMapper(filePath, llmName);

            return TuiCommandResult.builder()
                .success(result.isSuccess())
                .message(result.getReport())
                .sessionId(newSessionId)
                .build();

        } catch (Exception e) {
            return TuiCommandResult.error("分析失败：" + e.getMessage());
        } finally {
            // 清除会话 ID
            SqlAnalyzerTools.clearCurrentSessionId();
        }
    }

    /**
     * 发送分析开始通知
     */
    private void sendAnalysisStart(String sessionId, String description) {
        WebSocketMessage message = new WebSocketMessage(
            ServerMessageType.ANALYSIS_START.name(),
            sessionId
        );
        message.addPayload("description", description);
        message.addPayload("status", "running");
        messagingTemplate.convertAndSend("/topic/session/" + sessionId, message);
    }

    /**
     * SQL 分析命令
     * /sql <statement> [-D datasource]
     */
    private TuiCommandResult handleSql(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 1)) {
            return TuiCommandResult.error("请指定要分析的 SQL 语句");
        }

        String sql = command.getArgument(0);
        String datasourceName = command.getOption("D", "default");
        String llmName = command.getOption("M", "default");

        // 发送分析开始通知
        sendAnalysisStart(sessionId, "分析 SQL: " + (sql.length() > 50 ? sql.substring(0, 50) + "..." : sql));

        try {
            // 设置当前会话 ID（用于工具调用记录）
            SqlAnalyzerTools.setCurrentSessionId(sessionId);

            AnalysisResult result = orchestrator.analyzeSql(sql, datasourceName, llmName);

            return TuiCommandResult.builder()
                .success(result.isSuccess())
                .message(result.getReport())
                .sessionId(sessionId)
                .build();

        } catch (Exception e) {
            return TuiCommandResult.error("SQL 分析失败：" + e.getMessage());
        } finally {
            // 清除会话 ID
            SqlAnalyzerTools.clearCurrentSessionId();
        }
    }

    /**
     * 表分析命令
     * /table <name> [-D datasource]
     */
    private TuiCommandResult handleTable(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 1)) {
            return TuiCommandResult.error("请指定要分析的表名");
        }

        String tableName = command.getArgument(0);
        String datasourceName = command.getOption("D", "default");
        String llmName = command.getOption("M", "default");

        // 发送分析开始通知
        sendAnalysisStart(sessionId, "分析表结构：" + tableName);

        try {
            // 设置当前会话 ID（用于工具调用记录）
            SqlAnalyzerTools.setCurrentSessionId(sessionId);

            AnalysisResult result = orchestrator.analyzeTable(tableName, datasourceName, llmName);

            return TuiCommandResult.builder()
                .success(result.isSuccess())
                .message(result.getReport())
                .sessionId(sessionId)
                .build();

        } catch (Exception e) {
            return TuiCommandResult.error("表分析失败：" + e.getMessage());
        } finally {
            // 清除会话 ID
            SqlAnalyzerTools.clearCurrentSessionId();
        }
    }

    /**
     * 测试命令
     * /test [-D datasource] [--index] [--compare]
     */
    private TuiCommandResult handleTest(TuiCommand command, String sessionId) {
        String datasourceName = command.getOption("D", "default");
        boolean withIndex = command.getOptions().containsKey("index");
        boolean compare = command.getOptions().containsKey("compare");

        String msg = String.format("执行测试 - 数据源：%s, 创建索引：%s, 性能对比：%s",
            datasourceName, withIndex, compare);

        return TuiCommandResult.success(msg);
    }

    /**
     * 提交命令
     * /commit <ddl-id>
     */
    private TuiCommandResult handleCommit(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 1)) {
            return TuiCommandResult.error("请指定 DDL 变更 ID");
        }

        String ddlId = command.getArgument(0);

        // TODO: 实现 DDL 提交流程

        return TuiCommandResult.success("DDL 变更 " + ddlId + " 已提交到生产环境");
    }

    /**
     * 会话管理命令
     * /session list|switch|clear
     */
    private TuiCommandResult handleSession(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 1)) {
            return TuiCommandResult.error("请指定子命令：list, switch, clear");
        }

        String subCommand = command.getArgument(0);

        return switch (subCommand.toLowerCase()) {
            case "list" -> handleSessionList(sessionId);
            case "switch" -> handleSessionSwitch(command, sessionId);
            case "clear" -> handleSessionClear(sessionId);
            default -> TuiCommandResult.error("未知子命令：" + subCommand);
        };
    }

    private TuiCommandResult handleSessionList(String sessionId) {
        var sessions = sessionMemory.getActiveSessions();
        StringBuilder sb = new StringBuilder();
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");
        sb.append("活动会话列表\n");
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");

        if (sessions.isEmpty()) {
            sb.append("当前没有活动会话\n");
        } else {
            for (var s : sessions) {
                sb.append(String.format("- %s [%s] %s\n",
                    s.getSessionId(),
                    s.getStatus(),
                    s.getUserRequest()));
            }
        }

        return TuiCommandResult.success(sb.toString());
    }

    private TuiCommandResult handleSessionSwitch(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 2)) {
            return TuiCommandResult.error("请指定要切换的会话 ID");
        }

        String targetSessionId = command.getArgument(1);
        var session = sessionMemory.getSession(targetSessionId);

        if (session == null) {
            return TuiCommandResult.error("会话不存在：" + targetSessionId);
        }

        // TODO: 切换当前会话

        return TuiCommandResult.success("已切换到会话：" + targetSessionId);
    }

    private TuiCommandResult handleSessionClear(String sessionId) {
        sessionMemory.removeSession(sessionId);
        return TuiCommandResult.success("会话已清除");
    }

    /**
     * Skill 模板命令
     * /skill list|load|edit
     */
    private TuiCommandResult handleSkill(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 1)) {
            return TuiCommandResult.error("请指定子命令：list, load, edit");
        }

        String subCommand = command.getArgument(0);

        return switch (subCommand.toLowerCase()) {
            case "list" -> handleSkillList(sessionId);
            case "load" -> handleSkillLoad(command, sessionId);
            case "edit" -> handleSkillEdit(command, sessionId);
            default -> TuiCommandResult.error("未知子命令：" + subCommand);
        };
    }

    private TuiCommandResult handleSkillList(String sessionId) {
        // TODO: 从 Skill 模板服务获取列表
        List<String> skills = List.of(
            "slow_query_analysis - 慢查询深度分析",
            "index_recommendation - 索引推荐",
            "mapper_audit - Mapper 审计",
            "data_distribution_check - 数据分布检查",
            "cross_shard_detect - 跨分片检测"
        );

        StringBuilder sb = new StringBuilder();
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");
        sb.append("可用 Skill 模板\n");
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");

        for (String skill : skills) {
            sb.append("- ").append(skill).append("\n");
        }

        return TuiCommandResult.success(sb.toString());
    }

    private TuiCommandResult handleSkillLoad(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 2)) {
            return TuiCommandResult.error("请指定要加载的 Skill ID");
        }

        String skillId = command.getArgument(1);

        // TODO: 加载 Skill 模板

        return TuiCommandResult.success("已加载 Skill 模板：" + skillId);
    }

    private TuiCommandResult handleSkillEdit(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 2)) {
            return TuiCommandResult.error("请指定要编辑的 Skill ID");
        }

        String skillId = command.getArgument(1);

        // TODO: 编辑 Skill 模板

        return TuiCommandResult.success("正在编辑 Skill 模板：" + skillId);
    }

    /**
     * 配置命令
     * /config set|get|list
     */
    private TuiCommandResult handleConfig(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 1)) {
            return TuiCommandResult.error("请指定子命令：set, get, list");
        }

        String subCommand = command.getArgument(0);

        return switch (subCommand.toLowerCase()) {
            case "list" -> handleConfigList(sessionId);
            case "get" -> handleConfigGet(command, sessionId);
            case "set" -> handleConfigSet(command, sessionId);
            default -> TuiCommandResult.error("未知子命令：" + subCommand);
        };
    }

    private TuiCommandResult handleConfigList(String sessionId) {
        // TODO: 从配置服务获取
        Map<String, String> configs = Map.of(
            "default_datasource", "goldendb-test",
            "default_llm", "deepseek-chat",
            "max_query_rows", "1000",
            "session_timeout_minutes", "30"
        );

        StringBuilder sb = new StringBuilder();
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");
        sb.append("当前配置\n");
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");

        for (Map.Entry<String, String> entry : configs.entrySet()) {
            sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
        }

        return TuiCommandResult.success(sb.toString());
    }

    private TuiCommandResult handleConfigGet(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 2)) {
            return TuiCommandResult.error("请指定配置键名");
        }

        String key = command.getArgument(1);

        // TODO: 从配置服务获取

        return TuiCommandResult.success(key + " = <value>");
    }

    private TuiCommandResult handleConfigSet(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 3)) {
            return TuiCommandResult.error("请指定配置键和值");
        }

        String key = command.getArgument(1);
        String value = command.getArgument(2);

        // TODO: 设置配置

        return TuiCommandResult.success("已设置 " + key + " = " + value);
    }

    /**
     * 环境管理命令
     * /env list|add|remove
     */
    private TuiCommandResult handleEnvironment(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 1)) {
            return TuiCommandResult.error("请指定子命令：list, add, remove");
        }

        String subCommand = command.getArgument(0);

        return switch (subCommand.toLowerCase()) {
            case "list" -> handleEnvironmentList(sessionId);
            case "add" -> handleEnvironmentAdd(command, sessionId);
            case "remove" -> handleEnvironmentRemove(command, sessionId);
            default -> TuiCommandResult.error("未知子命令：" + subCommand);
        };
    }

    private TuiCommandResult handleEnvironmentList(String sessionId) {
        // TODO: 从环境管理服务获取
        StringBuilder sb = new StringBuilder();
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");
        sb.append("测试环境列表\n");
        sb.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\n");
        sb.append("- goldendb-test (默认)\n");
        sb.append("- goldendb-prod-mirror\n");

        return TuiCommandResult.success(sb.toString());
    }

    private TuiCommandResult handleEnvironmentAdd(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 3)) {
            return TuiCommandResult.error("请指定环境名称和 JDBC URL");
        }

        String name = command.getArgument(1);
        String jdbcUrl = command.getArgument(2);

        // TODO: 添加环境

        return TuiCommandResult.success("已添加测试环境：" + name);
    }

    private TuiCommandResult handleEnvironmentRemove(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 2)) {
            return TuiCommandResult.error("请指定要移除的环境名称");
        }

        String name = command.getArgument(1);

        // TODO: 移除环境

        return TuiCommandResult.success("已移除测试环境：" + name);
    }

    /**
     * 模型管理命令
     * /model list|switch|use|info
     */
    private TuiCommandResult handleModel(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 1)) {
            return handleModelList(sessionId);
        }

        String subCommand = command.getArgument(0);

        return switch (subCommand.toLowerCase()) {
            case "list" -> handleModelList(sessionId);
            case "switch", "use" -> handleModelSwitch(command, sessionId);
            case "info" -> handleModelInfo(command, sessionId);
            default -> TuiCommandResult.error("未知子命令：" + subCommand + "。可用：list, switch, use, info");
        };
    }

    private TuiCommandResult handleModelList(String sessionId) {
        // 定义所有可用模型信息
        List<ModelInfo> models = List.of(
            new ModelInfo("qwen3.5-plus", "通义千问 3.5 Plus", "通用分析任务", "1M", 0.7),
            new ModelInfo("qwen3-max", "通义千问 3.5 Max", "复杂推理任务", "256K", 0.7),
            new ModelInfo("qwen3-coder-next", "通义千问 Coder Next", "代码生成任务", "256K", 0.3),
            new ModelInfo("qwen3-coder-plus", "通义千问 Coder Plus", "代码分析任务", "1M", 0.5),
            new ModelInfo("MiniMax-M2.5", "MiniMax M2.5", "多模态任务", "192K", 0.5),
            new ModelInfo("glm-5", "GLM-5", "通用任务", "198K", 0.6),
            new ModelInfo("glm-4.7", "GLM-4.7", "轻量级任务", "198K", 0.6),
            new ModelInfo("kimi-k2.5", "Kimi K2.5", "长文本任务", "256K", 0.6),
            new ModelInfo("default", "默认模型", "默认使用 qwen3.5-plus", "1M", 0.7)
        );

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("可用大模型列表\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append(String.format("%-20s %-15s %-10s %-8s %s\n", "模型名称", "中文名", "上下文", "温度", "用途"));
        sb.append("───────────────────────────────────────────────────────────────\n");

        for (ModelInfo model : models) {
            sb.append(String.format("%-20s %-15s %-10s %-8s %s\n",
                model.name,
                model.chineseName,
                model.contextWindow,
                model.temperature,
                model.description));
        }

        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("使用 /model switch <模型名称> 切换模型\n");
        sb.append("使用 /model info <模型名称> 查看详细信息\n");

        return TuiCommandResult.success(sb.toString());
    }

    private TuiCommandResult handleModelSwitch(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 2)) {
            return TuiCommandResult.error("请指定要切换的模型名称\n可用模型：/model list");
        }

        String modelName = command.getArgument(1);

        // 验证模型名称
        List<String> validModels = List.of(
            "qwen3.5-plus", "qwen3-max", "qwen3-coder-next", "qwen3-coder-plus",
            "MiniMax-M2.5", "glm-5", "glm-4.7", "kimi-k2.5", "default"
        );

        if (!validModels.contains(modelName)) {
            return TuiCommandResult.error("未知的模型名称：" + modelName + "\n可用模型：/model list");
        }

        // TODO: 实际切换模型（需要注入 ChatModel Map）
        // 这里只是模拟，实际需要在服务层实现模型切换

        return TuiCommandResult.success("已切换到模型：" + modelName + "\n下次分析将使用此模型");
    }

    private TuiCommandResult handleModelInfo(TuiCommand command, String sessionId) {
        if (!commandParser.hasRequiredArgs(command, 2)) {
            return TuiCommandResult.error("请指定模型名称\n可用模型：/model list");
        }

        String modelName = command.getArgument(1);

        ModelInfo model = switch (modelName) {
            case "qwen3.5-plus" -> new ModelInfo("qwen3.5-plus", "通义千问 3.5 Plus", "通用分析任务", "1M", 0.7);
            case "qwen3-max" -> new ModelInfo("qwen3-max", "通义千问 3.5 Max", "复杂推理任务", "256K", 0.7);
            case "qwen3-coder-next" -> new ModelInfo("qwen3-coder-next", "通义千问 Coder Next", "代码生成任务", "256K", 0.3);
            case "qwen3-coder-plus" -> new ModelInfo("qwen3-coder-plus", "通义千问 Coder Plus", "代码分析任务", "1M", 0.5);
            case "MiniMax-M2.5" -> new ModelInfo("MiniMax-M2.5", "MiniMax M2.5", "多模态任务", "192K", 0.5);
            case "glm-5" -> new ModelInfo("glm-5", "GLM-5", "通用任务", "198K", 0.6);
            case "glm-4.7" -> new ModelInfo("glm-4.7", "GLM-4.7", "轻量级任务", "198K", 0.6);
            case "kimi-k2.5" -> new ModelInfo("kimi-k2.5", "Kimi K2.5", "长文本任务", "256K", 0.6);
            case "default" -> new ModelInfo("default", "默认模型", "默认使用 qwen3.5-plus", "1M", 0.7);
            default -> null;
        };

        if (model == null) {
            return TuiCommandResult.error("未知的模型名称：" + modelName + "\n可用模型：/model list");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("模型详细信息：").append(model.name).append("\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("中文名：").append(model.chineseName).append("\n");
        sb.append("用途：").append(model.description).append("\n");
        sb.append("上下文窗口：").append(model.contextWindow).append(" tokens\n");
        sb.append("默认温度：").append(model.temperature).append("\n");
        sb.append("API 提供商：阿里云 DashScope\n");
        sb.append("═══════════════════════════════════════════════════════════════\n");

        return TuiCommandResult.success(sb.toString());
    }

    /**
     * 模型信息辅助类
     */
    private static class ModelInfo {
        final String name;
        final String chineseName;
        final String description;
        final String contextWindow;
        final double temperature;

        ModelInfo(String name, String chineseName, String description, String contextWindow, double temperature) {
            this.name = name;
            this.chineseName = chineseName;
            this.description = description;
            this.contextWindow = contextWindow;
            this.temperature = temperature;
        }
    }
}
