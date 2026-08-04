package com.biz.sccba.sqlanalyzer.idea.state;

import com.biz.sccba.sqlanalyzer.idea.client.*;
import com.biz.sccba.sqlanalyzer.idea.client.BackendClient.*;
import com.biz.sccba.sqlanalyzer.idea.contract.PluginApiDtos.*;
import com.biz.sccba.sqlanalyzer.idea.history.BoundedReportCache;
import com.biz.sccba.sqlanalyzer.idea.mybatis.MyBatisStatementPsi;
import com.biz.sccba.sqlanalyzer.idea.mybatis.GutterAnalysisState;
import com.biz.sccba.sqlanalyzer.idea.report.ReportViewModel;
import com.biz.sccba.sqlanalyzer.idea.scenario.MainScenarioModel;
import com.biz.sccba.sqlanalyzer.idea.scenario.ScenarioReviewModel;
import com.biz.sccba.sqlanalyzer.idea.settings.ProjectAnalyzerSettings;
import com.biz.sccba.sqlanalyzer.idea.settings.TokenStore;
import com.biz.sccba.sqlanalyzer.idea.ui.AnalysisUiBridge;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import com.google.gson.*;

import static com.biz.sccba.sqlanalyzer.idea.state.AnalysisState.*;

/**
 * Project-scoped orchestration for the P1 flow. PSI supplies immutable statement/module facts;
 * network work stays off EDT; rendering consumes only reducer state and structured projections.
 */
@Service(Service.Level.PROJECT)
public final class AnalysisCoordinator implements Disposable {
    private final Project project;
    private final ProjectAnalyzerSettings settings;
    private final TokenStore tokens;
    private final AnalysisUiBridge bridge;
    private final ExecutorService executor;
    private final AtomicBoolean cancelling = new AtomicBoolean();
    private final BoundedReportCache cache;
    private final List<TransientRule> transientRules = new CopyOnWriteArrayList<>();
    private final Object stateLock = new Object();

    private volatile AnalysisState state = AnalysisState.idle();
    private volatile MyBatisStatementPsi.StatementRef statementRef;
    private volatile MainScenarioModel mainScenario;
    private volatile BackendClient client;
    private volatile AguiSseClient stream;
    private volatile List<DatasourceProfile> datasourceCandidates = List.of();
    private volatile String temporaryDatasourceId = "";
    private volatile String rawReport = "";
    private volatile String suggestionCacheKey = "";
    private volatile SuggestionSet cachedSuggestions;
    private volatile MainScenario lastConfirmedScenario;
    private volatile MainScenario reuseScenarioOnce;
    private volatile ScenarioReviewModel reviewModel;
    private volatile boolean keepTemporaryDatasourceOnce;

    public AnalysisCoordinator(Project project) {
        this.project = project;
        this.settings = ProjectAnalyzerSettings.getInstance(project);
        this.tokens = TokenStore.getInstance();
        this.bridge = AnalysisUiBridge.getInstance(project);
        this.cache = new BoundedReportCache(50,
                settings.localCacheMegabytes() * 1024L * 1024L);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "sql-analyzer-p1-coordinator");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static AnalysisCoordinator getInstance(Project project) {
        return project.getService(AnalysisCoordinator.class);
    }

    public AnalysisState state() { return state; }
    public MainScenarioModel mainScenario() { return mainScenario; }
    public List<DatasourceProfile> datasourceCandidates() { return datasourceCandidates; }
    public String rawReport() { return rawReport; }
    public void clearLocalCache() { cache.clear(); }

    public void begin(MyBatisStatementPsi.StatementRef ref) {
        Objects.requireNonNull(ref, "statement");
        if (statementRef == null || !statementRef.contentHash().equals(ref.contentHash())
                || !statementRef.statementId().equals(ref.statementId())) {
            lastConfirmedScenario = null;
        }
        this.statementRef = ref;
        project.getService(GutterAnalysisState.class).mark(ref.locator(),
                GutterAnalysisState.Status.READY, ref.contentHash(), "", "", "正在准备分析");
        if (!keepTemporaryDatasourceOnce) this.temporaryDatasourceId = "";
        keepTemporaryDatasourceOnce = false;
        this.mainScenario = null;
        this.rawReport = "";
        transientRules.clear();
        dispatch(new AnalysisEvent.ResolveStatement());
        StatementContext context = new StatementContext(ref.namespace(), ref.statementId(),
                ref.statementType(), ref.mapperPath(), ref.moduleName(), ref.contentHash(),
                "", "", "", "", "", isDml(ref.statementType()));
        dispatch(new AnalysisEvent.StatementResolved(context));
        executor.execute(this::resolveAndPrepare);
    }

    public void chooseDatasource(String profileId, boolean rememberForModule) {
        if (profileId == null || profileId.isBlank() || statementRef == null) return;
        temporaryDatasourceId = profileId;
        if (rememberForModule) settings.bindDatasourceProfile(statementRef.moduleName(), profileId);
        executor.execute(this::resolveAndPrepare);
    }

    public void confirmMainScenario() {
        MainScenarioModel model = mainScenario;
        if (model == null || !model.valid()) return;
        dispatch(new AnalysisEvent.MainScenarioConfirmed());
        lastConfirmedScenario = model.snapshot();
        executor.execute(() -> submit(lastConfirmedScenario));
    }

    public void previewBoundSql() {
        MainScenarioModel model = mainScenario;
        if (model == null || !model.valid() || client == null) return;
        executor.execute(() -> {
            try {
                MainScenario snapshot = model.snapshot();
                BoundSqlPreview preview = client.previewBoundSql(new BoundSqlPreviewRequest(
                        snapshot.suggestionSetId(), snapshot.selections(), snapshot.parameters()));
                bridge.boundSqlPreview(preview);
            } catch (Exception error) {
                handleError(error, "无法预览 BoundSql", "检查字段错误或服务端 P1 能力");
            }
        });
    }

    public void refreshSuggestions() {
        suggestionCacheKey = "";
        cachedSuggestions = null;
        executor.execute(() -> prepare(state.statement()));
    }

    public void cancelPreparation() {
        if (state.run().runId().isBlank()) {
            mainScenario = null;
            transientRules.clear();
            dispatch(new AnalysisEvent.Reset());
        }
    }

    public void previewTransientRules(Consumer<TransientRuleImpact> success) {
        if (client == null || transientRules.isEmpty()) return;
        executor.execute(() -> {
            try {
                String artifactId = settings.artifactForHash(statementRef.contentHash());
                TransientRuleImpact impact = client.previewTransientRules(
                        new TransientRulePreviewRequest(artifactId, statementRef.statementId(),
                                state.statement().datasourceProfileId(), project.getLocationHash(),
                                statementRef.moduleName(),
                                mainScenario == null ? null : mainScenario.snapshot(),
                                List.copyOf(transientRules), settings.maxScenarios(),
                                parseCost(settings.costThreshold())));
                if (success != null) com.intellij.openapi.application.ApplicationManager.getApplication()
                        .invokeLater(() -> success.accept(impact));
            } catch (Exception error) {
                handleError(error, "无法预览临时规则影响", "修正字段或重试");
            }
        });
    }

    public void replaceTransientRules(List<TransientRule> rules) {
        transientRules.clear();
        if (rules != null) transientRules.addAll(rules);
    }

    public void cancel() {
        String runId = state.run().runId();
        if (runId.isBlank() || client == null || !cancelling.compareAndSet(false, true)) return;
        dispatch(new AnalysisEvent.CancelRequested());
        executor.execute(() -> {
            try {
                client.cancelRun(runId);
                dispatch(new AnalysisEvent.Cancelled());
            } catch (Exception error) {
                handleError(error, "取消尚未确认", "重试取消或查询 Run 状态");
            } finally {
                cancelling.set(false);
            }
        });
    }

    public void confirmReview() {
        ScenarioReviewModel model = reviewModel;
        String runId = state.run().runId();
        if (model == null || runId.isBlank() || client == null) return;
        List<ExcludedScenario> excluded = model.exclusions().entrySet().stream()
                .map(entry -> new ExcludedScenario(entry.getKey(), entry.getValue())).toList();
        ScenarioConfirmation confirmation = new ScenarioConfirmation(
                new ArrayList<>(model.includedIds()), excluded);
        executor.execute(() -> {
            try {
                client.confirmRun(runId, confirmation, "idea-confirm-" + runId);
                dispatch(new AnalysisEvent.RunStarted());
            } catch (Exception error) {
                handleError(error, "场景确认失败", "检查 required 场景和排除原因");
            }
        });
    }

    public boolean includeScenario(String scenarioId, boolean included, String reason) {
        ScenarioReviewModel model = reviewModel;
        return model != null && model.include(scenarioId, included, reason);
    }

    /** Continues the preserved draft after 401 without clearing main scenario or transient rules. */
    public void resumeAfterAuthentication() {
        executor.execute(this::resolveAndPrepare);
    }

    public void reanalyze(ReanalysisMode mode, String datasourceId) {
        MyBatisStatementPsi.StatementRef ref = statementRef;
        if (ref == null) return;
        if (mode == ReanalysisMode.SWITCH_DATASOURCE) {
            temporaryDatasourceId = datasourceId == null ? "" : datasourceId;
            keepTemporaryDatasourceOnce = true;
        }
        if (mode == ReanalysisMode.REUSE_PARAMETERS) reuseScenarioOnce = lastConfirmedScenario;
        if (mode != ReanalysisMode.REUSE_PARAMETERS) {
            suggestionCacheKey = "";
            cachedSuggestions = null;
        }
        begin(ref);
    }

    public enum ReanalysisMode { REUSE_PARAMETERS, REFRESH_CONTEXT, SWITCH_DATASOURCE }

    private void resolveAndPrepare() {
        MyBatisStatementPsi.StatementRef ref = statementRef;
        if (ref == null) return;
        String token = tokens.token();
        if (token.isBlank()) {
            dispatch(new AnalysisEvent.AuthenticationRequired(new AnalysisError(
                    "UNAUTHORIZED", "尚未连接后端", false, "连接后端")));
            return;
        }
        try {
            client = new BackendClient(settings.endpoint(), token);
            DatasourceResolution resolution = client.resolveDatasource(temporaryDatasourceId,
                    settings.moduleDatasourceProfile(ref.moduleName()), settings.datasourceProfileId());
            datasourceCandidates = resolution.candidates();
            bridge.datasourceCandidates(datasourceCandidates);
            if (resolution.status() != DatasourceResolutionStatus.RESOLVED) {
                GuardType type = resolution.status() == DatasourceResolutionStatus.MISSING
                        ? GuardType.DATASOURCE_MISSING : GuardType.DATASOURCE_AMBIGUOUS;
                dispatch(new AnalysisEvent.DatasourceRequired(List.of(new Guard(type, true,
                        type == GuardType.DATASOURCE_MISSING ? "当前 statement 没有可用数据源"
                                : "多个数据源同等匹配，需要明确选择",
                        ref.locator()))));
                return;
            }
            DatasourceProfile profile = resolution.selected();
            StatementContext bound = new StatementContext(ref.namespace(), ref.statementId(), ref.statementType(),
                    ref.mapperPath(), ref.moduleName(), ref.contentHash(), profile.id(), profile.name(),
                    resolution.bindingSource(), "", "", isDml(ref.statementType()));
            dispatch(new AnalysisEvent.BindingResolved(bound));
            prepare(bound);
        } catch (Exception error) {
            handleError(error, "无法解析数据源", "选择数据源或重新认证");
        }
    }

    private void prepare(StatementContext context) {
        try {
            dispatch(new AnalysisEvent.PreparationStarted());
            String sessionId = settings.sessionId();
            if (sessionId.isBlank()) {
                sessionId = client.createSession("IDEA " + statementRef.namespace() + "." + statementRef.statementId());
                settings.sessionId(sessionId);
            }
            dispatch(new AnalysisEvent.UploadStarted());
            String artifactId = settings.artifactForHash(statementRef.contentHash());
            if (artifactId.isBlank()) {
                artifactId = statementRef.sourceKind() == MyBatisStatementPsi.SourceKind.XML
                        ? client.indexMyBatisMapper(sessionId, statementRef.mapperXml(), statementRef.namespace())
                        : client.indexMyBatisAnnotation(sessionId, statementRef.mapperXml(),
                        statementRef.namespace(), statementRef.statementId());
                settings.cacheArtifact(statementRef.contentHash(), artifactId);
            }
            dispatch(new AnalysisEvent.ContextLoading());
            MainScenario reuse = reuseScenarioOnce;
            if (reuse != null) {
                reuseScenarioOnce = null;
                dispatch(new AnalysisEvent.MainScenarioConfirmed());
                submit(reuse);
                return;
            }
            String key = statementRef.contentHash() + "|" + statementRef.statementId() + "|"
                    + context.datasourceProfileId();
            SuggestionSet suggestions;
            if (key.equals(suggestionCacheKey) && cachedSuggestions != null) {
                suggestions = cachedSuggestions;
            } else {
                dispatch(new AnalysisEvent.SuggestionsRequested());
                suggestions = client.suggestDefaultParameters(new SuggestionRequest(artifactId,
                        statementRef.statementId(), context.datasourceProfileId(), project.getLocationHash(),
                        statementRef.moduleName(), statementRef.contentHash()));
                suggestionCacheKey = key;
                cachedSuggestions = suggestions;
            }
            mainScenario = new MainScenarioModel(suggestions);
            if (mainScenario.requiresConfirmation()) {
                dispatch(new AnalysisEvent.SuggestionsReady());
                bridge.mainScenario(mainScenario);
            } else {
                dispatch(new AnalysisEvent.MainScenarioConfirmed());
                submit(mainScenario.snapshot());
            }
        } catch (Exception error) {
            handleError(error, "准备分析失败", "检查 Mapper、类型或服务端 P1 能力");
        }
    }

    private void submit(MainScenario scenario) {
        try {
            dispatch(new AnalysisEvent.PlanReady(List.of(), false));
            dispatch(new AnalysisEvent.SubmitStarted());
            String artifactId = settings.artifactForHash(statementRef.contentHash());
            AnalyzeRequest request = requestForCurrentSession(artifactId, scenario);
            AnalysisHandle handle = client.analyzeStatement(request,
                    "idea-" + project.getLocationHash() + "-" + UUID.randomUUID());
            settings.sessionId(handle.sessionId());
            dispatch(new AnalysisEvent.RunAccepted(handle.sessionId(), handle.runId(), true));
            bridge.runStarted(handle.runId());
            followStream(handle);
        } catch (Exception error) {
            if (isMissingSessionError(error) && !settings.sessionId().isBlank()) {
                settings.sessionId("");
                try {
                    String retryArtifactId = settings.artifactForHash(statementRef.contentHash());
                    AnalyzeRequest request = requestForCurrentSession(retryArtifactId, scenario);
                    AnalysisHandle handle = client.analyzeStatement(request,
                            "idea-" + project.getLocationHash() + "-" + UUID.randomUUID());
                    settings.sessionId(handle.sessionId());
                    dispatch(new AnalysisEvent.RunAccepted(handle.sessionId(), handle.runId(), true));
                    bridge.runStarted(handle.runId());
                    followStream(handle);
                    return;
                } catch (Exception retryError) {
                    handleError(retryError, "提交分析失败", "按错误类型重试或重新认证");
                    return;
                }
            }
            handleError(error, "提交分析失败", "按错误类型重试或重新认证");
        }
    }

    private AnalyzeRequest requestForCurrentSession(String artifactId, MainScenario scenario) {
        return new AnalyzeRequest(artifactId, statementRef.statementId(),
                state.statement().datasourceProfileId(), project.getLocationHash(),
                statementRef.moduleName(), settings.sessionId(),
                "REVIEW".equals(settings.executionMode()) ? ExecutionMode.REVIEW : ExecutionMode.AUTO,
                scenario, List.copyOf(transientRules), settings.maxScenarios(), parseCost(settings.costThreshold()));
    }

    private void followStream(AnalysisHandle handle) {
        stream = new AguiSseClient(settings.endpoint(), tokens.token());
        try {
            stream.streamExisting(handle.streamUrl(), handle.runId(), new AguiSseClient.Listener() {
                @Override
                public boolean onEvent(String id, String type, String json) {
                    bridge.streamText(AguiEventRenderer.render(type, json));
                    captureScenarioPlan(type, json);
                    for (AnalysisEvent event : AguiStateMapper.map(id, type, json)) {
                        dispatch(event);
                        if (event instanceof AnalysisEvent.ReportReady ready) loadReport(ready.reportId());
                    }
                    return "RUN_FINISHED".equals(type);
                }

                @Override
                public void onConnectionState(AguiSseClient.ConnectionState connection, int attempt,
                                              String lastEventId, String reason) {
                    ConnectionState mapped = switch (connection) {
                        case CONNECTING -> ConnectionState.CONNECTING;
                        case STREAMING -> ConnectionState.STREAMING;
                        case BACKOFF -> ConnectionState.BACKOFF;
                        case RESUMING -> ConnectionState.RESUMING;
                        case TERMINAL -> ConnectionState.TERMINAL;
                        case ABORTED -> ConnectionState.ABORTED;
                    };
                    dispatch(new AnalysisEvent.ConnectionChanged(mapped, reason));
                }
            });
        } catch (Exception error) {
            recoverRunOrFail(handle.runId(), error);
        }
    }

    private void captureScenarioPlan(String type, String json) {
        if (!"CUSTOM".equals(type)) return;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String name = root.has("name") ? root.get("name").getAsString() : "";
            if (!"spa.scenarios_ready".equals(name) && !"spa.scenario_matrix".equals(name)) return;
            JsonArray scenarios = root.has("scenarios") && root.get("scenarios").isJsonArray()
                    ? root.getAsJsonArray("scenarios") : new JsonArray();
            List<ScenarioReviewModel.Scenario> values = new ArrayList<>();
            for (JsonElement element : scenarios) {
                JsonObject item = element.getAsJsonObject();
                String id = text(item, "scenarioId");
                values.add(new ScenarioReviewModel.Scenario(id, text(item, "name"),
                        bool(item, "required"), bool(item, "mainPath"), bool(item, "guardScenario"),
                        !item.has("excludable") || bool(item, "excludable"), text(item, "costLevel")));
            }
            reviewModel = new ScenarioReviewModel(values);
            bridge.scenarioMatrix(json);
        } catch (RuntimeException ignored) {
            // malformed scenario payload is surfaced by normal projection/error paths
        }
    }

    private void loadReport(String reportId) {
        try {
            String json = client.report(reportId);
            rawReport = json;
            cache.put(reportId, json);
            ReportViewModel model = ReportViewModel.parse(json);
            bridge.reportModel(model, json);
            bridge.report(json);
            bridge.scenarioMatrix(json);
            dispatch(new AnalysisEvent.ProjectionSucceeded());
        } catch (Exception error) {
            dispatch(new AnalysisEvent.ProjectionFailed(error("REPORT_PROJECTION_FAILED",
                    "报告视图暂时无法加载", true, "重试加载报告或导出原始 JSON")));
        }
    }

    private void recoverRunOrFail(String runId, Exception streamError) {
        try {
            RunStatus recovered = client.runStatus(runId);
            if (recovered.lastEventId() != null && !recovered.lastEventId().isBlank()) {
                dispatch(new AnalysisEvent.EventConsumed(recovered.lastEventId()));
            }
            if ("COMPLETED".equals(recovered.status())) {
                if (recovered.reportId() != null && !recovered.reportId().isBlank()) loadReport(recovered.reportId());
                dispatch(new AnalysisEvent.RunFinished(AnalysisEvent.TerminalStatus.COMPLETED));
            } else if ("CANCELLED".equals(recovered.status())) {
                dispatch(new AnalysisEvent.RunFinished(AnalysisEvent.TerminalStatus.CANCELLED));
            } else {
                handleError(streamError, "SSE 重连耗尽，Run 仍未终止", "查询状态或取消 Run");
            }
        } catch (Exception queryError) {
            handleError(streamError, "SSE 重连耗尽且无法恢复 Run 状态", "检查网络后查询 Run");
        }
    }

    private void handleError(Exception thrown, String fallback, String nextAction) {
        Throwable root = thrown;
        while (root.getCause() != null) root = root.getCause();
        if (root instanceof BackendException backend && backend.authenticationRequired()) {
            dispatch(new AnalysisEvent.AuthenticationRequired(error(backend.code(),
                    "需要重新认证；未提交配置已保留", false, "重新认证")));
            return;
        }
        String code = root instanceof BackendException backend ? backend.code() : "NETWORK";
        boolean retryable = root instanceof BackendException backend && backend.retryable();
        String message = root.getMessage() == null || root.getMessage().isBlank() ? fallback : root.getMessage();
        dispatch(new AnalysisEvent.Failed(error(code, message, retryable, nextAction)));
        bridge.streamText(fallback + "：" + message + "\n");
    }

    private void dispatch(AnalysisEvent event) {
        synchronized (stateLock) { state = AnalysisReducer.reduce(state, event); }
        updateGutter();
        bridge.state(state);
        if (state.businessState() == BusinessState.COMPLETED
                || state.businessState() == BusinessState.CANCELLED
                || state.businessState() == BusinessState.FAILED) {
            transientRules.clear();
        }
    }

    private void updateGutter() {
        MyBatisStatementPsi.StatementRef ref = statementRef;
        if (ref == null) return;
        GutterAnalysisState.Status gutterStatus = switch (state.businessState()) {
            case QUEUED, RUNNING, PROJECTING, CANCELLING -> GutterAnalysisState.Status.RUNNING;
            case COMPLETED -> GutterAnalysisState.Status.COMPLETED;
            case FAILED, PROJECTION_FAILED -> GutterAnalysisState.Status.FAILED;
            default -> GutterAnalysisState.Status.READY;
        };
        String message = switch (gutterStatus) {
            case RUNNING -> "正在分析；点击打开 Tool Window";
            case COMPLETED -> "分析已完成；点击查看报告";
            case FAILED -> "分析失败；点击查看结构化错误";
            case STALE -> "结果可能已过期";
            case READY -> "分析 SQL 性能";
        };
        project.getService(GutterAnalysisState.class).mark(ref.locator(), gutterStatus,
                ref.contentHash(), state.statement().datasourceProfileId(),
                reportSeverity(), message);
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() ->
                com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart());
    }

    private String reportSeverity() {
        if (rawReport == null || rawReport.isBlank()) return "";
        try {
            return JsonParser.parseString(rawReport).getAsJsonObject()
                    .getAsJsonObject("summary").get("severity").getAsString();
        } catch (RuntimeException ignored) { return ""; }
    }

    private static AnalysisError error(String code, String message, boolean retryable, String next) {
        return new AnalysisError(code, message, retryable, next);
    }
    private static boolean isDml(String type) {
        return "INSERT".equals(type) || "UPDATE".equals(type) || "DELETE".equals(type);
    }
    private static CostLevel parseCost(String value) {
        try { return CostLevel.valueOf(value); } catch (Exception ignored) { return CostLevel.MEDIUM; }
    }
    private static String text(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsString() : "";
    }
    private static boolean isMissingSessionError(Exception thrown) {
        Throwable root = thrown;
        while (root != null) {
            if (root instanceof BackendClient.BackendException backend) {
                String message = root.getMessage() == null ? "" : root.getMessage();
                return backend.status() == 404 && message.contains("会话不存在");
            }
            root = root.getCause();
        }
        return false;
    }
    private static boolean bool(JsonObject object, String field) {
        try { return object.has(field) && object.get(field).getAsBoolean(); }
        catch (RuntimeException ignored) { return false; }
    }

    @Override
    public void dispose() {
        if (stream != null) stream.abort();
        executor.shutdownNow();
    }
}
