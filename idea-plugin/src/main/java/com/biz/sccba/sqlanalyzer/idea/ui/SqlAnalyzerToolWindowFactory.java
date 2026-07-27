package com.biz.sccba.sqlanalyzer.idea.ui;

import com.biz.sccba.sqlanalyzer.idea.client.BackendClient;
import com.biz.sccba.sqlanalyzer.idea.settings.ProjectAnalyzerSettings;
import com.biz.sccba.sqlanalyzer.idea.settings.TokenStore;
import com.biz.sccba.sqlanalyzer.idea.mybatis.MyBatisMapperWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/**
 * Tool Window (development-guide §8.2): tabs for the AG-UI stream, the scenario matrix and
 * structured recommendations with one-click accept/reject (no manual recommendation ids).
 */
public final class SqlAnalyzerToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        Panel panel = new Panel(project);
        toolWindow.getContentManager().addContent(ContentFactory.getInstance().createContent(panel.root, "分析", false));
        Disposer.register(toolWindow.getDisposable(), panel);
    }

    private static final class Panel implements AnalysisUiBridge.Listener, Disposable {
        private final Project project;
        private final ProjectAnalyzerSettings settings;
        private final TokenStore tokens = TokenStore.getInstance();
        private final AnalysisUiBridge bridge;

        private final JPanel root = new JPanel(new BorderLayout(8, 8));
        private final JTextField endpoint = new JTextField();
        private final JPasswordField token = new JPasswordField();
        private final JTextField datasourceProfile = new JTextField();
        private final JTextArea stream = new JTextArea();
        private final JTextArea report = new JTextArea();
        private final JBTable scenarioTable = new JBTable();
        private final JLabel status = new JBLabel("未连接");
        private final DefaultListModel<RecommendationItem> recommendationModel = new DefaultListModel<>();
        private final JList<RecommendationItem> recommendationList = new JList<>(recommendationModel);
        private volatile String activeRunId = "";

        private record RecommendationItem(String id, String text) {
            @Override public String toString() { return text; }
        }

        private Panel(Project project) {
            this.project = project;
            this.settings = ProjectAnalyzerSettings.getInstance(project);
            this.bridge = AnalysisUiBridge.getInstance(project);
            bridge.register(this);

            endpoint.setText(settings.endpoint());
            token.setText(tokens.token());
            datasourceProfile.setText(settings.datasourceProfileId());
            stream.setEditable(false);
            stream.setLineWrap(true);
            stream.setWrapStyleWord(true);
            report.setEditable(false);
            report.setLineWrap(true);
            report.setWrapStyleWord(true);
            recommendationList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                              boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    setText("<html><body style='width:380px;word-wrap:break-word'>" + ((RecommendationItem) value).text() + "</body></html>");
                    return this;
                }
            });

            JPanel config = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 2, 2, 2); c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
            c.gridx = 0; c.gridy = 0; config.add(new JBLabel("后端地址"), c);
            c.gridx = 1; config.add(endpoint, c);
            c.gridx = 0; c.gridy = 1; config.add(new JBLabel("Token"), c);
            c.gridx = 1; config.add(token, c);
            c.gridx = 0; c.gridy = 2; config.add(new JBLabel("数据源 ID"), c);
            c.gridx = 1; config.add(datasourceProfile, c);

            JButton apply = new JButton("申请 Token");
            JButton analyzeSelection = new JButton("分析编辑器选中内容");
            JButton uploadMapper = new JButton("上传当前 Mapper");
            JButton loadHistory = new JButton("加载会话历史");
            JButton cancelRun = new JButton("取消当前 Run");
            JButton acceptRecommendation = new JButton("接受选中建议");
            JButton rejectRecommendation = new JButton("拒绝选中建议");
            apply.addActionListener(e -> issueToken());
            analyzeSelection.addActionListener(e -> analyzeSelection());
            uploadMapper.addActionListener(e -> uploadMapper());
            loadHistory.addActionListener(e -> loadHistory());
            cancelRun.addActionListener(e -> cancelRun());
            acceptRecommendation.addActionListener(e -> decideSelected("ACCEPTED"));
            rejectRecommendation.addActionListener(e -> decideSelected("REJECTED"));

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
            actions.add(apply); actions.add(analyzeSelection); actions.add(uploadMapper);
            actions.add(loadHistory); actions.add(cancelRun); actions.add(status);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("流式事件", new JBScrollPane(stream));
            tabs.addTab("场景矩阵", new JBScrollPane(scenarioTable));
            JPanel recPanel = new JPanel(new BorderLayout());
            JSplitPane reportAndRecommendations = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    new JBScrollPane(report), new JBScrollPane(recommendationList));
            reportAndRecommendations.setResizeWeight(0.6);
            recPanel.add(reportAndRecommendations, BorderLayout.CENTER);
            JPanel recActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
            recActions.add(acceptRecommendation); recActions.add(rejectRecommendation);
            recPanel.add(recActions, BorderLayout.SOUTH);
            tabs.addTab("报告", recPanel);

            root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            root.add(config, BorderLayout.NORTH);
            root.add(tabs, BorderLayout.CENTER);
            root.add(actions, BorderLayout.SOUTH);
        }

        // ---- AnalysisUiBridge.Listener (all on EDT) ----

        @Override public void onStreamText(String text) { stream.append(text); stream.setCaretPosition(stream.getDocument().getLength()); }
        @Override public void onStatus(String statusText) { status.setText(statusText); }

        @Override public void onScenarioMatrix(String planJson) {
            var rows = ScenarioMatrixModel.rows(planJson);
            scenarioTable.setModel(new javax.swing.table.DefaultTableModel(
                    ScenarioMatrixModel.tableData(rows), ScenarioMatrixModel.COLUMNS) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            });
        }

        @Override public void onRecommendations(String json) { renderRecommendations(json); }
        @Override public void onReport(String json) { report.setText(ReportFormatter.format(json)); report.setCaretPosition(0); }
        @Override public void onRunStarted(String runId) { activeRunId = runId == null ? "" : runId; }

        @Override public void onRunFinished() { activeRunId = ""; loadRecommendations(); }

        // ---- actions ----

        private void issueToken() {
            saveSettings();
            run("正在申请 Token...", () -> new BackendClient(settings.endpoint(), "").applyToken("idea-" + project.getName()), tokenValue -> {
                tokens.token(tokenValue); token.setText(tokenValue); status.setText("Token 已保存");
                appendStream("Token 申请成功，已存入 PasswordSafe。\n");
            });
        }

        private void analyzeSelection() {
            saveSettings();
            if (tokens.token().isBlank()) { appendStream("请先申请 Token。\n"); return; }
            Editor editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
            String selected = editor == null ? "" : editor.getSelectionModel().getSelectedText();
            if (selected == null || selected.isBlank()) {
                appendStream("请先在编辑器中选中 SQL 或 MyBatis 片段（或对 statement 标签使用右键菜单“分析 SQL 性能”）。\n");
                return;
            }
            BackendClient client = new BackendClient(settings.endpoint(), tokens.token());
            run("正在创建分析会话...", () -> {
                String sessionId = settings.sessionId();
                if (sessionId.isBlank()) {
                    sessionId = client.createSession("IDEA SQL 分析");
                    settings.sessionId(sessionId);
                }
                return sessionId;
            }, sessionId -> streamSimpleRun(client, sessionId, selected));
        }

        /** Ad-hoc text analysis via AG-UI streaming (no statement context). */
        private void streamSimpleRun(BackendClient client, String sessionId, String content) {
            status.setText("Agent 运行中…");
            appendStream("\n--- 分析开始 ---\n");
            CompletableFuture.runAsync(() -> {
                try {
                    String runInput = "{\"threadId\":\"" + sessionId + "\",\"runId\":\"run_" + java.util.UUID.randomUUID()
                            + "\",\"messages\":[{\"id\":\"msg_" + java.util.UUID.randomUUID()
                            + "\",\"role\":\"user\",\"content\":\"" + escape(content) + "\"}]}";
                    var sse = new com.biz.sccba.sqlanalyzer.idea.client.AguiSseClient(settings.endpoint(), tokens.token());
                    sse.runAndStream(runInput, (id, type, json) -> {
                        String text = com.biz.sccba.sqlanalyzer.idea.client.AguiEventRenderer.render(type, json);
                        if (text != null && !text.isEmpty()) onStreamText(text);
                        if (com.biz.sccba.sqlanalyzer.idea.client.AguiEventRenderer.isTerminal(type)) {
                            onRunFinished();
                            return true;
                        }
                        return false;
                    });
                    onStatus(sse.isAborted() ? "已取消" : "分析完成");
                } catch (Exception ex) {
                    onStreamText("\n分析失败：" + rootCause(ex).getMessage() + "\n");
                    onStatus("分析失败");
                }
            });
        }

        private void uploadMapper() {
            saveSettings();
            Editor editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null || editor.getVirtualFile() == null) { appendStream("请先打开 MyBatis Mapper XML。\n"); return; }
            project.getService(MyBatisMapperWatcher.class).uploadNow(editor.getVirtualFile());
            status.setText("Mapper 已加入上传队列");
        }

        private void cancelRun() {
            saveSettings();
            String runId = activeRunId;
            if (runId.isBlank() || tokens.token().isBlank()) {
                appendStream("当前没有可取消的 Run。\n");
                return;
            }
            run("正在取消 Run...", () -> new BackendClient(settings.endpoint(), tokens.token()).cancelRun(runId),
                    result -> { activeRunId = ""; status.setText("Run 已取消"); appendStream("Run " + runId + "：" + result + "\n"); });
        }

        private void loadRecommendations() {
            saveSettings();
            if (tokens.token().isBlank() || settings.sessionId().isBlank()) {
                appendStream("请先申请 Token 并创建分析会话。\n");
                return;
            }
            run("正在加载优化建议...", () -> new BackendClient(settings.endpoint(), tokens.token()).recommendations(settings.sessionId()),
                    this::renderRecommendations);
        }

        private void renderRecommendations(String json) {
            recommendationModel.clear();
            for (RecommendationFormatter.RecItem item : RecommendationFormatter.items(json)) {
                recommendationModel.addElement(new RecommendationItem(item.id(), item.text()));
            }
            status.setText("建议已加载");
            if (!recommendationModel.isEmpty()) {
                appendStream("\n--- 优化建议 ---\n" + RecommendationFormatter.format(json) + "\n");
            }
        }

        private void decideSelected(String decision) {
            saveSettings();
            RecommendationItem selected = recommendationList.getSelectedValue();
            if (selected == null || selected.id().isBlank()) {
                appendStream("请先在列表中选中一条建议。\n");
                return;
            }
            String reason = "";
            if ("REJECTED".equals(decision)) {
                reason = Messages.showInputDialog(project, "请填写拒绝原因：", "拒绝建议", null);
                if (reason == null || reason.isBlank()) {
                    appendStream("拒绝建议时必须填写原因。\n");
                    return;
                }
            }
            String finalReason = reason;
            run("正在提交建议反馈...", () -> {
                new BackendClient(settings.endpoint(), tokens.token())
                        .decideRecommendation(selected.id(), decision, "IDEA", finalReason);
                return decision;
            }, result -> { status.setText("反馈已保存"); appendStream("建议 " + selected.id() + " 已标记为 " + result + "。\n"); });
        }

        private void loadHistory() {
            saveSettings();
            if (tokens.token().isBlank() || settings.sessionId().isBlank()) {
                appendStream("请先申请 Token 并创建分析会话。\n");
                return;
            }
            run("正在加载会话历史...", () -> new BackendClient(settings.endpoint(), tokens.token()).messages(settings.sessionId()),
                    result -> { status.setText("历史已加载"); appendStream("\n--- 会话历史 ---\n" + ConversationFormatter.format(result) + "\n"); });
        }

        private <T> void run(String message, ThrowingSupplier<T> supplier, java.util.function.Consumer<T> success) {
            status.setText(message);
            CompletableFuture.supplyAsync(() -> {
                try { return supplier.get(); } catch (Exception e) { throw new RuntimeException(e); }
            }).whenComplete((value, error) -> ApplicationManager.getApplication().invokeLater(() -> {
                if (error != null) { status.setText("失败"); appendStream("请求失败：" + rootCause(error).getMessage() + "\n"); }
                else success.accept(value);
            }));
        }

        private void saveSettings() {
            settings.endpoint(endpoint.getText());
            settings.datasourceProfileId(datasourceProfile.getText());
            tokens.token(new String(token.getPassword()));
        }
        private void appendStream(String text) { onStreamText(text); }
        private static Throwable rootCause(Throwable t) { while (t.getCause() != null) t = t.getCause(); return t; }

        private static String escape(String value) {
            return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r");
        }

        @FunctionalInterface private interface ThrowingSupplier<T> { T get() throws Exception; }

        @Override public void dispose() { bridge.unregister(this); }
    }
}
