package com.biz.sccba.sqlanalyzer.tui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TUI 命令解析器单元测试
 */
class TuiCommandParserTest {

    private TuiCommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new TuiCommandParser();
    }

    @Test
    @DisplayName("测试解析简单命令")
    void testParseSimpleCommand() {
        TuiCommand command = parser.parse("/help");

        assertNotNull(command);
        assertEquals("help", command.getName());
        assertEquals(0, command.getArguments().length);
    }

    @Test
    @DisplayName("测试解析带参数的命令")
    void testParseCommandWithArguments() {
        TuiCommand command = parser.parse("/analyze UserMapper.xml");

        assertNotNull(command);
        assertEquals("analyze", command.getName());
        assertEquals(1, command.getArguments().length);
        assertEquals("UserMapper.xml", command.getArgument(0));
    }

    @Test
    @DisplayName("测试解析带选项的命令")
    void testParseCommandWithOptions() {
        TuiCommand command = parser.parse("/analyze UserMapper.xml -D goldendb-test -M deepseek");

        assertNotNull(command);
        assertEquals("analyze", command.getName());
        assertEquals(1, command.getArguments().length);
        assertEquals("UserMapper.xml", command.getArgument(0));
        assertEquals("goldendb-test", command.getOption("D"));
        assertEquals("deepseek", command.getOption("M"));
    }

    @Test
    @DisplayName("测试解析带引号的选项值")
    void testParseCommandWithQuotedOptionValue() {
        TuiCommand command = parser.parse("/sql \"SELECT * FROM users\" -D test");

        assertNotNull(command);
        assertEquals("sql", command.getName());
        assertEquals("SELECT * FROM users", command.getArgument(0));
        assertEquals("test", command.getOption("D"));
    }

    @Test
    @DisplayName("测试非命令输入")
    void testParseNonCommandInput() {
        TuiCommand command = parser.parse("SELECT * FROM users");
        assertNull(command);
    }

    @Test
    @DisplayName("测试空输入")
    void testParseEmptyInput() {
        TuiCommand command = parser.parse("");
        assertNull(command);
    }

    @Test
    @DisplayName("测试 null 输入")
    void testParseNullInput() {
        TuiCommand command = parser.parse(null);
        assertNull(command);
    }

    @Test
    @DisplayName("测试命令别名")
    void testCommandAliases() {
        TuiCommand h = parser.parse("/h");
        assertEquals("h", h.getName());

        TuiCommand a = parser.parse("/a UserMapper.xml");
        assertEquals("a", a.getName());

        TuiCommand t = parser.parse("/t users");
        assertEquals("t", t.getName());
    }

    @Test
    @DisplayName("测试复杂命令解析")
    void testParseComplexCommand() {
        TuiCommand command = parser.parse("/test -D goldendb-prod --index --compare");

        assertNotNull(command);
        assertEquals("test", command.getName());
        assertEquals(0, command.getArguments().length);
        assertEquals("goldendb-prod", command.getOption("D"));
        assertTrue(command.getOptions().containsKey("index"));
        assertTrue(command.getOptions().containsKey("compare"));
    }

    @Test
    @DisplayName("测试验证必需参数")
    void testHasRequiredArgs() {
        TuiCommand command = parser.parse("/analyze UserMapper.xml -D test");

        assertTrue(parser.hasRequiredArgs(command, 1));
        assertFalse(parser.hasRequiredArgs(command, 2));
    }

    @Test
    @DisplayName("测试检查选项存在")
    void testHasOption() {
        TuiCommand command = parser.parse("/analyze UserMapper.xml -D test");

        assertTrue(parser.hasOption(command, "D"));
        assertFalse(parser.hasOption(command, "M"));
    }
}
