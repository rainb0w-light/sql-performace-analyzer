package com.biz.sccba.sqlanalyzer.idea.state;

import com.biz.sccba.sqlanalyzer.idea.state.AnalysisEvent.*;
import com.biz.sccba.sqlanalyzer.idea.state.AnalysisState.*;

import java.util.List;

/** Pure reducer implementing the frozen product state machine. */
public final class AnalysisReducer {
    private AnalysisReducer() {}

    public static AnalysisState reduce(AnalysisState state, AnalysisEvent event) {
        if (event instanceof Reset) return AnalysisState.idle();
        if (event instanceof ConnectionChanged changed) {
            return copy(state, state.businessState(), changed.state(), state.statement(), state.run(),
                    state.phase(), state.lastEventId(), state.guards(), state.error(), state.draftPreserved());
        }
        if (event instanceof EventConsumed consumed) {
            return copy(state, state.businessState(), state.connectionState(), state.statement(), state.run(),
                    state.phase(), consumed.eventId(), state.guards(), state.error(), state.draftPreserved());
        }
        if (event instanceof AuthenticationRequired auth) {
            return copy(state, BusinessState.AUTH_REQUIRED, state.connectionState(), state.statement(), state.run(),
                    state.phase(), state.lastEventId(), state.guards(), auth.error(), true);
        }
        if (event instanceof Failed failed) {
            return copy(state, BusinessState.FAILED, state.connectionState(), state.statement(), state.run(),
                    state.phase(), state.lastEventId(), state.guards(), failed.error(), state.draftPreserved());
        }
        if (event instanceof ResolveStatement) return business(state, BusinessState.RESOLVING_STATEMENT);
        if (event instanceof StatementResolved resolved) {
            return copy(state, BusinessState.RESOLVING_BINDING, state.connectionState(), resolved.context(),
                    RunContext.empty(), "", "", List.of(), null, false);
        }
        if (event instanceof DatasourceRequired required) {
            return copy(state, BusinessState.NEEDS_DATASOURCE, state.connectionState(), state.statement(), state.run(),
                    state.phase(), state.lastEventId(), required.guards(), null, state.draftPreserved());
        }
        if (event instanceof BindingResolved resolved) {
            return copy(state, BusinessState.PREPARING, state.connectionState(), resolved.context(), state.run(),
                    state.phase(), state.lastEventId(), List.of(), null, state.draftPreserved());
        }
        if (event instanceof PreparationStarted) return business(state, BusinessState.PREPARING);
        if (event instanceof UploadStarted) return business(state, BusinessState.UPLOADING);
        if (event instanceof ContextLoading) return business(state, BusinessState.LOADING_CONTEXT);
        if (event instanceof SuggestionsRequested) return business(state, BusinessState.SUGGESTING_DEFAULTS);
        if (event instanceof SuggestionsReady) return business(state, BusinessState.CONFIGURING_MAIN_SCENARIO);
        if (event instanceof MainScenarioConfirmed) return business(state, BusinessState.PLANNING);
        if (event instanceof PlanReady planned) {
            BusinessState next = planned.reviewRequired() || planned.guards().stream().anyMatch(Guard::blocking)
                    ? BusinessState.AWAITING_REVIEW : BusinessState.SUBMITTING;
            return copy(state, next, state.connectionState(), state.statement(), state.run(), state.phase(),
                    state.lastEventId(), planned.guards(), null, state.draftPreserved());
        }
        if (event instanceof SubmitStarted) return business(state, BusinessState.SUBMITTING);
        if (event instanceof RunAccepted accepted) {
            return copy(state, BusinessState.QUEUED, ConnectionState.CONNECTING, state.statement(),
                    new RunContext(accepted.sessionId(), accepted.runId(), "", accepted.cancellable()),
                    "", state.lastEventId(), state.guards(), null, false);
        }
        if (event instanceof RunStarted) return business(state, BusinessState.RUNNING);
        if (event instanceof PhaseChanged phase) {
            return copy(state, state.businessState(), state.connectionState(), state.statement(), state.run(),
                    phase.phase(), state.lastEventId(), state.guards(), state.error(), state.draftPreserved());
        }
        if (event instanceof ReportReady report) {
            RunContext run = new RunContext(state.run().sessionId(), state.run().runId(), report.reportId(), state.run().cancellable());
            return copy(state, BusinessState.PROJECTING, state.connectionState(), state.statement(), run,
                    state.phase(), state.lastEventId(), state.guards(), null, state.draftPreserved());
        }
        if (event instanceof ProjectionSucceeded) return business(state, BusinessState.COMPLETED);
        if (event instanceof ProjectionFailed projection) {
            return copy(state, BusinessState.PROJECTION_FAILED, state.connectionState(), state.statement(), state.run(),
                    state.phase(), state.lastEventId(), state.guards(), projection.error(), state.draftPreserved());
        }
        if (event instanceof CancelRequested) return business(state, BusinessState.CANCELLING);
        if (event instanceof Cancelled) return business(state, BusinessState.CANCELLED);
        if (event instanceof NotCancellable) return business(state, BusinessState.RUNNING);
        if (event instanceof RunFinished finished) {
            BusinessState terminal = switch (finished.status()) {
                case COMPLETED -> state.businessState() == BusinessState.PROJECTION_FAILED
                        ? BusinessState.PROJECTION_FAILED : BusinessState.COMPLETED;
                case CANCELLED -> BusinessState.CANCELLED;
                case FAILED -> BusinessState.FAILED;
            };
            RunContext run = new RunContext(state.run().sessionId(), state.run().runId(),
                    state.run().reportId(), false);
            return copy(state, terminal, ConnectionState.TERMINAL, state.statement(), run, state.phase(),
                    state.lastEventId(), state.guards(), state.error(), state.draftPreserved());
        }
        return state;
    }

    private static AnalysisState business(AnalysisState state, BusinessState business) {
        return copy(state, business, state.connectionState(), state.statement(), state.run(), state.phase(),
                state.lastEventId(), state.guards(), state.error(), state.draftPreserved());
    }

    private static AnalysisState copy(AnalysisState old, BusinessState business, ConnectionState connection,
                                      StatementContext statement, RunContext run, String phase, String eventId,
                                      List<Guard> guards, AnalysisError error, boolean draftPreserved) {
        return new AnalysisState(business, connection, statement, run, phase, eventId,
                guards, error, draftPreserved);
    }
}

