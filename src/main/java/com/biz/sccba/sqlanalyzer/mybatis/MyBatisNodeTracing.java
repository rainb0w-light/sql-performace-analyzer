package com.biz.sccba.sqlanalyzer.mybatis;

import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog.NodeInfo;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.scripting.xmltags.ChooseSqlNode;
import org.apache.ibatis.scripting.xmltags.DynamicContext;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.ForEachSqlNode;
import org.apache.ibatis.scripting.xmltags.IfSqlNode;
import org.apache.ibatis.scripting.xmltags.MixedSqlNode;
import org.apache.ibatis.scripting.xmltags.SetSqlNode;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.apache.ibatis.scripting.xmltags.TrimSqlNode;
import org.apache.ibatis.scripting.xmltags.VarDeclSqlNode;
import org.apache.ibatis.scripting.xmltags.WhereSqlNode;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Request-local instrumentation of MyBatis' official SqlNode graph.
 *
 * <p>Condition evaluation remains entirely inside the original MyBatis nodes. Wrappers only
 * observe whether a node appended SQL to DynamicContext.</p>
 */
public final class MyBatisNodeTracing {

    private MyBatisNodeTracing() {
    }

    public static Set<String> instrument(Configuration configuration, String statementId,
                                         List<NodeInfo> catalogNodes) {
        try {
            MappedStatement statement = configuration.getMappedStatement(statementId);
            if (!(statement.getSqlSource() instanceof DynamicSqlSource source)) {
                return Set.of();
            }
            Set<String> hits = new LinkedHashSet<>();
            Cursor cursor = new Cursor(catalogNodes);
            Field root = field(DynamicSqlSource.class, "rootSqlNode");
            SqlNode rootNode = (SqlNode) root.get(source);
            instrumentChildren(rootNode, cursor, hits, false);
            if (cursor.hasNext()) {
                throw unsupported("动态节点目录与 MyBatis SqlNode 图不一致");
            }
            return hits;
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw unsupported("当前 MyBatis 版本不支持节点追踪：" + rootMessage(e));
        }
    }

    private static void instrumentChildren(SqlNode node, Cursor cursor, Set<String> hits,
                                           boolean chooseArm)
            throws ReflectiveOperationException {
        if (node instanceof MixedSqlNode mixed) {
            List<SqlNode> children = listField(mixed, MixedSqlNode.class, "contents");
            for (int i = 0; i < children.size(); i++) {
                int childIndex = i;
                SqlNode child = children.get(i);
                instrumentPoint(child, replacement -> children.set(childIndex, replacement),
                        cursor, hits, chooseArm);
            }
            return;
        }
        if (node instanceof IfSqlNode ifNode) {
            Field contents = field(IfSqlNode.class, "contents");
            SqlNode child = (SqlNode) contents.get(ifNode);
            instrumentChildren(child, cursor, hits, false);
            return;
        }
        if (node instanceof ChooseSqlNode choose) {
            List<SqlNode> arms = listField(choose, ChooseSqlNode.class, "ifSqlNodes");
            for (int i = 0; i < arms.size(); i++) {
                int armIndex = i;
                SqlNode arm = arms.get(i);
                instrumentPoint(arm, replacement -> arms.set(armIndex, replacement),
                        cursor, hits, true);
            }
            Field defaultField = field(ChooseSqlNode.class, "defaultSqlNode");
            SqlNode otherwise = (SqlNode) defaultField.get(choose);
            if (otherwise != null) {
                NodeInfo info = cursor.require("otherwise");
                instrumentChildren(otherwise, cursor, hits, false);
                defaultField.set(choose, new TrackingSqlNode(otherwise, info.nodeId(), hits));
            }
            return;
        }
        if (node instanceof ForEachSqlNode foreach) {
            Field contents = field(ForEachSqlNode.class, "contents");
            instrumentChildren((SqlNode) contents.get(foreach), cursor, hits, false);
            return;
        }
        if (node instanceof TrimSqlNode trim) {
            Field contents = field(TrimSqlNode.class, "contents");
            instrumentChildren((SqlNode) contents.get(trim), cursor, hits, false);
        }
    }

    private static void instrumentPoint(SqlNode node, Replacer replacer, Cursor cursor,
                                        Set<String> hits, boolean chooseArm)
            throws ReflectiveOperationException {
        String type = type(node, chooseArm);
        if (type == null) {
            instrumentChildren(node, cursor, hits, chooseArm);
            return;
        }
        NodeInfo info = cursor.require(type);
        instrumentChildren(node, cursor, hits, "choose".equals(type));
        replacer.replace(new TrackingSqlNode(node, info.nodeId(), hits));
    }

    private static String type(SqlNode node, boolean chooseArm) {
        if (node instanceof ChooseSqlNode) {
            return "choose";
        }
        if (node instanceof IfSqlNode) {
            return chooseArm ? "when" : "if";
        }
        if (node instanceof ForEachSqlNode) {
            return "foreach";
        }
        if (node instanceof WhereSqlNode) {
            return "where";
        }
        if (node instanceof SetSqlNode) {
            return "set";
        }
        if (node instanceof TrimSqlNode) {
            return "trim";
        }
        if (node instanceof VarDeclSqlNode) {
            return "bind";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<SqlNode> listField(Object target, Class<?> owner, String name)
            throws ReflectiveOperationException {
        return (List<SqlNode>) field(owner, name).get(target);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static UnsupportedOperationException unsupported(String detail) {
        return new UnsupportedOperationException("UNSUPPORTED_NODE_TRACE: " + detail);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private interface Replacer {
        void replace(SqlNode replacement) throws ReflectiveOperationException;
    }

    private record TrackingSqlNode(SqlNode delegate, String nodeId, Set<String> hits)
            implements SqlNode {
        @Override
        public boolean apply(DynamicContext context) {
            String before = context.getSql();
            boolean result = delegate.apply(context);
            if (result || !context.getSql().equals(before)) {
                hits.add(nodeId);
            }
            return result;
        }
    }

    private static final class Cursor {
        private final List<NodeInfo> nodes;
        private int index;

        private Cursor(List<NodeInfo> nodes) {
            this.nodes = nodes == null ? List.of() : new ArrayList<>(nodes);
        }

        private NodeInfo require(String type) {
            if (!hasNext()) {
                throw unsupported("缺少 " + type + " 节点");
            }
            NodeInfo next = nodes.get(index++);
            if (!next.type().toLowerCase(Locale.ROOT).equals(type)) {
                throw unsupported("期望 " + type + "，实际为 " + next.type());
            }
            return next;
        }

        private boolean hasNext() {
            return index < nodes.size();
        }
    }
}
