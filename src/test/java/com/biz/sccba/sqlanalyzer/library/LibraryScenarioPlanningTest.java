package com.biz.sccba.sqlanalyzer.library;

import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamInfo;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ParamKind;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.ShardInfo;
import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioPlanner;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Library shard scenarios (docs/cloud-code-next-goal.md §4.6/§5.2): with the loan shard metadata
 * (primary member_id, secondary borrowed_at), the planner must emit single-shard, cross-shard
 * and secondary-time scenarios, and every BoundSql must come from the official MyBatis runtime
 * with fingerprints deduped under the cap.
 */
class LibraryScenarioPlanningTest {

    private static byte[] loanMapper() throws Exception {
        try (InputStream in = LibraryScenarioPlanningTest.class
                .getResourceAsStream("/fixtures/library/mapper/LoanMapper.xml")) {
            return in.readAllBytes();
        }
    }

    private static PlannerInput loanInput() {
        Map<String, ParamInfo> params = new LinkedHashMap<>();
        params.put("asOf", new ParamInfo("asOf", ParamKind.DATE, false));
        params.put("memberId", new ParamInfo("memberId", ParamKind.LONG, true));
        params.put("branchId", new ParamInfo("branchId", ParamKind.LONG, true));
        params.put("dueBefore", new ParamInfo("dueBefore", ParamKind.DATE, true));
        params.put("borrowedFrom", new ParamInfo("borrowedFrom", ParamKind.DATE, true));
        params.put("borrowedTo", new ParamInfo("borrowedTo", ParamKind.DATE, true));
        params.put("statuses", new ParamInfo("statuses", ParamKind.LIST, true));
        return new PlannerInput(params, List.of(), List.of(), List.of(),
                List.of(new ShardInfo("member_id", "borrowed_at")),
                List.of(), "library-domain@1", null, 20);
    }

    @Test
    void shardMetadataDrivesSingleCrossAndSecondaryScenarios() throws Exception {
        ScenarioEngine engine = new ScenarioEngine(new DynamicNodeCatalog(), new ScenarioPlanner());
        var result = engine.plan(loanMapper(), "fixtures/library/mapper/LoanMapper.xml",
                "findOverdueLoans", loanInput(), null, null);

        assertEquals(null, result.loadError());
        assertFalse(result.scenarios().isEmpty());
        assertTrue(result.scenarios().size() <= 20, "scenario count must respect the cap");

        // Merged coverage goals: SHARD_SINGLE binds to the same SQL shape as the main path, so
        // its intent survives dedup via the merged goal set of the fingerprint group.
        var goals = result.scenarios().stream()
                .flatMap(s -> s.mergedCoverageGoals().stream()).toList();
        assertTrue(goals.contains("SHARD_SINGLE"), "single-shard scenario (member_id set): " + goals);
        assertTrue(goals.contains("SHARD_CROSS"), "cross-shard scenario (member_id missing): " + goals);
        assertTrue(goals.contains("SHARD_SECONDARY_MISSING"),
                "secondary shard scenario (borrowed_at missing): " + goals);

        // Every scenario carries a BoundSql from the official runtime and a fingerprint.
        assertTrue(result.scenarios().stream().allMatch(s -> s.isUnsupported()
                || (s.boundSql() != null && !s.boundSql().isBlank() && s.sqlFingerprint() != null)));

        // Fingerprints are unique after dedup.
        long distinctFingerprints = result.scenarios().stream()
                .filter(s -> !s.isUnsupported())
                .map(s -> s.sqlFingerprint()).distinct().count();
        long boundCount = result.scenarios().stream().filter(s -> !s.isUnsupported()).count();
        assertEquals(distinctFingerprints, boundCount, "BoundSql fingerprints must be deduped");

        // The single-shard SQL constrains member_id; the cross-shard SQL does not.
        boolean singleHasMemberEq = result.scenarios().stream()
                .filter(s -> s.mergedCoverageGoals().contains("SHARD_SINGLE"))
                .anyMatch(s -> s.boundSql() != null && s.boundSql().contains("member_id = ?"));
        boolean crossLacksMemberEq = result.scenarios().stream()
                .filter(s -> s.mergedCoverageGoals().contains("SHARD_CROSS"))
                .anyMatch(s -> s.boundSql() != null && !s.boundSql().contains("member_id = ?"));
        assertTrue(singleHasMemberEq, "single-shard BoundSql must filter by member_id");
        assertTrue(crossLacksMemberEq, "cross-shard BoundSql must lack the member_id filter");
    }

    @Test
    void statusesForeachClassesAppear() throws Exception {
        ScenarioEngine engine = new ScenarioEngine(new DynamicNodeCatalog(), new ScenarioPlanner());
        var result = engine.plan(loanMapper(), "fixtures/library/mapper/LoanMapper.xml",
                "findOverdueLoans", loanInput(), null, null);
        // Merged goals: foreach class scenarios sharing a fingerprint with the main path
        // contribute their goals to the merged set.
        var goals = result.scenarios().stream()
                .flatMap(s -> s.mergedCoverageGoals().stream()).toList();
        assertTrue(goals.stream().anyMatch(g -> g.startsWith("FOREACH_EMPTY")), "foreach empty class");
        assertTrue(goals.stream().anyMatch(g -> g.startsWith("FOREACH_SINGLE")), "foreach single class");
        assertTrue(goals.stream().anyMatch(g -> g.startsWith("FOREACH_MULTI")), "foreach multi class");
    }
}
