package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.service.ArtifactPipelineService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Conservative extractor for statically evaluable MyBatis statement annotations.
 *
 * <p>This is deliberately not a Java compiler. Only literal strings, literal concatenation and
 * literal arrays are accepted; every other Java expression is rejected as UNSUPPORTED.</p>
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class StaticAnnotationMapperService {

    private static final Pattern ANNOTATION =
            Pattern.compile("@(?:org\\.apache\\.ibatis\\.annotations\\.)?(Select|Insert|Update|Delete)\\s*\\(");

    private final ArtifactPipelineService pipeline;

    public StaticAnnotationMapperService(ArtifactPipelineService pipeline) {
        this.pipeline = pipeline;
    }

    public ArtifactPipelineService.IndexedArtifact index(
            String clientId, String sessionId, String javaContent,
            String namespace, String methodName) {
        if (javaContent == null || javaContent.isBlank()) {
            throw unsupported("Java 内容为空");
        }
        if (namespace == null || namespace.isBlank() || methodName == null || methodName.isBlank()) {
            throw unsupported("namespace 和 methodName 必填");
        }
        List<Match> matches = matchingAnnotations(javaContent, methodName);
        if (matches.size() != 1) {
            throw unsupported("目标方法必须唯一且仅包含一个受支持的 MyBatis SQL 注解");
        }
        Match match = matches.get(0);
        String sql = parseLiteralExpression(match.expression());
        String body = scriptBody(sql);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
                + "\"https://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
                + "<mapper namespace=\"" + escapeAttribute(namespace) + "\">\n"
                + "  <" + match.kind().toLowerCase(Locale.ROOT)
                + " id=\"" + escapeAttribute(methodName) + "\">\n"
                + body + "\n"
                + "  </" + match.kind().toLowerCase(Locale.ROOT) + ">\n"
                + "</mapper>";
        return pipeline.ingestMyBatisMapper(clientId, sessionId, xml, namespace,
                "MYBATIS_ANNOTATION_MAPPER",
                "{\"sourceContentHash\":\"" + sha256(javaContent) + "\"}");
    }

    private static List<Match> matchingAnnotations(String source, String methodName) {
        List<Match> matches = new ArrayList<>();
        Matcher matcher = ANNOTATION.matcher(source);
        while (matcher.find()) {
            int close = matchingParen(source, matcher.end() - 1);
            if (close < 0) {
                throw unsupported("SQL 注解括号不完整");
            }
            int signatureEnd = Math.min(source.length(), close + 1000);
            String signature = source.substring(close + 1, signatureEnd);
            Matcher method = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\(")
                    .matcher(signature);
            if (method.find()) {
                matches.add(new Match(matcher.group(1).toUpperCase(Locale.ROOT),
                        source.substring(matcher.end(), close)));
            }
        }
        return matches;
    }

    private static int matchingParen(String source, int open) {
        int depth = 0;
        boolean string = false;
        boolean textBlock = false;
        boolean escaped = false;
        for (int i = open; i < source.length(); i++) {
            if (!string && source.startsWith("\"\"\"", i)) {
                textBlock = !textBlock;
                i += 2;
                continue;
            }
            if (textBlock) {
                continue;
            }
            char c = source.charAt(i);
            if (string) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    string = false;
                }
                continue;
            }
            if (c == '"') {
                string = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    static String parseLiteralExpression(String expression) {
        String value = expression == null ? "" : expression.trim();
        if (value.startsWith("value")) {
            int equals = value.indexOf('=');
            if (equals > 0) {
                value = value.substring(equals + 1).trim();
            }
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            value = value.substring(1, value.length() - 1);
        }
        List<String> parts = new ArrayList<>();
        int index = 0;
        while (index < value.length()) {
            index = skipSeparators(value, index);
            if (index >= value.length()) {
                break;
            }
            Literal literal = literal(value, index);
            if (literal == null) {
                throw unsupported("SQL 注解包含非静态字符串表达式");
            }
            parts.add(literal.value());
            index = literal.end();
            int next = skipWhitespace(value, index);
            if (next < value.length() && value.charAt(next) != '+'
                    && value.charAt(next) != ',') {
                throw unsupported("SQL 注解包含非静态字符串表达式");
            }
            index = next;
        }
        if (parts.isEmpty()) {
            throw unsupported("SQL 注解没有静态 SQL 内容");
        }
        return String.join(" ", parts);
    }

    private static Literal literal(String value, int start) {
        if (value.startsWith("\"\"\"", start)) {
            int end = value.indexOf("\"\"\"", start + 3);
            if (end < 0) {
                return null;
            }
            String text = value.substring(start + 3, end);
            if (text.startsWith("\n")) {
                text = text.substring(1);
            }
            return new Literal(text.stripIndent().strip(), end + 3);
        }
        if (value.charAt(start) != '"') {
            return null;
        }
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                out.append(switch (c) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> c;
                });
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return new Literal(out.toString(), i + 1);
            } else {
                out.append(c);
            }
        }
        return null;
    }

    private static int skipSeparators(String value, int index) {
        int i = skipWhitespace(value, index);
        while (i < value.length() && (value.charAt(i) == '+' || value.charAt(i) == ',')) {
            i = skipWhitespace(value, i + 1);
        }
        return i;
    }

    private static int skipWhitespace(String value, int index) {
        int i = index;
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String scriptBody(String sql) {
        String trimmed = sql.trim();
        if (trimmed.startsWith("<script>") && trimmed.endsWith("</script>")) {
            return trimmed.substring("<script>".length(),
                    trimmed.length() - "</script>".length()).trim();
        }
        return escapeText(trimmed);
    }

    private static String escapeAttribute(String value) {
        return escapeText(value).replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String escapeText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static IllegalArgumentException unsupported(String detail) {
        return new IllegalArgumentException("UNSUPPORTED: " + detail);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算 Java Mapper hash", e);
        }
    }

    private record Match(String kind, String expression) {
    }

    private record Literal(String value, int end) {
    }
}
