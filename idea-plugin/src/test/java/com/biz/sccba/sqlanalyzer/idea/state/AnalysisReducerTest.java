package com.biz.sccba.sqlanalyzer.idea.state;

import com.biz.sccba.sqlanalyzer.idea.state.AnalysisEvent.*;
import com.biz.sccba.sqlanalyzer.idea.state.AnalysisState.*;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AnalysisReducerTest {
    @Test
    public void fullHappyPathUsesTypedEvents() {
        AnalysisState state = AnalysisState.idle();
        state = AnalysisReducer.reduce(state, new ResolveStatement());
        assertEquals(BusinessState.RESOLVING_STATEMENT, state.businessState());
        state = AnalysisReducer.reduce(state, new StatementResolved(statement("SELECT")));
        state = AnalysisReducer.reduce(state, new BindingResolved(statement("SELECT")));
        state = AnalysisReducer.reduce(state, new ContextLoading());
        state = AnalysisReducer.reduce(state, new SuggestionsRequested());
        state = AnalysisReducer.reduce(state, new SuggestionsReady());
        assertEquals(BusinessState.CONFIGURING_MAIN_SCENARIO, state.businessState());
        state = AnalysisReducer.reduce(state, new MainScenarioConfirmed());
        state = AnalysisReducer.reduce(state, new PlanReady(List.of(), false));
        state = AnalysisReducer.reduce(state, new RunAccepted("session_1", "run_1", true));
        assertEquals(BusinessState.QUEUED, state.businessState());
        assertEquals(ConnectionState.CONNECTING, state.connectionState());
        state = AnalysisReducer.reduce(state, new RunStarted());
        state = AnalysisReducer.reduce(state, new ReportReady("report_1"));
        assertEquals(BusinessState.PROJECTING, state.businessState());
        state = AnalysisReducer.reduce(state, new ProjectionSucceeded());
        state = AnalysisReducer.reduce(state, new RunFinished(TerminalStatus.COMPLETED));
        assertEquals(BusinessState.COMPLETED, state.businessState());
        assertEquals(ConnectionState.TERMINAL, state.connectionState());
    }

    @Test
    public void connectionFailureDoesNotChangeRunningBusinessState() {
        AnalysisState running = AnalysisReducer.reduce(
                AnalysisReducer.reduce(AnalysisState.idle(), new RunAccepted("s", "r", true)),
                new RunStarted());
        AnalysisState backoff = AnalysisReducer.reduce(running,
                new ConnectionChanged(ConnectionState.BACKOFF, "network"));
        assertEquals(BusinessState.RUNNING, backoff.businessState());
        assertEquals(ConnectionState.BACKOFF, backoff.connectionState());
        AnalysisState resumed = AnalysisReducer.reduce(backoff,
                new ConnectionChanged(ConnectionState.STREAMING, ""));
        assertEquals(BusinessState.RUNNING, resumed.businessState());
    }

    @Test
    public void allFiveBlockingGuardsForceReview() {
        List<Guard> guards = List.of(
                guard(GuardType.DATASOURCE_MISSING),
                guard(GuardType.DATASOURCE_AMBIGUOUS),
                guard(GuardType.DOLLAR_WHITELIST_MISSING),
                guard(GuardType.SCENARIO_OR_COST_LIMIT),
                guard(GuardType.UNSUPPORTED_LANGUAGE_OR_TYPE));
        AnalysisState state = AnalysisReducer.reduce(AnalysisState.idle(), new PlanReady(guards, false));
        assertEquals(BusinessState.AWAITING_REVIEW, state.businessState());
        assertTrue(state.hasBlockingGuards());
        assertEquals(5, state.guards().size());
    }

    @Test
    public void authenticationPreservesUnsubmittedDraft() {
        AnalysisError error = new AnalysisError("UNAUTHORIZED", "token expired", false, "重新认证");
        AnalysisState state = AnalysisReducer.reduce(AnalysisState.idle(), new AuthenticationRequired(error));
        assertEquals(BusinessState.AUTH_REQUIRED, state.businessState());
        assertTrue(state.draftPreserved());
    }

    @Test
    public void projectionFailureIsNotRunFailure() {
        AnalysisState state = AnalysisReducer.reduce(AnalysisState.idle(), new RunAccepted("s", "r", true));
        state = AnalysisReducer.reduce(state, new ReportReady("report_1"));
        state = AnalysisReducer.reduce(state, new ProjectionFailed(
                new AnalysisError("REPORT_UNAVAILABLE", "unavailable", true, "重试加载报告")));
        state = AnalysisReducer.reduce(state, new RunFinished(TerminalStatus.COMPLETED));
        assertEquals(BusinessState.PROJECTION_FAILED, state.businessState());
        assertEquals("report_1", state.run().reportId());
    }

    private static Guard guard(GuardType type) { return new Guard(type, true, type.name(), ""); }

    private static StatementContext statement(String type) {
        return new StatementContext("n", "id", type, "/mapper.xml", "module", "hash",
                "dsp", "Dev", "MODULE_DEFAULT", "k@1", "snap_1",
                !"SELECT".equals(type) && !"WITH".equals(type));
    }
}

