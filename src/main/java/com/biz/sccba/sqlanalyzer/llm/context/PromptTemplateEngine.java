package com.biz.sccba.sqlanalyzer.llm.context;

import com.biz.sccba.sqlanalyzer.error.AgentErrorCode;
import com.biz.sccba.sqlanalyzer.error.AgentException;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 轻量级模板渲染器，强校验占位符，避免遗漏或注入。
 */
@Component
public class PromptTemplateEngine {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    /**
     * 渲染模板，要求模板中的占位符在上下文中全部存在。
     */
    public String render(String template, Map<String, String> context) throws AgentException {
        if (template == null) {
            throw new AgentException(AgentErrorCode.TEMPLATE_RENDER_FAILED, "模板内容为空");
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        Set<String> providedKeys = context.keySet();

        while (matcher.find()) {
            String key = matcher.group(1);
            if (!context.containsKey(key)) {
                throw new AgentException(AgentErrorCode.TEMPLATE_RENDER_FAILED,
                        "缺失模板占位符: " + key);
            }
            String value = context.getOrDefault(key, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);

        // 二次校验：模板中未出现但上下文提供的 key 可忽略；但出现占位符必须被替换。
        return sb.toString();
    }
}

