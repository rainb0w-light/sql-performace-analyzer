package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.analysis.ScenarioContextResolver;
import com.biz.sccba.sqlanalyzer.analysis.StatementReferenceResolver;
import com.biz.sccba.sqlanalyzer.domain.profiling.Profiling.ColumnStat;
import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog.NodeInfo;
import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog.StatementStructure;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.CategorySource;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.ConditionCategory;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.NodeKind;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginApiModels.ValueType;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamInfo;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamKind;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ShardInfo;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PluginMapperPreparationServiceTest {

    @Test
    void projectsChooseForeachNestingProvenanceFallbackAndTypeConflicts() {
        var service = new PluginMapperPreparationService(mock(ArtifactService.class),
                mock(com.biz.sccba.sqlanalyzer.repository.ArtifactRepository.class),
                mock(com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog.class),
                mock(StatementReferenceResolver.class), mock(ScenarioContextResolver.class),
                new ObjectMapper());
        List<NodeInfo> nodes = List.of(
                node("s#choose[0]", "choose", null, null,
                        List.of("s#choose[0]/when[0]", "s#choose[0]/otherwise[0]"), List.of(), Map.of()),
                node("s#choose[0]/when[0]", "when", "memberId != null", "s#choose[0]",
                        List.of(), List.of("memberId"), Map.of("test", "memberId != null")),
                node("s#choose[0]/otherwise[0]", "otherwise", null, "s#choose[0]",
                        List.of(), List.of(), Map.of()),
                node("s#if[0]", "if", "status != null", null,
                        List.of("s#if[0]/if[0]"), List.of("status"), Map.of("test", "status != null")),
                node("s#if[0]/if[0]", "if", "status.size() > 0", "s#if[0]",
                        List.of(), List.of("status.size"), Map.of("test", "status.size() > 0")),
                node("s#foreach[0]", "foreach", null, null, List.of(), List.of(),
                        Map.of("collection", "ids")),
                node("s#if[1]", "if", "category != null", null, List.of(),
                        List.of("category"), Map.of("test", "category != null")),
                node("s#if[2]", "if", "name != null", null, List.of(),
                        List.of("name"), Map.of("test", "name != null")));
        StatementStructure statement = new StatementStructure("demo.M", "s", "SELECT", "",
                nodes, List.of());
        Map<String, ParamInfo> params = new LinkedHashMap<>();
        params.put("memberId", new ParamInfo("memberId", ParamKind.LONG, true));
        params.put("status", new ParamInfo("status", ParamKind.STRING, true));
        params.put("ids", new ParamInfo("ids", ParamKind.LIST, true));
        params.put("category", new ParamInfo("category", ParamKind.STRING, true));
        params.put("name", new ParamInfo("name", ParamKind.STRING, true));
        var refs = new StatementReferenceResolver.References("demo.M", "s", "SELECT",
                List.of("loan"), params, List.of());
        var stat = new ColumnStat("stat", "snap_1", "public", "loan", "category",
                0.0, 2L, null, null, "[{\"value\":\"BOOK\"}]", "[]", "[]",
                Instant.parse("2026-01-01T00:00:00Z"));
        var planner = new PlannerInput(params, List.of(), List.of(), List.of(),
                List.of(new ShardInfo("member_id", null)), List.of(),
                "kb@3", "snap_1", 20);
        var context = new ScenarioContextResolver.ContextBundle(planner, refs, List.of(),
                List.of(), List.of(new ShardInfo("member_id", null)), List.of(stat),
                "kb@3", "snap_1");

        var suggestions = service.suggestions(statement, params, context);

        var when = suggestions.stream().filter(item ->
                item.kind() == NodeKind.CHOOSE_WHEN).findFirst().orElseThrow();
        assertEquals("s#choose[0]", when.chooseGroupId());
        assertEquals(ConditionCategory.ROUTING, when.category());
        assertEquals(CategorySource.SERVER_EXPLAINED, when.categorySource());
        var foreach = suggestions.stream().filter(item ->
                item.kind() == NodeKind.FOREACH).findFirst().orElseThrow();
        assertEquals(ValueType.COLLECTION, foreach.suggestedValue().type());
        var conflict = suggestions.stream().filter(item ->
                item.nodeId().equals("s#if[0]")).findFirst().orElseThrow();
        assertFalse(conflict.assignable());
        assertTrue(conflict.reason().contains("类型冲突"));
        var profiled = suggestions.stream().filter(item ->
                "category".equals(item.parameterPath())).findFirst().orElseThrow();
        assertEquals("PROFILE_SNAPSHOT", profiled.source());
        assertEquals("BOOK", profiled.suggestedValue().value());
        assertTrue(profiled.suggestedEnabled());
        var fallback = suggestions.stream().filter(item ->
                "name".equals(item.parameterPath())).findFirst().orElseThrow();
        assertEquals(" ", fallback.suggestedValue().value());
        assertFalse(fallback.suggestedEnabled());
        assertEquals(CategorySource.STRUCTURE_FALLBACK, fallback.categorySource());
    }

    private static NodeInfo node(String id, String type, String test, String parent,
                                 List<String> children, List<String> names,
                                 Map<String, String> attributes) {
        return new NodeInfo(id, type, test, parent, children, id, attributes, names);
    }
}
