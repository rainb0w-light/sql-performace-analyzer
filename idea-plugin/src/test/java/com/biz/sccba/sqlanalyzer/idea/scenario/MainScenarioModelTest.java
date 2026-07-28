package com.biz.sccba.sqlanalyzer.idea.scenario;

import com.biz.sccba.sqlanalyzer.idea.contract.PluginApiDtos.*;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class MainScenarioModelTest {
    @Test
    public void ifChooseForeachNestedAndSharedParametersFollowUiSemantics() {
        SuggestionNode parent = node("if_parent", NodeKind.IF, "filter != null", "filter",
                "java.lang.Object", null, null, .9, true,
                new TypedValue(ValueType.OBJECT, "", List.of(), java.util.Map.of()));
        SuggestionNode child = node("if_child", NodeKind.IF, "status != null", "status",
                "java.lang.String", "if_parent", null, .9, true,
                TypedValue.scalar(ValueType.STRING, "ACTIVE"));
        SuggestionNode whenA = node("when_a", NodeKind.CHOOSE_WHEN, "kind == 'A'", "kind",
                "java.lang.String", null, "choose_1", .9, true,
                TypedValue.scalar(ValueType.STRING, "A"));
        SuggestionNode whenB = node("when_b", NodeKind.CHOOSE_WHEN, "kind == 'B'", "kind",
                "java.lang.String", null, "choose_1", .9, false,
                TypedValue.scalar(ValueType.STRING, "A"));
        SuggestionNode foreach = node("foreach_1", NodeKind.FOREACH, "ids != null", "ids",
                "java.util.List", null, null, .8, true,
                TypedValue.collection(List.of(TypedValue.scalar(ValueType.INTEGER, "1"))));
        MainScenarioModel model = new MainScenarioModel(
                new SuggestionSet("set_1", "ctx_1", List.of(parent, child, whenA, whenB, foreach)));

        assertTrue(model.requiresConfirmation());
        assertTrue(model.selected("if_child"));
        model.select("if_parent", false);
        assertFalse(model.selected("if_child"));
        assertFalse(model.enabled("if_child"));

        model.select("when_b", true);
        assertTrue(model.selected("when_b"));
        assertFalse(model.selected("when_a"));

        model.collectionMode("foreach_1", CollectionMode.EMPTY);
        assertEquals(CollectionMode.EMPTY, model.collectionMode("foreach_1"));
        assertFalse(model.selected("foreach_1"));
        model.collectionMode("foreach_1", CollectionMode.MULTIPLE);
        assertTrue(model.selected("foreach_1"));

        assertEquals("shared choose parameter is represented once", 1,
                model.snapshot().parameters().keySet().stream().filter("kind"::equals).count());
    }

    @Test
    public void lowConfidenceSpaceFallbackIsVisibleAndNotSelected() {
        SuggestionNode keyword = node("if_keyword", NodeKind.IF, "keyword != null", "keyword",
                "java.lang.String", null, null, .2, true, null);
        MainScenarioModel model = new MainScenarioModel(
                new SuggestionSet("set", "ctx", List.of(keyword,
                        node("if_status", NodeKind.IF, "status != null", "status",
                                "java.lang.String", null, null, .9, true,
                                TypedValue.scalar(ValueType.STRING, "ACTIVE")))));

        assertFalse(model.selected("if_keyword"));
        assertEquals(" ", model.parameter("keyword").value());
        assertEquals("␠ 1个空格", MainScenarioModel.displayValue(keyword, model.parameter("keyword")));
        assertEquals(ConditionCategory.FILTER, model.nodes().get(0).category());
        assertEquals(CategorySource.STRUCTURE_FALLBACK, model.nodes().get(0).categorySource());
    }

    @Test
    public void conflictingSharedParameterTypesBlockConfirmation() {
        MainScenarioModel model = new MainScenarioModel(new SuggestionSet("set", "ctx", List.of(
                node("n1", NodeKind.IF, "value != null", "value", "java.lang.String",
                        null, null, .9, true, null),
                node("n2", NodeKind.IF, "value > 0", "value", "java.lang.Integer",
                        null, null, .9, true, null))));
        assertFalse(model.valid());
        assertTrue(model.conflicts().get(0).contains("value"));
        assertTrue(model.conflicts().get(0).contains("n2"));
    }

    private static SuggestionNode node(String id, NodeKind kind, String expression, String path,
                                       String type, String parent, String choose, double confidence,
                                       boolean enabled, TypedValue value) {
        return new SuggestionNode(id, kind, expression, path, type, parent, choose,
                null, null, true, enabled, value,
                value == null ? "TYPE_FALLBACK" : "PROFILE_SNAPSHOT",
                value == null ? "" : "snap_1", value == null ? "" : "locator",
                confidence, value == null ? "无业务证据" : "画像 Top-K");
    }
}
