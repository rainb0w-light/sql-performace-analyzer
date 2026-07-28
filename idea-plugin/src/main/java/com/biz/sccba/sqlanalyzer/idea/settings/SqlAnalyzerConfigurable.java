package com.biz.sccba.sqlanalyzer.idea.settings;

import com.biz.sccba.sqlanalyzer.idea.state.AnalysisCoordinator;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/** Settings / Tools / SQL Analyzer. Secrets are status-only and remain in PasswordSafe. */
public final class SqlAnalyzerConfigurable implements SearchableConfigurable {
    private final Project project;
    private final ProjectAnalyzerSettings settings;
    private JBPanel<?> root;
    private JBTextField endpoint;
    private ComboBox<String> mode;
    private JSpinner maxScenarios;
    private ComboBox<String> cost;
    private JBTextField projectDatasource;
    private JSpinner cacheMb;
    private JBLabel tokenStatus;

    public SqlAnalyzerConfigurable(Project project) {
        this.project = project;
        this.settings = ProjectAnalyzerSettings.getInstance(project);
    }

    @Override public @NotNull String getId() { return "tools.sql.analyzer"; }
    @Override public @Nls String getDisplayName() { return "SQL Analyzer"; }

    @Override
    public @Nullable JComponent createComponent() {
        root = new JBPanel<>(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = JBUI.insets(4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        int row = 0;
        addSection("Server", row++, c);
        endpoint = new JBTextField(settings.endpoint());
        addRow("Backend URL", endpoint, row++, c);
        tokenStatus = new JBLabel(TokenStore.getInstance().token().isBlank()
                ? "未配置 · 请在 Tool Window 连接后端" : "已保存在 PasswordSafe");
        addRow("Token", tokenStatus, row++, c);

        addSection("Analysis", row++, c);
        mode = new ComboBox<>(new String[]{"AUTO", "REVIEW"});
        mode.setSelectedItem(settings.executionMode());
        addRow("运行方式", mode, row++, c);
        maxScenarios = new JSpinner(new SpinnerNumberModel(settings.maxScenarios(), 1, 100, 1));
        addRow("最大场景数", maxScenarios, row++, c);
        cost = new ComboBox<>(new String[]{"LOW", "MEDIUM", "HIGH", "EXTREME"});
        cost.setSelectedItem(settings.costThreshold());
        addRow("成本提示阈值", cost, row++, c);

        addSection("Data Sources", row++, c);
        projectDatasource = new JBTextField(settings.datasourceProfileId());
        addRow("Project 默认", projectDatasource, row++, c);
        JBTextArea bindings = new JBTextArea(settings.moduleDatasourceProfiles().toString());
        bindings.setEditable(false);
        addRow("module 绑定（只读摘要）", new JBScrollPane(bindings), row++, c);

        addSection("Local Cache", row++, c);
        cacheMb = new JSpinner(new SpinnerNumberModel(settings.localCacheMegabytes(), 10, 500, 10));
        addRow("展示缓存上限 (MB)", cacheMb, row++, c);
        JButton clear = new JButton("清理本地缓存");
        clear.addActionListener(event -> {
            AnalysisCoordinator.getInstance(project).clearLocalCache();
            clear.setText("本地缓存已清理（服务端报告未删除）");
        });
        addRow("", clear, row++, c);

        addSection("Product Safety（不可配置）", row++, c);
        addRow("", new JBLabel("✓ INSERT/UPDATE/DELETE 始终只做只读静态分析"), row++, c);
        addRow("", new JBLabel("✓ 不执行 DML、DDL 或 EXPLAIN ANALYZE；不自动修改 Mapper"), row++, c);
        c.gridy = row; c.gridx = 0; c.gridwidth = 2; c.weighty = 1; c.fill = GridBagConstraints.BOTH;
        root.add(Box.createVerticalGlue(), c);
        return root;
    }

    @Override
    public boolean isModified() {
        return root != null && (!Objects.equals(endpoint.getText().trim(), settings.endpoint())
                || !Objects.equals(mode.getSelectedItem(), settings.executionMode())
                || ((Number) maxScenarios.getValue()).intValue() != settings.maxScenarios()
                || !Objects.equals(cost.getSelectedItem(), settings.costThreshold())
                || !Objects.equals(projectDatasource.getText().trim(), settings.datasourceProfileId())
                || ((Number) cacheMb.getValue()).intValue() != settings.localCacheMegabytes());
    }

    @Override
    public void apply() {
        settings.endpoint(endpoint.getText());
        settings.executionMode(String.valueOf(mode.getSelectedItem()));
        settings.maxScenarios(((Number) maxScenarios.getValue()).intValue());
        settings.costThreshold(String.valueOf(cost.getSelectedItem()));
        settings.datasourceProfileId(projectDatasource.getText());
        settings.localCacheMegabytes(((Number) cacheMb.getValue()).intValue());
    }

    @Override public void reset() {
        if (root == null) return;
        endpoint.setText(settings.endpoint());
        mode.setSelectedItem(settings.executionMode());
        maxScenarios.setValue(settings.maxScenarios());
        cost.setSelectedItem(settings.costThreshold());
        projectDatasource.setText(settings.datasourceProfileId());
        cacheMb.setValue(settings.localCacheMegabytes());
        tokenStatus.setText(TokenStore.getInstance().token().isBlank()
                ? "未配置 · 请在 Tool Window 连接后端" : "已保存在 PasswordSafe");
    }

    @Override public void disposeUIResources() { root = null; }

    private void addSection(String title, int row, GridBagConstraints c) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weightx = 1;
        root.add(new com.intellij.ui.TitledSeparator(title), c);
        c.gridwidth = 1;
    }
    private void addRow(String label, JComponent component, int row, GridBagConstraints c) {
        c.gridy = row; c.gridx = 0; c.weightx = 0;
        root.add(new JBLabel(label), c);
        c.gridx = 1; c.weightx = 1;
        root.add(component, c);
    }
}
