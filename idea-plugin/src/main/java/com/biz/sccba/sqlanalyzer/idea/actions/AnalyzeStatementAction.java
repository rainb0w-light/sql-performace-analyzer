package com.biz.sccba.sqlanalyzer.idea.actions;

import com.biz.sccba.sqlanalyzer.idea.client.AguiEventRenderer;
import com.biz.sccba.sqlanalyzer.idea.client.AguiSseClient;
import com.biz.sccba.sqlanalyzer.idea.client.BackendClient;
import com.biz.sccba.sqlanalyzer.idea.mybatis.MyBatisStatementPsi;
import com.biz.sccba.sqlanalyzer.idea.settings.ProjectAnalyzerSettings;
import com.biz.sccba.sqlanalyzer.idea.settings.TokenStore;
import com.biz.sccba.sqlanalyzer.idea.ui.AnalysisUiBridge;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.PsiJavaFile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * "Analyze SQL performance" entry point on MyBatis statement tags (development-guide §8.1):
 * collects module/namespace/statementId/content hash via PSI, uploads the mapper with content-hash
 * deduplication, starts the canonical asynchronous analysis command, then follows its persisted
 * AG-UI stream into the Tool Window. The user never copies XML, SQL or ids by hand.
 */
public final class AnalyzeStatementAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean visible = false;
        if ((file instanceof XmlFile || file instanceof PsiJavaFile) && editor != null) {
            visible = ReadAction.compute(() ->
                    MyBatisStatementPsi.resolve(e.getProject(), file, editor.getCaretModel().getOffset()) != null);
        }
        e.getPresentation().setEnabledAndVisible(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var project = e.getProject();
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || file == null || editor == null) return;
        startAnalysis(project, editor, file);
    }

    public static void startAnalysis(com.intellij.openapi.project.Project project,
                                     Editor editor, PsiFile file) {
        if (project == null || file == null || editor == null) return;

        AnalysisUiBridge bridge = AnalysisUiBridge.getInstance(project);
        MyBatisStatementPsi.StatementRef ref = ReadAction.compute(() ->
                MyBatisStatementPsi.resolve(project, file, editor.getCaretModel().getOffset()));
        if (ref == null) {
                bridge.showAndDispatch(() -> bridge.status(
                        "请将光标放在 Mapper statement 或可静态解析的 MyBatis 注解方法内"));
            return;
        }
        bridge.showAndDispatch(() -> bridge.status("正在分析 " + ref.namespace() + "." + ref.statementId()));
        CompletableFuture.runAsync(() -> runAnalysis(project, bridge, ref));
    }

    private static void runAnalysis(com.intellij.openapi.project.Project project, AnalysisUiBridge bridge,
                                    MyBatisStatementPsi.StatementRef ref) {
        try {
            ProjectAnalyzerSettings settings = ProjectAnalyzerSettings.getInstance(project);
            String token = TokenStore.getInstance().token();
            if (token.isBlank()) {
                bridge.streamText("请先在 Tool Window 中申请 Token。\n");
                bridge.status("缺少 Token");
                return;
            }
            BackendClient client = new BackendClient(settings.endpoint(), token);

            String sessionId = settings.sessionId();

            // Content-hash dedup: identical mapper content is not re-uploaded.
            String artifactId = settings.lastMapperArtifactId();
            if (artifactId.isBlank() || !ref.contentHash().equals(settings.lastMapperHash())) {
                artifactId = ref.sourceKind() == MyBatisStatementPsi.SourceKind.XML
                        ? client.indexMyBatisMapper(sessionId, ref.mapperXml(), ref.namespace())
                        : client.indexMyBatisAnnotation(sessionId, ref.mapperXml(), ref.namespace(), ref.statementId());
                settings.mapperCache(ref.contentHash(), artifactId);
            }

            String datasourceProfileId = client.resolveDatasourceProfile(
                    settings.datasourceProfileIdForModule(ref.moduleName()));
            settings.bindDatasourceProfile(ref.moduleName(), datasourceProfileId);
            BackendClient.AnalysisHandle handle = client.analyzeStatement(
                    artifactId, ref.statementId(), datasourceProfileId,
                    project.getLocationHash(), ref.moduleName(), sessionId);
            settings.sessionId(handle.sessionId());
            bridge.runStarted(handle.runId());

            AguiSseClient stream = new AguiSseClient(settings.endpoint(), token);
            bridge.status("分析任务已提交：" + handle.runId());
            stream.streamExisting(handle.streamUrl(), handle.runId(), (id, type, json) -> {
                String text = AguiEventRenderer.render(type, json);
                if (text != null && !text.isEmpty()) bridge.streamText(text);
                handleProjectionEvent(client, bridge, handle.sessionId(), type, json);
                if (AguiEventRenderer.isTerminal(type)) {
                    bridge.runFinished();
                    return true;
                }
                return false;
            });
            bridge.status(stream.isAborted() ? "已取消" : "分析完成");
        } catch (Exception ex) {
            Throwable root = ex;
            while (root.getCause() != null) root = root.getCause();
            bridge.streamText("\n分析失败：" + root.getMessage() + "\n");
            bridge.status("分析失败");
        }
    }

    private static void handleProjectionEvent(BackendClient client, AnalysisUiBridge bridge,
                                              String sessionId, String type, String json) {
        if (!"CUSTOM".equals(type)) return;
        try {
            JsonObject event = JsonParser.parseString(json).getAsJsonObject();
            String name = event.has("name") ? event.get("name").getAsString() : "";
            if ("spa.report_ready".equals(name) && event.has("reportId")) {
                String reportId = event.get("reportId").getAsString();
                String report = client.report(reportId);
                bridge.scenarioMatrix(report);
                bridge.report(report);
                bridge.streamText("标准报告已加载：" + reportId + "\n");
            } else if ("spa.recommendations_ready".equals(name)) {
                bridge.recommendations(client.recommendations(sessionId));
            }
        } catch (Exception projectionError) {
            bridge.streamText("投影视图刷新失败：" + projectionError.getMessage() + "\n");
        }
    }
}
