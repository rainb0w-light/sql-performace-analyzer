package com.biz.sccba.sqlanalyzer.idea.state;

import com.biz.sccba.sqlanalyzer.idea.state.AnalysisState.AnalysisError;
import com.biz.sccba.sqlanalyzer.idea.state.AnalysisState.ConnectionState;
import com.biz.sccba.sqlanalyzer.idea.state.AnalysisState.Guard;
import com.biz.sccba.sqlanalyzer.idea.state.AnalysisState.StatementContext;

import java.util.List;

/** Typed events consumed by {@link AnalysisReducer}; display text is never a state fact. */
public sealed interface AnalysisEvent permits
        AnalysisEvent.ResolveStatement,
        AnalysisEvent.StatementResolved,
        AnalysisEvent.DatasourceRequired,
        AnalysisEvent.BindingResolved,
        AnalysisEvent.PreparationStarted,
        AnalysisEvent.UploadStarted,
        AnalysisEvent.ContextLoading,
        AnalysisEvent.SuggestionsRequested,
        AnalysisEvent.SuggestionsReady,
        AnalysisEvent.MainScenarioConfirmed,
        AnalysisEvent.PlanReady,
        AnalysisEvent.SubmitStarted,
        AnalysisEvent.RunAccepted,
        AnalysisEvent.RunStarted,
        AnalysisEvent.PhaseChanged,
        AnalysisEvent.ConnectionChanged,
        AnalysisEvent.EventConsumed,
        AnalysisEvent.ReportReady,
        AnalysisEvent.ProjectionSucceeded,
        AnalysisEvent.ProjectionFailed,
        AnalysisEvent.CancelRequested,
        AnalysisEvent.Cancelled,
        AnalysisEvent.NotCancellable,
        AnalysisEvent.AuthenticationRequired,
        AnalysisEvent.Failed,
        AnalysisEvent.RunFinished,
        AnalysisEvent.Reset {

    record ResolveStatement() implements AnalysisEvent {}
    record StatementResolved(StatementContext context) implements AnalysisEvent {}
    record DatasourceRequired(List<Guard> guards) implements AnalysisEvent {}
    record BindingResolved(StatementContext context) implements AnalysisEvent {}
    record PreparationStarted() implements AnalysisEvent {}
    record UploadStarted() implements AnalysisEvent {}
    record ContextLoading() implements AnalysisEvent {}
    record SuggestionsRequested() implements AnalysisEvent {}
    record SuggestionsReady() implements AnalysisEvent {}
    record MainScenarioConfirmed() implements AnalysisEvent {}
    record PlanReady(List<Guard> guards, boolean reviewRequired) implements AnalysisEvent {}
    record SubmitStarted() implements AnalysisEvent {}
    record RunAccepted(String sessionId, String runId, boolean cancellable) implements AnalysisEvent {}
    record RunStarted() implements AnalysisEvent {}
    record PhaseChanged(String phase) implements AnalysisEvent {}
    record ConnectionChanged(ConnectionState state, String reason) implements AnalysisEvent {}
    record EventConsumed(String eventId) implements AnalysisEvent {}
    record ReportReady(String reportId) implements AnalysisEvent {}
    record ProjectionSucceeded() implements AnalysisEvent {}
    record ProjectionFailed(AnalysisError error) implements AnalysisEvent {}
    record CancelRequested() implements AnalysisEvent {}
    record Cancelled() implements AnalysisEvent {}
    record NotCancellable() implements AnalysisEvent {}
    record AuthenticationRequired(AnalysisError error) implements AnalysisEvent {}
    record Failed(AnalysisError error) implements AnalysisEvent {}
    record RunFinished(TerminalStatus status) implements AnalysisEvent {}
    record Reset() implements AnalysisEvent {}

    enum TerminalStatus { COMPLETED, CANCELLED, FAILED }
}
