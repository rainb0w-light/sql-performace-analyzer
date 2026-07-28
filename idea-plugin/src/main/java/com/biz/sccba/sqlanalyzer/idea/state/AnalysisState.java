package com.biz.sccba.sqlanalyzer.idea.state;

import java.util.List;

/** Immutable UI state. Business Run state and SSE transport state are intentionally orthogonal. */
public record AnalysisState(
        BusinessState businessState,
        ConnectionState connectionState,
        StatementContext statement,
        RunContext run,
        String phase,
        String lastEventId,
        List<Guard> guards,
        AnalysisError error,
        boolean draftPreserved
) {
    public AnalysisState {
        businessState = businessState == null ? BusinessState.IDLE : businessState;
        connectionState = connectionState == null ? ConnectionState.DISCONNECTED : connectionState;
        statement = statement == null ? StatementContext.empty() : statement;
        run = run == null ? RunContext.empty() : run;
        phase = phase == null ? "" : phase;
        lastEventId = lastEventId == null ? "" : lastEventId;
        guards = guards == null ? List.of() : List.copyOf(guards);
    }

    public static AnalysisState idle() {
        return new AnalysisState(BusinessState.IDLE, ConnectionState.DISCONNECTED,
                StatementContext.empty(), RunContext.empty(), "", "", List.of(), null, false);
    }

    public boolean hasBlockingGuards() {
        return guards.stream().anyMatch(Guard::blocking);
    }

    public enum BusinessState {
        IDLE,
        RESOLVING_STATEMENT,
        RESOLVING_BINDING,
        NEEDS_DATASOURCE,
        PREPARING,
        UPLOADING,
        LOADING_CONTEXT,
        SUGGESTING_DEFAULTS,
        CONFIGURING_MAIN_SCENARIO,
        PLANNING,
        AWAITING_REVIEW,
        SUBMITTING,
        QUEUED,
        RUNNING,
        PROJECTING,
        COMPLETED,
        PROJECTION_FAILED,
        CANCELLING,
        CANCELLED,
        AUTH_REQUIRED,
        FAILED
    }

    public enum ConnectionState { DISCONNECTED, CONNECTING, STREAMING, BACKOFF, RESUMING, TERMINAL, ABORTED }

    public record StatementContext(
            String namespace,
            String statementId,
            String statementType,
            String mapperPath,
            String moduleName,
            String contentHash,
            String datasourceProfileId,
            String datasourceDisplayName,
            String datasourceBindingSource,
            String knowledgeVersion,
            String profileSnapshotId,
            boolean readOnlyStaticAnalysis
    ) {
        public StatementContext {
            namespace = text(namespace);
            statementId = text(statementId);
            statementType = text(statementType);
            mapperPath = text(mapperPath);
            moduleName = text(moduleName);
            contentHash = text(contentHash);
            datasourceProfileId = text(datasourceProfileId);
            datasourceDisplayName = text(datasourceDisplayName);
            datasourceBindingSource = text(datasourceBindingSource);
            knowledgeVersion = text(knowledgeVersion);
            profileSnapshotId = text(profileSnapshotId);
        }

        public static StatementContext empty() {
            return new StatementContext("", "", "", "", "", "", "", "", "", "", "", false);
        }

        private static String text(String value) { return value == null ? "" : value; }
    }

    public record RunContext(
            String sessionId,
            String runId,
            String reportId,
            boolean cancellable
    ) {
        public RunContext {
            sessionId = sessionId == null ? "" : sessionId;
            runId = runId == null ? "" : runId;
            reportId = reportId == null ? "" : reportId;
        }

        public static RunContext empty() { return new RunContext("", "", "", false); }
    }

    public enum GuardType {
        DATASOURCE_MISSING,
        DATASOURCE_AMBIGUOUS,
        DOLLAR_WHITELIST_MISSING,
        SCENARIO_OR_COST_LIMIT,
        UNSUPPORTED_LANGUAGE_OR_TYPE
    }

    public record Guard(GuardType type, boolean blocking, String message, String locator) {
        public Guard {
            message = message == null ? "" : message;
            locator = locator == null ? "" : locator;
        }
    }

    public record AnalysisError(String code, String message, boolean retryable, String nextAction) {
        public AnalysisError {
            code = code == null ? "UNKNOWN" : code;
            message = message == null ? "" : message;
            nextAction = nextAction == null ? "" : nextAction;
        }
    }
}
