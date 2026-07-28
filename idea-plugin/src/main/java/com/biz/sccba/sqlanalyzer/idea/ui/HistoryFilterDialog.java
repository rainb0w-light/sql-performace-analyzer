package com.biz.sccba.sqlanalyzer.idea.ui;

import com.biz.sccba.sqlanalyzer.idea.client.BackendClient;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/** Server-side history filters; no report delete/retention controls are exposed. */
public final class HistoryFilterDialog extends DialogWrapper {
    private final String projectId;
    private final String moduleId;
    private final JBTextField statement = new JBTextField();
    private final JBTextField datasource = new JBTextField();
    private final ComboBox<String> severity = new ComboBox<>(
            new String[]{"全部", "CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"});
    private final JBTextField completedFrom = new JBTextField();
    private final JBTextField completedTo = new JBTextField();
    private final ComboBox<String> stale = new ComboBox<>(new String[]{"全部", "当前", "已过期"});

    public HistoryFilterDialog(Project project, String projectId, String moduleId,
                               String initialStatement, String initialDatasource) {
        super(project);
        this.projectId = projectId;
        this.moduleId = moduleId;
        statement.setText(initialStatement);
        datasource.setText(initialDatasource);
        setTitle("筛选服务端历史报告");
        init();
    }

    public BackendClient.HistoryFilter filter() {
        Boolean staleValue = stale.getSelectedIndex() == 0 ? null : stale.getSelectedIndex() == 2;
        return new BackendClient.HistoryFilter(projectId, moduleId, statement.getText().trim(),
                datasource.getText().trim(), severity.getSelectedIndex() == 0 ? ""
                : String.valueOf(severity.getSelectedItem()), completedFrom.getText().trim(),
                completedTo.getText().trim(), staleValue, 0, 10);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBPanel<?> panel = new JBPanel<>(new GridBagLayout());
        panel.setPreferredSize(JBUI.size(520, 260));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = JBUI.insets(4); c.fill = GridBagConstraints.HORIZONTAL;
        add(panel, c, 0, "Statement", statement);
        add(panel, c, 1, "DataSource ID", datasource);
        add(panel, c, 2, "严重度", severity);
        add(panel, c, 3, "完成时间起（ISO-8601）", completedFrom);
        add(panel, c, 4, "完成时间止（ISO-8601）", completedTo);
        add(panel, c, 5, "过期状态", stale);
        JBLabel note = new JBLabel("历史与保留以服务端为准；Plugin 不提供报告硬删除。");
        c.gridy = 6; c.gridx = 0; c.gridwidth = 2;
        panel.add(note, c);
        return panel;
    }

    private static void add(JPanel panel, GridBagConstraints c, int row,
                            String label, JComponent component) {
        c.gridy = row; c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
        panel.add(new JBLabel(label), c);
        c.gridx = 1; c.weightx = 1;
        panel.add(component, c);
    }
}
