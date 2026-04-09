package com.biz.sccba.sqlanalyzer.tui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TUI 模型切换命令测试
 */
@DisplayName("TUI 模型切换命令测试")
public class ModelSwitchCommandTest {

    private final TuiCommandParser parser = new TuiCommandParser();

    @Test
    @DisplayName("解析/model list 命令")
    public void testModelListCommand() {
        TuiCommand command = parser.parse("/model list");

        assertNotNull(command);
        assertEquals("model", command.getName());
        assertEquals("list", command.getSubcommand());
        assertTrue(command.getArgs().isEmpty());
    }

    @Test
    @DisplayName("解析/model switch 命令")
    public void testModelSwitchCommand() {
        TuiCommand command = parser.parse("/model switch qwen3.5-plus");

        assertNotNull(command);
        assertEquals("model", command.getName());
        assertEquals("switch", command.getSubcommand());
        assertEquals(1, command.getArgs().size());
        assertEquals("qwen3.5-plus", command.getArgs().get(0));
    }

    @Test
    @DisplayName("解析/model switch 不带模型名")
    public void testModelSwitchWithoutModelName() {
        TuiCommand command = parser.parse("/model switch");

        assertNotNull(command);
        assertEquals("model", command.getName());
        assertEquals("switch", command.getSubcommand());
        assertTrue(command.getArgs().isEmpty());
    }

    @Test
    @DisplayName("解析无效的 model 子命令")
    public void testInvalidModelSubcommand() {
        TuiCommand command = parser.parse("/model invalid");

        assertNotNull(command);
        assertEquals("model", command.getName());
        assertEquals("invalid", command.getSubcommand());
    }

    @Test
    @DisplayName("解析/model help 命令")
    public void testModelHelpCommand() {
        TuiCommand command = parser.parse("/model help");

        assertNotNull(command);
        assertEquals("model", command.getName());
        assertEquals("help", command.getSubcommand());
    }

    @Test
    @DisplayName("解析/model use 命令（别名）")
    public void testModelUseCommand() {
        TuiCommand command = parser.parse("/model use glm-5");

        assertNotNull(command);
        assertEquals("model", command.getName());
        assertEquals("use", command.getSubcommand());
        assertEquals(1, command.getArgs().size());
        assertEquals("glm-5", command.getArgs().get(0));
    }
}
