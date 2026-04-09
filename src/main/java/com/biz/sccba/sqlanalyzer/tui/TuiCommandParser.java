package com.biz.sccba.sqlanalyzer.tui;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TUI 命令解析器
 * 解析 /command 格式的命令
 *
 * 支持的命令格式：
 * /command [arg1] [arg2] [-option1 value1] [-option2 value2] [--flag]
 */
@Component
public class TuiCommandParser {

    /**
     * 解析命令行输入
     *
     * @param input 用户输入，如 "/analyze mapper.xml -D datasource=test"
     * @return 解析后的命令对象
     */
    public TuiCommand parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String trimmed = input.trim();

        // 检查是否是命令（以 / 开头）
        if (!trimmed.startsWith("/")) {
            return null;
        }

        // 移除 / 前缀
        String commandPart = trimmed.substring(1);

        // 分割命令名和参数
        int firstSpace = commandPart.indexOf(' ');
        String commandName;
        String argsPart;

        if (firstSpace == -1) {
            // 没有参数
            commandName = commandPart.trim();
            argsPart = "";
        } else {
            commandName = commandPart.substring(0, firstSpace).trim();
            argsPart = commandPart.substring(firstSpace).trim();
        }

        // 解析参数和选项
        List<String> argList = new ArrayList<>();
        Map<String, String> options = new HashMap<>();
        parseArgsAndOptions(argsPart, argList, options);

        return new TuiCommand(commandName, argList.toArray(new String[0]), options, input);
    }

    /**
     * 同时解析参数和选项
     * 支持格式：
     * - 普通参数：arg1 arg2
     * - 短选项：-D value
     * - 短选项带引号：-D "value with spaces"
     * - 长选项（boolean flag）：--index --compare
     */
    private void parseArgsAndOptions(String argsPart, List<String> argList, Map<String, String> options) {
        if (argsPart == null || argsPart.trim().isEmpty()) {
            return;
        }

        String[] tokens = tokenize(argsPart);

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];

            if (token.startsWith("--")) {
                // 长选项（boolean flag），如 --index
                String key = token.substring(2);
                if (!key.isEmpty()) {
                    options.put(key, "");  // 空值表示 flag
                }
            } else if (token.startsWith("-")) {
                // 短选项，如 -D value
                String key = token.substring(1);
                if (!key.isEmpty() && i + 1 < tokens.length) {
                    options.put(key, tokens[i + 1]);
                    i++;  // 跳过值
                } else if (!key.isEmpty()) {
                    // 没有值的短选项，视为 flag
                    options.put(key, "");
                }
            } else {
                // 普通参数
                argList.add(token);
            }
        }
    }

    /**
     * 将字符串分割成 tokens，支持引号
     * 例如：UserMapper.xml -D "goldendb test" => ["UserMapper.xml", "-D", "goldendb test"]
     */
    private String[] tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens.toArray(new String[0]);
    }

    /**
     * 验证命令是否有必需的参数
     */
    public boolean hasRequiredArgs(TuiCommand command, int requiredCount) {
        return command != null && command.getArguments().length >= requiredCount;
    }

    /**
     * 检查命令是否包含指定选项
     */
    public boolean hasOption(TuiCommand command, String optionKey) {
        return command != null && command.getOptions().containsKey(optionKey);
    }
}
