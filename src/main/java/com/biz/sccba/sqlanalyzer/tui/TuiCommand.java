package com.biz.sccba.sqlanalyzer.tui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * TUI 命令模型
 * 解析后的命令对象
 */
public class TuiCommand {

    /**
     * 命令名称（不含前缀 /）
     */
    private final String name;

    /**
     * 命令参数
     */
    private final String[] arguments;

    /**
     * 命令选项（-key value 格式）
     */
    private final Map<String, String> options;

    /**
     * 命令原始输入
     */
    private final String rawInput;

    public TuiCommand(String name, String[] arguments, Map<String, String> options, String rawInput) {
        this.name = name;
        this.arguments = arguments != null ? arguments : new String[0];
        this.options = options != null ? options : new HashMap<>();
        this.rawInput = rawInput;
    }

    public String getName() {
        return name;
    }

    public String[] getArguments() {
        return arguments;
    }

    public String getArgument(int index) {
        if (index >= 0 && index < arguments.length) {
            return arguments[index];
        }
        return null;
    }

    /**
     * Get the first positional argument as subcommand
     * e.g., for "/model list", returns "list"
     */
    public String getSubcommand() {
        return getArgument(0);
    }

    /**
     * Get all positional arguments except the first one (subcommand)
     * e.g., for "/model switch qwen3.5-plus", returns ["qwen3.5-plus"]
     * for "/model list", returns empty list
     */
    public java.util.List<String> getArgs() {
        if (arguments.length <= 1) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.asList(java.util.Arrays.copyOfRange(arguments, 1, arguments.length));
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public String getOption(String key) {
        return options.get(key);
    }

    public String getOption(String key, String defaultValue) {
        return options.getOrDefault(key, defaultValue);
    }

    public String getRawInput() {
        return rawInput;
    }

    @Override
    public String toString() {
        return "TuiCommand{" +
               "name='" + name + '\'' +
               ", arguments=" + String.join(", ", arguments) +
               ", options=" + options +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TuiCommand that = (TuiCommand) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
