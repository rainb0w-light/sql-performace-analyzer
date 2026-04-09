package com.biz.sccba.sqlanalyzer.tui;

import com.biz.sccba.sqlanalyzer.agent.SQLAnalysisOrchestrator;
import com.biz.sccba.sqlanalyzer.memory.SessionMemoryService;
import com.biz.sccba.sqlanalyzer.model.agent.AnalysisResult;
import com.biz.sccba.sqlanalyzer.service.TestEnvironmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TUI 命令处理器单元测试
 */
class TuiCommandHandlerTest {

    @Mock
    private SessionMemoryService sessionMemory;

    @Mock
    private SQLAnalysisOrchestrator orchestrator;

    @Mock
    private TestEnvironmentService testEnvironmentService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private AnalysisResult analysisResult;

    private TuiCommandHandler handler;
    private TuiCommandParser parser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        parser = new TuiCommandParser();
        handler = new TuiCommandHandler(parser, sessionMemory, orchestrator, testEnvironmentService, messagingTemplate);
    }

    @Test
    @DisplayName("测试帮助命令")
    void testHandleHelp() {
        TuiCommandResult result = handler.handleCommand("/help", "session-1");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("GoldenDB SQL Analyzer"));
        assertTrue(result.getMessage().contains("/analyze"));
        assertTrue(result.getMessage().contains("/sql"));
    }

    @Test
    @DisplayName("测试帮助命令别名")
    void testHandleHelpAlias() {
        TuiCommandResult result = handler.handleCommand("/h", "session-1");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("GoldenDB SQL Analyzer"));
    }

    @Test
    @DisplayName("测试分析命令 - 缺少参数")
    void testHandleAnalyzeMissingArgument() {
        TuiCommandResult result = handler.handleCommand("/analyze", "session-1");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("请指定要分析的 Mapper 文件路径"));
    }

    @Test
    @DisplayName("测试 SQL 命令 - 缺少参数")
    void testHandleSqlMissingArgument() {
        TuiCommandResult result = handler.handleCommand("/sql", "session-1");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("请指定要分析的 SQL 语句"));
    }

    @Test
    @DisplayName("测试表分析命令 - 缺少参数")
    void testHandleTableMissingArgument() {
        TuiCommandResult result = handler.handleCommand("/table", "session-1");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("请指定要分析的表名"));
    }

    @Test
    @DisplayName("测试会话列表命令")
    void testHandleSessionList() {
        when(sessionMemory.getActiveSessions()).thenReturn(java.util.List.of());

        TuiCommandResult result = handler.handleCommand("/session list", "session-1");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("活动会话列表"));
    }

    @Test
    @DisplayName("测试会话清除命令")
    void testHandleSessionClear() {
        doNothing().when(sessionMemory).removeSession("session-1");

        TuiCommandResult result = handler.handleCommand("/session clear", "session-1");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("会话已清除"));
        verify(sessionMemory).removeSession("session-1");
    }

    @Test
    @DisplayName("测试会话切换命令 - 缺少参数")
    void testHandleSessionSwitchMissingArgument() {
        TuiCommandResult result = handler.handleCommand("/session switch", "session-1");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("请指定要切换的会话 ID"));
    }

    @Test
    @DisplayName("测试会话切换命令 - 会话不存在")
    void testHandleSessionSwitchNotFound() {
        when(sessionMemory.getSession("non-existent")).thenReturn(null);

        TuiCommandResult result = handler.handleCommand("/session switch non-existent", "session-1");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("会话不存在"));
    }

    @Test
    @DisplayName("测试 Skill 列表命令")
    void testHandleSkillList() {
        TuiCommandResult result = handler.handleCommand("/skill list", "session-1");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("可用 Skill 模板"));
        assertTrue(result.getMessage().contains("slow_query_analysis"));
    }

    @Test
    @DisplayName("测试配置列表命令")
    void testHandleConfigList() {
        TuiCommandResult result = handler.handleCommand("/config list", "session-1");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("当前配置"));
    }

    @Test
    @DisplayName("测试环境列表命令")
    void testHandleEnvironmentList() {
        TuiCommandResult result = handler.handleCommand("/env list", "session-1");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("测试环境列表"));
    }

    @Test
    @DisplayName("测试未知命令")
    void testHandleUnknownCommand() {
        TuiCommandResult result = handler.handleCommand("/unknown", "session-1");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未知命令"));
    }

    @Test
    @DisplayName("测试提交命令 - 缺少参数")
    void testHandleCommitMissingArgument() {
        TuiCommandResult result = handler.handleCommand("/commit", "session-1");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("请指定 DDL 变更 ID"));
    }

    @Test
    @DisplayName("测试测试命令")
    void testHandleTest() {
        TuiCommandResult result = handler.handleCommand("/test -D goldendb-test --index --compare", "session-1");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("执行测试"));
    }
}
