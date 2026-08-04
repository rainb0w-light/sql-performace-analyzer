package com.biz.sccba.sqlanalyzer.idea.ui;

import com.biz.sccba.sqlanalyzer.idea.client.BackendClient;
import com.biz.sccba.sqlanalyzer.idea.contract.PluginApiDtos.BoundSqlPreview;
import com.biz.sccba.sqlanalyzer.idea.navigation.ReportNavigation;
import com.biz.sccba.sqlanalyzer.idea.report.*;
import com.biz.sccba.sqlanalyzer.idea.scenario.MainScenarioModel;
import com.biz.sccba.sqlanalyzer.idea.settings.ProjectAnalyzerSettings;
import com.biz.sccba.sqlanalyzer.idea.settings.TokenStore;
import com.biz.sccba.sqlanalyzer.idea.state.AnalysisCoordinator;
import com.biz.sccba.sqlanalyzer.idea.state.AnalysisState;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.*;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.google.gson.*;

/** State-driven P1 Tool Window with report, scenario, evidence and run-log tabs. */
public final class SqlAnalyzerToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        Panel panel = new Panel(project);
        toolWindow.getContentManager().addContent(
                ContentFactory.getInstance().createContent(panel.root, "", false));
        Disposer.register(toolWindow.getDisposable(), panel);
    }

    static final class Panel implements AnalysisUiBridge.Listener, Disposable {
        final JBPanel<?> root = new JBPanel<>(new BorderLayout());
        private final Project project;
        private final ProjectAnalyzerSettings settings;
        private final TokenStore tokenStore;
        private final AnalysisCoordinator coordinator;
        private final AnalysisUiBridge bridge;
        private final ReportNavigation navigation = new ReportNavigation();

        private final JBLabel statementLabel = new JBLabel("未选择 MyBatis statement");
        private final JBLabel datasourceLabel = new JBLabel("DataSource: 未解析");
        private final JBLabel knowledgeLabel = new JBLabel("Knowledge: 自动选择已发布版本");
        private final JBLabel profileLabel = new JBLabel("Profile: 最新完成快照");
        private final JBLabel moduleLabel = new JBLabel("Module: —");
        private final JBLabel runLabel = new JBLabel("Run: —");
        private final JBLabel staleBanner = new JBLabel("报告已过期：当前上下文指纹与报告不一致",
                AllIcons.General.Warning, SwingConstants.LEFT);
        private final JBLabel readOnlyBanner = new JBLabel(
                "只读静态分析：不会执行 DML、DDL 或 EXPLAIN ANALYZE，也不会修改 Mapper。",
                AllIcons.General.Warning, SwingConstants.LEFT);
        private final JBLabel stateLabel = new JBLabel("就绪");
        private final JBLabel connectionLabel = new JBLabel("SSE: 未连接");
        private final JBLabel cursorLabel = new JBLabel("Last-Event-ID: —");

        private final JTabbedPane tabs = new JTabbedPane();
        private final JBPanel<?> reportTab = new JBPanel<>(new BorderLayout());
        private final JBPanel<?> scenarioTab = new JBPanel<>(new BorderLayout());
        private final JBPanel<?> evidenceTab = new JBPanel<>(new BorderLayout());
        private final JBPanel<?> logTab = new JBPanel<>(new BorderLayout());
        private final JBPanel<?> mainScenarioSlot = new JBPanel<>(new BorderLayout());
        private MainScenarioPanel mainScenarioPanel;

        private final JBLabel summarySeverity = new JBLabel("尚无报告");
        private final JBLabel summaryHeadline = new JBLabel(
                "从 Mapper statement 的 Gutter、右键菜单或 Intention 发起分析。");
        private final DefaultListModel<ReportViewModel.Risk> risks = new DefaultListModel<>();
        private final JBList<ReportViewModel.Risk> riskList = new JBList<>(risks);
        private final DefaultListModel<ReportViewModel.Recommendation> recommendations = new DefaultListModel<>();
        private final JBList<ReportViewModel.Recommendation> recommendationList = new JBList<>(recommendations);
        private final DefaultListModel<String> limits = new DefaultListModel<>();
        private final JBList<String> limitList = new JBList<>(limits);

        private final JBTable scenarioTable = new JBTable();
        private final JBTextArea scenarioDetail = readonlyArea();
        private final JButton fullPlanButton = new JButton("查看完整执行计划");
        private final DefaultListModel<ReportViewModel.Evidence> evidence = new DefaultListModel<>();
        private final JBList<ReportViewModel.Evidence> evidenceList = new JBList<>(evidence);
        private final JBTextArea evidenceDetail = readonlyArea();
        private final DefaultListModel<String> logs = new DefaultListModel<>();
        private final JBList<String> logList = new JBList<>(logs);

        private final JBPanel<?> guardPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
        private final JBTextArea guardMessage = readonlyArea();
        private final ComboBox<BackendClient.DatasourceProfile> datasourceChoices = new ComboBox<>();
        private final JBCheckBox rememberModule = new JBCheckBox("记住为当前 module 默认");
        private final JButton chooseDatasource = new JButton("使用此数据源");
        private final JButton confirmReview = new JButton("确认并继续分析");
        private final JButton cancelButton = new JButton("取消");
        private final JButton connectButton = new JButton("连接后端");
        private final ComboBox<String> reanalysisMode = new ComboBox<>(
                new String[]{"沿用上次参数", "刷新上下文", "切换数据源"});
        private final JButton reanalyzeButton = new JButton("重新分析");
        private final ComboBox<String> exportFormat = new ComboBox<>(
                new String[]{"Markdown", "标准 JSON"});
        private final JButton exportButton = new JButton("导出");
        private final JButton transientRulesButton = new JButton("本次分析补充…");
        private final JButton locateMapperButton = new JButton("在编辑器中定位");
        private final JBLabel authStatus = new JBLabel("Token: 未检查");

        private AnalysisState state = AnalysisState.idle();
        private ReportViewModel reportModel;
        private String rawReport = "";
        private List<BackendClient.DatasourceProfile> candidates = List.of();
        private final Map<Integer, String> visibleScenarioIds = new HashMap<>();

        private Panel(Project project) {
            this.project = project;
            this.settings = ProjectAnalyzerSettings.getInstance(project);
            this.tokenStore = TokenStore.getInstance();
            this.coordinator = AnalysisCoordinator.getInstance(project);
            this.bridge = AnalysisUiBridge.getInstance(project);
            bridge.register(this);

            root.setBorder(JBUI.Borders.empty(8));
            root.add(contextArea(), BorderLayout.NORTH);
            root.add(mainArea(), BorderLayout.CENTER);
            root.add(statusBar(), BorderLayout.SOUTH);
            configureActions();
            configureAccessibility();
            onState(coordinator.state());
        }

        private JComponent contextArea() {
            JBPanel<?> context = new JBPanel<>(new BorderLayout());
            // Context identifiers are diagnostic details, not primary navigation. Keep them in
            // the model for status/tooltips, but do not spend the most prominent vertical space
            // on a six-field technical summary.
            context.add(toolbar(), BorderLayout.NORTH);

            JBPanel<?> banners = new JBPanel<>();
            banners.setLayout(new BoxLayout(banners, BoxLayout.Y_AXIS));
            staleBanner.setVisible(false);
            readOnlyBanner.setVisible(false);
            guardMessage.setRows(3);
            guardMessage.setColumns(58);
            guardMessage.setOpaque(false);
            guardMessage.getAccessibleContext().setAccessibleName("分析前安全条件");
            guardPanel.add(guardMessage);
            guardPanel.add(datasourceChoices);
            guardPanel.add(rememberModule);
            guardPanel.add(chooseDatasource);
            guardPanel.add(confirmReview);
            guardPanel.setVisible(false);
            banners.add(staleBanner);
            banners.add(readOnlyBanner);
            banners.add(guardPanel);
            context.add(banners, BorderLayout.SOUTH);
            return context;
        }

        private JComponent toolbar() {
            JBPanel<?> toolbar = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
            JButton settingsButton = new JButton(AllIcons.General.Settings);
            settingsButton.setToolTipText("项目设置");
            settingsButton.addActionListener(event ->
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, "SQL Analyzer"));
            toolbar.add(connectButton);
            toolbar.add(authStatus);
            toolbar.add(transientRulesButton);
            toolbar.add(locateMapperButton);
            toolbar.add(reanalysisMode);
            toolbar.add(reanalyzeButton);
            toolbar.add(exportFormat);
            toolbar.add(exportButton);
            toolbar.add(cancelButton);
            toolbar.add(settingsButton);
            return toolbar;
        }

        private JComponent mainArea() {
            tabs.addTab("报告", reportContent());
            tabs.addTab("场景矩阵", scenarioContent());
            tabs.addTab("证据", evidenceContent());
            tabs.addTab("运行日志", logContent());
            return tabs;
        }

        private JComponent reportContent() {
            JBPanel<?> cards = new JBPanel<>();
            cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
            JBPanel<?> summary = card("结论摘要");
            summary.add(summarySeverity);
            summary.add(Box.createHorizontalStrut(JBUI.scale(12)));
            summary.add(summaryHeadline);
            cards.add(summary);
            cards.add(listCard("关键风险", riskList));
            cards.add(listCard("优化建议", recommendationList));
            cards.add(listCard("限制与缺失证据", limitList));
            reportTab.add(new JBScrollPane(cards), BorderLayout.CENTER);
            JBPanel<?> feedback = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
            JButton accept = new JButton("接受选中建议");
            JButton reject = new JButton("拒绝选中建议");
            accept.addActionListener(event -> decideRecommendation("ACCEPTED"));
            reject.addActionListener(event -> decideRecommendation("REJECTED"));
            JButton history = new JButton("筛选历史报告");
            history.addActionListener(event -> loadHistory());
            feedback.add(accept); feedback.add(reject); feedback.add(history);
            reportTab.add(feedback, BorderLayout.SOUTH);
            return reportTab;
        }

        private JComponent scenarioContent() {
            scenarioTab.add(mainScenarioSlot, BorderLayout.NORTH);
            JBSplitter splitter = new JBSplitter(false, .55f);
            splitter.setFirstComponent(new JBScrollPane(scenarioTable));
            JBPanel<?> detail = new JBPanel<>(new BorderLayout());
            detail.add(new TitledSeparator("场景详情"), BorderLayout.NORTH);
            detail.add(new JBScrollPane(scenarioDetail), BorderLayout.CENTER);
            JBPanel<?> actions = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
            JButton evidenceButton = new JButton("查看证据");
            evidenceButton.addActionListener(event -> openScenarioEvidence());
            fullPlanButton.setEnabled(false);
            fullPlanButton.addActionListener(event -> openFullPlan());
            actions.add(evidenceButton); actions.add(fullPlanButton);
            detail.add(actions, BorderLayout.SOUTH);
            splitter.setSecondComponent(detail);
            scenarioTab.add(splitter, BorderLayout.CENTER);
            scenarioTable.getSelectionModel().addListSelectionListener(this::scenarioSelected);
            return scenarioTab;
        }

        private JComponent evidenceContent() {
            JBSplitter splitter = new JBSplitter(false, .40f);
            splitter.setFirstComponent(new JBScrollPane(evidenceList));
            JBPanel<?> detail = new JBPanel<>(new BorderLayout());
            detail.add(new TitledSeparator("证据详情"), BorderLayout.NORTH);
            detail.add(new JBScrollPane(evidenceDetail), BorderLayout.CENTER);
            JBPanel<?> actions = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
            JButton copyLocator = new JButton("复制来源坐标");
            copyLocator.addActionListener(event -> copyEvidenceLocator());
            JButton back = new JButton("返回");
            back.addActionListener(event -> navigateBack());
            actions.add(copyLocator); actions.add(back);
            detail.add(actions, BorderLayout.SOUTH);
            splitter.setSecondComponent(detail);
            evidenceList.addListSelectionListener(event -> evidenceSelected());
            evidenceTab.add(splitter, BorderLayout.CENTER);
            return evidenceTab;
        }

        private JComponent logContent() {
            logList.setEmptyText("结构化阶段、工具、连接和错误事件将在这里显示。");
            logTab.add(new JBScrollPane(logList), BorderLayout.CENTER);
            return logTab;
        }

        private JComponent statusBar() {
            JBPanel<?> bar = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
            bar.add(stateLabel);
            bar.add(new JSeparator(SwingConstants.VERTICAL));
            bar.add(connectionLabel);
            bar.add(new JSeparator(SwingConstants.VERTICAL));
            bar.add(cursorLabel);
            return bar;
        }

        private void configureActions() {
            connectButton.addActionListener(event -> connectBackend());
            chooseDatasource.addActionListener(event -> {
                BackendClient.DatasourceProfile selected =
                        (BackendClient.DatasourceProfile) datasourceChoices.getSelectedItem();
                if (selected != null) coordinator.chooseDatasource(selected.id(), rememberModule.isSelected());
            });
            confirmReview.addActionListener(event -> coordinator.confirmReview());
            cancelButton.addActionListener(event -> coordinator.cancel());
            transientRulesButton.addActionListener(event -> new TransientRulesDialog(project, List.of(),
                    coordinator::replaceTransientRules, coordinator::previewTransientRules).show());
            locateMapperButton.addActionListener(event -> locateMapper());
            reanalyzeButton.addActionListener(event -> {
                int selected = reanalysisMode.getSelectedIndex();
                AnalysisCoordinator.ReanalysisMode mode = selected == 0
                        ? AnalysisCoordinator.ReanalysisMode.REUSE_PARAMETERS
                        : selected == 1 ? AnalysisCoordinator.ReanalysisMode.REFRESH_CONTEXT
                        : AnalysisCoordinator.ReanalysisMode.SWITCH_DATASOURCE;
                BackendClient.DatasourceProfile datasource =
                        (BackendClient.DatasourceProfile) datasourceChoices.getSelectedItem();
                coordinator.reanalyze(mode, datasource == null ? "" : datasource.id());
            });
            exportButton.addActionListener(event -> exportReport());
            riskList.addListSelectionListener(event -> riskSelected());
        }

        private void configureAccessibility() {
            statementLabel.getAccessibleContext().setAccessibleName("当前 MyBatis statement");
            datasourceLabel.getAccessibleContext().setAccessibleName("当前数据源");
            stateLabel.getAccessibleContext().setAccessibleName("Run 业务状态");
            connectionLabel.getAccessibleContext().setAccessibleName("SSE 连接状态");
            tabs.getAccessibleContext().setAccessibleName("SQL Analyzer 主视图");
            cancelButton.setMnemonic('C');
            reanalyzeButton.setMnemonic('R');
            exportButton.setMnemonic('E');
        }

        @Override
        public void onState(AnalysisState newState) {
            this.state = newState;
            AnalysisState.StatementContext context = newState.statement();
            statementLabel.setText(context.statementId().isBlank() ? "未选择 MyBatis statement"
                    : context.mapperPath() + " / " + context.statementId() + " · " + context.statementType());
            datasourceLabel.setText("DataSource: " + (context.datasourceDisplayName().isBlank()
                    ? "未解析" : context.datasourceDisplayName() + " · " + context.datasourceBindingSource()));
            knowledgeLabel.setText("Knowledge: " + fallback(context.knowledgeVersion(), "等待服务端上下文"));
            profileLabel.setText("Profile: " + fallback(context.profileSnapshotId(), "等待服务端上下文"));
            moduleLabel.setText("Module: " + fallback(context.moduleName(), "—"));
            runLabel.setText("Run: " + fallback(newState.run().runId(), "—")
                    + " · " + newState.businessState());
            readOnlyBanner.setVisible(context.readOnlyStaticAnalysis());
            stateLabel.setText(stateText(newState));
            connectionLabel.setText("SSE: " + newState.connectionState());
            cursorLabel.setText("Last-Event-ID: " + fallback(newState.lastEventId(), "—"));
            cancelButton.setEnabled(newState.run().cancellable()
                    && Set.of(AnalysisState.BusinessState.QUEUED, AnalysisState.BusinessState.RUNNING,
                    AnalysisState.BusinessState.CANCELLING, AnalysisState.BusinessState.PROJECTING)
                    .contains(newState.businessState()));
            reanalyzeButton.setEnabled(Set.of(AnalysisState.BusinessState.COMPLETED,
                    AnalysisState.BusinessState.CANCELLED, AnalysisState.BusinessState.FAILED,
                    AnalysisState.BusinessState.PROJECTION_FAILED).contains(newState.businessState()));
            exportButton.setEnabled(!rawReport.isBlank());
            guardPanel.setVisible(newState.businessState() == AnalysisState.BusinessState.NEEDS_DATASOURCE
                    || newState.businessState() == AnalysisState.BusinessState.AWAITING_REVIEW);
            boolean datasourceGuard = newState.businessState() == AnalysisState.BusinessState.NEEDS_DATASOURCE;
            datasourceChoices.setVisible(datasourceGuard);
            rememberModule.setVisible(datasourceGuard);
            chooseDatasource.setVisible(datasourceGuard);
            confirmReview.setVisible(newState.businessState() == AnalysisState.BusinessState.AWAITING_REVIEW
                    && !newState.hasBlockingGuards());
            guardMessage.setText(newState.guards().isEmpty() ? "" : guardSummary(newState.guards()));
            if (newState.businessState() == AnalysisState.BusinessState.AUTH_REQUIRED) {
                connectButton.setVisible(true);
                authStatus.setText("Token: 需要重新认证");
            }
            updateStaleBanner();
        }

        @Override
        public void onMainScenario(MainScenarioModel model) {
            mainScenarioPanel = new MainScenarioPanel(model, coordinator::previewBoundSql,
                    coordinator::confirmMainScenario, coordinator::refreshSuggestions,
                    coordinator::cancelPreparation);
            mainScenarioSlot.removeAll();
            mainScenarioSlot.add(mainScenarioPanel, BorderLayout.CENTER);
            mainScenarioSlot.revalidate();
            mainScenarioSlot.repaint();
            tabs.setSelectedComponent(scenarioTab);
        }

        @Override
        public void onBoundSqlPreview(BoundSqlPreview preview) {
            if (mainScenarioPanel != null) mainScenarioPanel.showPreview(preview);
        }

        @Override
        public void onDatasourceCandidates(List<BackendClient.DatasourceProfile> profiles) {
            candidates = profiles == null ? List.of() : List.copyOf(profiles);
            datasourceChoices.removeAllItems();
            candidates.forEach(datasourceChoices::addItem);
            datasourceChoices.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                        boolean selected, boolean focused) {
                    super.getListCellRendererComponent(list, value, index, selected, focused);
                    if (value instanceof BackendClient.DatasourceProfile profile) {
                        setText(profile.name() + " · " + profile.dialect() + " · "
                                + fallback(profile.schemaName(), "schema 未提供")
                                + " · Profile " + fallback(profile.latestProfileAt(), "时间未知"));
                    }
                    return this;
                }
            });
        }

        @Override
        public void onReportModel(ReportViewModel model, String rawJson) {
            reportModel = model;
            rawReport = rawJson;
            summarySeverity.setText(model.severity() + " · 置信度 "
                    + String.format(Locale.ROOT, "%.2f", model.confidence()));
            summaryHeadline.setText(model.headline());
            replace(risks, model.risks());
            replace(recommendations, model.recommendations());
            replace(limits, model.limits());
            renderScenarios(model.scenarios());
            replace(evidence, model.evidence());
            tabs.setSelectedComponent(reportTab);
            exportButton.setEnabled(true);
            updateStaleBanner();
        }

        @Override
        public void onScenarioMatrix(String planJson) {
            try {
                JsonObject root = JsonParser.parseString(planJson).getAsJsonObject();
                JsonArray items = root.has("scenarios") && root.get("scenarios").isJsonArray()
                        ? root.getAsJsonArray("scenarios") : new JsonArray();
                visibleScenarioIds.clear();
                Object[][] rows = new Object[items.size()][7];
                boolean[] locked = new boolean[items.size()];
                for (int index = 0; index < items.size(); index++) {
                    JsonObject item = items.get(index).getAsJsonObject();
                    String scenarioId = jsonText(item, "scenarioId");
                    visibleScenarioIds.put(index, scenarioId);
                    boolean required = jsonBool(item, "required");
                    boolean main = jsonBool(item, "mainPath");
                    locked[index] = required || main;
                    rows[index] = new Object[]{Boolean.TRUE, String.valueOf(index + 1),
                            jsonText(item, "name"), jsonText(item, "description"),
                            jsonText(item, "source"), jsonArrayText(item, "coverageGoals"),
                            required ? (main ? "必选 · 主路径" : "必选") : "可选"};
                }
                scenarioTable.setModel(new DefaultTableModel(rows,
                        new Object[]{"包含", "编号", "场景", "场景说明", "来源", "覆盖目标", "约束"}) {
                    @Override public Class<?> getColumnClass(int column) {
                        return column == 0 ? Boolean.class : String.class;
                    }
                    @Override public boolean isCellEditable(int row, int column) {
                        return column == 0 && !locked[row];
                    }
                    @Override public void setValueAt(Object value, int row, int column) {
                        if (column != 0) return;
                        boolean include = Boolean.TRUE.equals(value);
                        String id = visibleScenarioIds.getOrDefault(row, "");
                        String reason = "";
                        if (!include) {
                            reason = Messages.showInputDialog(project,
                                    "排除场景原因（写入本 Run 审计）", "排除场景", null);
                            if (reason == null || reason.isBlank()) return;
                        }
                        if (coordinator.includeScenario(id, include, reason)) {
                            super.setValueAt(value, row, column);
                        }
                    }
                });
            } catch (RuntimeException error) {
                onStreamText("场景规划投影失败：" + error.getMessage());
            }
        }

        @Override
        public void onStreamText(String text) {
            if (text == null || text.isBlank()) return;
            for (String line : text.split("\\R")) if (!line.isBlank()) logs.addElement(line);
            if (!logs.isEmpty()) logList.ensureIndexIsVisible(logs.size() - 1);
        }

        @Override public void onStatus(String status) {
            if (status != null && !status.isBlank()) stateLabel.setText(status);
        }

        private void renderScenarios(List<ReportViewModel.Scenario> scenarios) {
            visibleScenarioIds.clear();
            Object[][] rows = new Object[scenarios.size()][6];
            for (int i = 0; i < scenarios.size(); i++) {
                ReportViewModel.Scenario scenario = scenarios.get(i);
                visibleScenarioIds.put(i, scenario.scenarioId());
                rows[i] = new Object[]{String.valueOf(i + 1), scenario.name(), scenario.source(),
                        String.join(", ", scenario.coverageGoals()), scenario.fingerprint(),
                        scenario.hasExplainEvidence() ? "EXPLAIN 可用" : "无 EXPLAIN 证据"};
            }
            scenarioTable.setModel(new DefaultTableModel(rows,
                    new Object[]{"编号", "场景", "来源", "覆盖目标", "指纹", "执行计划"}) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            });
        }

        private void riskSelected() {
            ReportViewModel.Risk risk = riskList.getSelectedValue();
            if (risk == null || risk.riskId().isBlank()) return;
            navigation.go(new ReportNavigation.Target(ReportNavigation.TargetType.RISK, risk.riskId(), ""));
            if (!risk.scenarioIds().isEmpty()) selectScenario(risk.scenarioIds().get(0));
            else if (!risk.evidenceIds().isEmpty()) selectEvidence(risk.evidenceIds().get(0));
        }

        private void scenarioSelected(ListSelectionEvent event) {
            if (event.getValueIsAdjusting() || reportModel == null || scenarioTable.getSelectedRow() < 0) return;
            String id = scenarioIdAt(scenarioTable.getSelectedRow());
            reportModel.scenarios().stream().filter(scenario -> id.equals(scenario.scenarioId())).findFirst()
                    .ifPresent(scenario -> {
                        navigation.go(new ReportNavigation.Target(
                                ReportNavigation.TargetType.SCENARIO, scenario.scenarioId(), ""));
                        scenarioDetail.setText("场景：" + scenario.name()
                                + "\n稳定 ID：" + scenario.scenarioId()
                                + "\n来源：" + scenario.source()
                                + "\n覆盖：" + scenario.coverageGoals()
                                + "\n指纹：" + scenario.fingerprint()
                                + "\n\n脱敏 BoundSql\n" + scenario.boundSql()
                                + "\n\n证据：" + scenario.evidenceIds());
                        fullPlanButton.setEnabled(scenario.hasExplainEvidence());
                        fullPlanButton.setToolTipText(scenario.hasExplainEvidence()
                                ? "打开真实 EXPLAIN 证据" : "没有真实 EXPLAIN 证据，无法查看完整计划");
                    });
        }

        private void evidenceSelected() {
            ReportViewModel.Evidence selected = evidenceList.getSelectedValue();
            if (selected == null) return;
            navigation.go(new ReportNavigation.Target(
                    ReportNavigation.TargetType.EVIDENCE, selected.evidenceId(), selected.locator()));
            evidenceDetail.setText("类型：" + selected.sourceType()
                    + "\n来源：" + selected.sourceId()
                    + "\n版本：" + selected.version()
                    + "\n定位：" + selected.locator()
                    + "\n置信度：" + selected.confidence()
                    + "\n\n" + selected.content());
        }

        private void openScenarioEvidence() {
            if (reportModel == null || scenarioTable.getSelectedRow() < 0) return;
            String id = scenarioIdAt(scenarioTable.getSelectedRow());
            reportModel.scenarios().stream().filter(item -> id.equals(item.scenarioId())).findFirst()
                    .filter(item -> !item.evidenceIds().isEmpty())
                    .ifPresent(item -> selectEvidence(item.evidenceIds().get(0)));
        }

        private void openFullPlan() {
            if (reportModel == null || scenarioTable.getSelectedRow() < 0) return;
            String id = scenarioIdAt(scenarioTable.getSelectedRow());
            reportModel.scenarios().stream().filter(item -> id.equals(item.scenarioId()))
                    .filter(ReportViewModel.Scenario::hasExplainEvidence)
                    .flatMap(item -> item.evidenceIds().stream())
                    .map(evidenceId -> reportModel.evidence().stream()
                            .filter(item -> evidenceId.equals(item.evidenceId())
                                    && "EXPLAIN".equals(item.sourceType())).findFirst().orElse(null))
                    .filter(Objects::nonNull).findFirst()
                    .ifPresent(item -> selectEvidence(item.evidenceId()));
        }

        private void locateMapper() {
            String path = state.statement().mapperPath();
            if (path.isBlank() || project.getBaseDir() == null) return;
            var file = LocalFileSystem.getInstance().findFileByPath(path);
            if (file == null || !VfsUtilCore.isAncestor(project.getBaseDir(), file, false)) {
                Messages.showWarningDialog(project,
                        "Mapper 不存在或不属于当前项目：" + path, "无法定位 Mapper");
                return;
            }
            new OpenFileDescriptor(project, file).navigate(true);
        }

        private void selectScenario(String id) {
            for (int row = 0; row < scenarioTable.getRowCount(); row++) {
                if (id.equals(scenarioIdAt(row))) {
                    tabs.setSelectedComponent(scenarioTab);
                    scenarioTable.setRowSelectionInterval(row, row);
                    return;
                }
            }
        }

        private void selectEvidence(String id) {
            for (int index = 0; index < evidence.size(); index++) {
                if (id.equals(evidence.get(index).evidenceId())) {
                    tabs.setSelectedComponent(evidenceTab);
                    evidenceList.setSelectedIndex(index);
                    return;
                }
            }
        }

        private void navigateBack() {
            ReportNavigation.Target target = navigation.back();
            if (target == null) return;
            if (target.type() == ReportNavigation.TargetType.SCENARIO) selectScenario(target.stableId());
            else if (target.type() == ReportNavigation.TargetType.EVIDENCE) selectEvidence(target.stableId());
            else if (target.type() == ReportNavigation.TargetType.RISK) {
                tabs.setSelectedComponent(reportTab);
                for (int index = 0; index < risks.size(); index++) {
                    if (target.stableId().equals(risks.get(index).riskId())) riskList.setSelectedIndex(index);
                }
            }
        }

        private void copyEvidenceLocator() {
            ReportViewModel.Evidence selected = evidenceList.getSelectedValue();
            if (selected == null || selected.locator().isBlank()) return;
            StringSelection selection = new StringSelection(selected.sourceType() + "/"
                    + selected.sourceId() + "@" + selected.version() + ":" + selected.locator());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        }

        private void connectBackend() {
            connectButton.setEnabled(false);
            authStatus.setText("Token: 正在认证");
            CompletableFuture.supplyAsync(() -> {
                try {
                    BackendClient client = new BackendClient(settings.endpoint(), "");
                    String token = client.applyToken("idea-" + project.getName());
                    tokenStore.token(token);
                    BackendClient.ClientStatus status = new BackendClient(settings.endpoint(), token).clientStatus();
                    return status.expiresAt();
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            }).whenComplete((expiresAt, error) -> ApplicationManager.getApplication().invokeLater(() -> {
                connectButton.setEnabled(true);
                if (error != null) {
                    authStatus.setText("Token: 认证失败");
                    Messages.showErrorDialog(project, rootMessage(error), "SQL Analyzer 认证");
                } else {
                    authStatus.setText(expiresAt == null || expiresAt.isBlank()
                            ? "Token: 有效" : "Token: 有效 · " + expiresAt);
                    if (state.businessState() == AnalysisState.BusinessState.AUTH_REQUIRED) {
                        coordinator.resumeAfterAuthentication();
                    }
                }
            }));
        }

        private void decideRecommendation(String decision) {
            ReportViewModel.Recommendation selected = recommendationList.getSelectedValue();
            if (selected == null || selected.recommendationId().isBlank()) return;
            String reason = "";
            if ("REJECTED".equals(decision)) {
                reason = Messages.showInputDialog(project, "拒绝原因（必填）", "拒绝建议", null);
                if (reason == null || reason.isBlank()) return;
            }
            String finalReason = reason;
            CompletableFuture.runAsync(() -> {
                try {
                    new BackendClient(settings.endpoint(), tokenStore.token()).decideRecommendation(
                            selected.recommendationId(), decision, "IDEA", finalReason);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            }).whenComplete((ignored, error) -> ApplicationManager.getApplication().invokeLater(() -> {
                if (error == null) stateLabel.setText("建议反馈已进入服务端审计 · " + Instant.now());
                else Messages.showErrorDialog(project, rootMessage(error), "建议反馈失败");
            }));
        }

        private void loadHistory() {
            HistoryFilterDialog filterDialog = new HistoryFilterDialog(project, project.getLocationHash(),
                    state.statement().moduleName(), state.statement().statementId(),
                    state.statement().datasourceProfileId());
            if (!filterDialog.showAndGet()) return;
            BackendClient.HistoryFilter filter = filterDialog.filter();
            CompletableFuture.supplyAsync(() -> {
                try {
                    return new BackendClient(settings.endpoint(), tokenStore.token()).reports(filter);
                } catch (Exception error) { throw new RuntimeException(error); }
            }).whenComplete((json, error) -> ApplicationManager.getApplication().invokeLater(() -> {
                if (error != null) Messages.showErrorDialog(project, rootMessage(error), "历史报告加载失败");
                else Messages.showInfoMessage(project,
                        "服务端筛选结果已加载。当前 P1 列表投影保留最近 10 条。\n" + json,
                        "历史报告");
            }));
        }

        private void exportReport() {
            if (rawReport.isBlank()) return;
            ReportExportService.Format format = exportFormat.getSelectedIndex() == 0
                    ? ReportExportService.Format.MARKDOWN : ReportExportService.Format.STANDARD_JSON;
            String extension = format == ReportExportService.Format.MARKDOWN ? "md" : "json";
            FileSaverDescriptor descriptor = new FileSaverDescriptor("导出 SQL Analyzer 报告",
                    "导出脱敏标准报告", extension);
            VirtualFileWrapper target = FileChooserFactory.getInstance()
                    .createSaveFileDialog(descriptor, project)
                    .save(project.getBaseDir(), "sql-analysis-" + reportModel.reportId() + "." + extension);
            if (target == null) return;
            try {
                FileUtil.writeToFile(target.getFile(),
                        ReportExportService.export(rawReport, format).getBytes(StandardCharsets.UTF_8));
                stateLabel.setText("报告已导出：" + target.getFile().getName());
            } catch (Exception error) {
                Messages.showErrorDialog(project, error.getMessage(), "导出失败");
            }
        }

        private void updateStaleBanner() {
            if (rawReport.isBlank()) { staleBanner.setVisible(false); return; }
            ContextFingerprint report = ContextFingerprint.fromReport(rawReport);
            ContextFingerprint current = new ContextFingerprint(state.statement().contentHash(),
                    state.statement().statementId(), state.statement().datasourceProfileId(),
                    state.statement().knowledgeVersion(), state.statement().profileSnapshotId());
            staleBanner.setVisible(report.staleComparedWith(current));
        }

        private static JBPanel<?> card(String title) {
            JBPanel<?> panel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
            panel.setBorder(BorderFactory.createTitledBorder(title));
            return panel;
        }

        private static JComponent listCard(String title, JList<?> list) {
            JBPanel<?> panel = new JBPanel<>(new BorderLayout());
            panel.setBorder(BorderFactory.createTitledBorder(title));
            panel.add(new JBScrollPane(list), BorderLayout.CENTER);
            panel.setPreferredSize(new Dimension(JBUI.scale(500), JBUI.scale(130)));
            return panel;
        }

        private static JBTextArea readonlyArea() {
            JBTextArea area = new JBTextArea();
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            return area;
        }

        private static String stateText(AnalysisState state) {
            String base = state.businessState().name();
            if (!state.phase().isBlank()) base += " · " + state.phase();
            if (state.error() != null) {
                base += " · " + state.error().code() + " · " + state.error().message()
                        + " · 下一步：" + state.error().nextAction();
            }
            return base;
        }

        private static String fallback(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        private static String rootMessage(Throwable error) {
            Throwable root = error;
            while (root.getCause() != null) root = root.getCause();
            return root.getMessage() == null ? root.toString() : root.getMessage();
        }

        private String scenarioIdAt(int row) {
            if (row < 0 || row >= scenarioTable.getRowCount()) return "";
            if (visibleScenarioIds.containsKey(row)) return visibleScenarioIds.get(row);
            int column = scenarioTable.getColumnClass(0) == Boolean.class ? 1 : 0;
            return String.valueOf(scenarioTable.getValueAt(row, column));
        }

        private static String jsonText(JsonObject object, String field) {
            return object.has(field) && !object.get(field).isJsonNull()
                    ? object.get(field).getAsString() : "";
        }
        private static String jsonArrayText(JsonObject object, String field) {
            if (!object.has(field) || !object.get(field).isJsonArray()) return "";
            List<String> values = new ArrayList<>();
            for (JsonElement value : object.getAsJsonArray(field)) values.add(value.getAsString());
            return String.join(", ", values);
        }
        private static boolean jsonBool(JsonObject object, String field) {
            try { return object.has(field) && object.get(field).getAsBoolean(); }
            catch (RuntimeException ignored) { return false; }
        }

        private static String guardSummary(List<AnalysisState.Guard> guards) {
            StringBuilder text = new StringBuilder("分析前必须满足的安全条件：");
            for (AnalysisState.Guard guard : guards) {
                text.append("\n- ").append(guardDescription(guard));
                if (guard.blocking()) text.append("（当前阻断分析）");
            }
            return text.toString();
        }

        private static String guardDescription(AnalysisState.Guard guard) {
            String description = switch (guard.type()) {
                case DATASOURCE_MISSING -> "数据源绑定：为当前 statement 选择可访问的数据源，才能生成真实 BoundSql 和只读证据";
                case DATASOURCE_AMBIGUOUS -> "数据源绑定：多个数据源都可能对应当前 statement，需要明确实际目标";
                case DOLLAR_WHITELIST_MISSING -> "动态 SQL 安全：${...} 只能使用显式白名单值，防止未经允许的文本直接拼入 SQL";
                case SCENARIO_OR_COST_LIMIT -> "场景与成本边界：场景数量或分析成本超过当前阈值，需要调整范围或阈值";
                case UNSUPPORTED_LANGUAGE_OR_TYPE -> "可执行性：Mapper 语言或参数类型无法安全绑定，避免生成不可信的 BoundSql";
            };
            return guard.message() == null || guard.message().isBlank()
                    ? description : description + "；服务端提示：" + guard.message();
        }

        private static <T> void replace(DefaultListModel<T> model, List<T> values) {
            model.clear();
            values.forEach(model::addElement);
        }

        @Override public void dispose() { bridge.unregister(this); }
    }
}
