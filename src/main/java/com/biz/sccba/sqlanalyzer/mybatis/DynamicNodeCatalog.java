package com.biz.sccba.sqlanalyzer.mybatis;

import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structural catalog of a statement's dynamic nodes (development-guide §6.2): tag positions,
 * {@code test} expressions and parent/child relations, used by the Scenario Planner and IDEA UI
 * for coverage targets. Evaluation of conditions is ALWAYS delegated to MyBatis OGNL/SqlNode —
 * this class never decides which branch runs.
 */
@Component
public class DynamicNodeCatalog {

    private static final Set<String> DYNAMIC_TAGS =
            Set.of("if", "choose", "when", "otherwise", "where", "trim", "set", "foreach", "bind");
    private static final Pattern TEST_IDENTIFIER = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_.]*)\\s*(==|!=|<=|>=|<|>|\\bne\\b|\\beq\\b|\\blt\\b|\\bgt\\b|\\ble\\b|\\bge\\b|\\binstanceof\\b|\\bcontains\\b|\\bstartsWith\\b|\\bendsWith\\b|\\bmatches\\b)");
    private static final Pattern DOLLAR_INTERPOLATION = Pattern.compile("\\$\\{([^}]+)}");

    public record NodeInfo(String nodeId, String type, String test, String parentNodeId,
                           List<String> childrenIds, String xpath, Map<String, String> attributes,
                           List<String> referencedNames) {}

    public record StatementStructure(String namespace, String statementId, String statementType,
                                     String rawDynamicSql, List<NodeInfo> nodes, List<String> dollarExpressions) {}

    public record MapperStructure(String namespace, List<StatementStructure> statements) {}

    public MapperStructure scan(String mapperXml) {
        String xml = mapperXml.replaceAll("<!DOCTYPE[^>]*>", "");
        XPathParser parser = new XPathParser(xml);
        XNode mapperNode = parser.evalNode("/mapper");
        if (mapperNode == null) throw new IllegalArgumentException("无效的 MyBatis XML，未找到 mapper 节点");
        String namespace = mapperNode.getStringAttribute("namespace");
        List<StatementStructure> structures = new ArrayList<>();
        for (String tag : List.of("select", "insert", "update", "delete")) {
            for (XNode node : mapperNode.evalNodes(tag)) {
                structures.add(scanStatement(namespace, node, tag.toUpperCase(java.util.Locale.ROOT)));
            }
        }
        return new MapperStructure(namespace, structures);
    }

    public StatementStructure scanStatement(String namespace, XNode statementNode, String statementType) {
        String statementId = statementNode.getStringAttribute("id");
        List<NodeInfo> nodes = new ArrayList<>();
        List<String> dollar = new ArrayList<>();
        collect(statementNode, statementId, null, "", nodes, dollar);
        return new StatementStructure(namespace, statementId, statementType,
                rawText(statementNode), nodes, dollar);
    }

    private void collect(XNode node, String statementId, String parentId, String indexedPath,
                         List<NodeInfo> out, List<String> dollar) {
        String name = node.getName();
        if (DYNAMIC_TAGS.contains(name)) {
            String nodeId = statementId + "#" + indexedPath;
            String test = node.getStringAttribute("test");
            Map<String, String> attrs = new LinkedHashMap<>();
            for (String key : List.of("test", "collection", "item", "index", "open", "close", "separator",
                    "name", "value", "prefix", "suffix", "prefixOverrides", "suffixOverrides")) {
                String value = node.getStringAttribute(key);
                if (value != null) attrs.put(key, value);
            }
            List<String> referenced = test == null ? List.of() : referencedNames(test);
            NodeInfo info = new NodeInfo(nodeId, name, test, parentId, new ArrayList<>(), indexedPath, attrs, referenced);
            out.add(info);
            if (parentId != null) {
                out.stream().filter(n -> n.nodeId().equals(parentId)).findFirst()
                        .ifPresent(parent -> parent.childrenIds().add(nodeId));
            }
            forEachChild(node, indexedPath, (child, childPath) ->
                    collect(child, statementId, nodeId, childPath, out, dollar));
        } else {
            scanDollar(node, dollar);
            forEachChild(node, indexedPath, (child, childPath) ->
                    collect(child, statementId, parentId, childPath, out, dollar));
        }
    }

    /** Iterates element children with sibling indices so node ids never collide between siblings. */
    private void forEachChild(XNode node, String parentPath, java.util.function.BiConsumer<XNode, String> consumer) {
        Map<String, Integer> counters = new LinkedHashMap<>();
        for (XNode child : node.getChildren()) {
            String childName = child.getName();
            int index = counters.getOrDefault(childName, 0);
            counters.put(childName, index + 1);
            String segment = childName + "[" + index + "]";
            String childPath = parentPath.isEmpty() ? segment : parentPath + "/" + segment;
            consumer.accept(child, childPath);
        }
    }

    private void scanDollar(XNode node, List<String> dollar) {
        String body = node.getStringBody("");
        if (body != null) {
            Matcher m = DOLLAR_INTERPOLATION.matcher(body);
            while (m.find()) dollar.add(m.group(1).trim());
        }
    }

    /** Left-hand identifiers referenced by a test expression (structural, for planning targets). */
    public static List<String> referencedNames(String testExpression) {
        List<String> names = new ArrayList<>();
        Matcher m = TEST_IDENTIFIER.matcher(testExpression == null ? "" : testExpression);
        while (m.find()) {
            String name = m.group(1);
            if (!Set.of("null", "true", "false", "instanceof").contains(name) && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** Extracts the raw dynamic SQL text with tags preserved (no evaluation, for display/audit). */
    private static String rawText(XNode statementNode) {
        try {
            var node = statementNode.getNode();
            var transform = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            transform.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            var writer = new java.io.StringWriter();
            transform.transform(new javax.xml.transform.dom.DOMSource(node), new javax.xml.transform.stream.StreamResult(writer));
            String full = writer.toString();
            int gt = full.indexOf('>');
            int lt = full.lastIndexOf('<');
            if (gt > 0 && lt > gt) return full.substring(gt + 1, lt).trim();
            return full.trim();
        } catch (Exception e) {
            return "";
        }
    }
}
