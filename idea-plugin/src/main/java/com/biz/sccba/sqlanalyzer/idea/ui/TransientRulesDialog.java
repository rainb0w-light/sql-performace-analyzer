package com.biz.sccba.sqlanalyzer.idea.ui;

import com.biz.sccba.sqlanalyzer.idea.contract.PluginApiDtos.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Run-scoped typed rules. Server impact preview runs only from the explicit button. */
public final class TransientRulesDialog extends DialogWrapper {
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"规则类型", "目标", "关系", "值类型", "值"}, 0);
    private final JBTable table = new JBTable(tableModel);
    private final Consumer<List<TransientRule>> apply;
    private final Consumer<Consumer<TransientRuleImpact>> preview;
    private final JBTextArea impact = new JBTextArea();

    public TransientRulesDialog(Project project, List<TransientRule> initial,
                                Consumer<List<TransientRule>> apply,
                                Consumer<Consumer<TransientRuleImpact>> preview) {
        super(project);
        this.apply = apply;
        this.preview = preview;
        setTitle("本次分析补充");
        if (initial != null) initial.forEach(rule -> tableModel.addRow(new Object[]{
                rule.kind(), rule.target(), rule.operator(),
                rule.values().isEmpty() ? ValueType.STRING : rule.values().get(0).type(),
                rule.values().stream().map(TypedValue::value).toList()}));
        table.getColumnModel().getColumn(0).setCellEditor(
                new DefaultCellEditor(new JComboBox<>(RuleKind.values())));
        table.getColumnModel().getColumn(2).setCellEditor(
                new DefaultCellEditor(new JComboBox<>(new String[]{"EQ", "PRESENT", "IN", "BETWEEN", "SAMPLE"})));
        table.getColumnModel().getColumn(3).setCellEditor(
                new DefaultCellEditor(new JComboBox<>(ValueType.values())));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBPanel<?> root = new JBPanel<>(new BorderLayout());
        root.setPreferredSize(JBUI.size(720, 420));
        root.add(new JBLabel("仅作用于当前 Run；终态后清除；不会进入知识库或 Agent 长期记忆。"),
                BorderLayout.NORTH);
        root.add(new JBScrollPane(table), BorderLayout.CENTER);
        JBPanel<?> south = new JBPanel<>(new BorderLayout());
        JBPanel<?> actions = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("添加一条");
        add.addActionListener(event -> tableModel.addRow(new Object[]{
                RuleKind.PARAMETER_FACT, "", "EQ", ValueType.STRING, ""}));
        JButton remove = new JButton("移除选中");
        remove.addActionListener(event -> {
            if (table.getSelectedRow() >= 0) tableModel.removeRow(table.getSelectedRow());
        });
        JButton previewButton = new JButton("预览影响");
        previewButton.addActionListener(event -> {
            List<TransientRule> values = rules();
            apply.accept(values);
            preview.accept(result -> impact.setText(format(result)));
        });
        actions.add(add); actions.add(remove); actions.add(previewButton);
        impact.setEditable(false);
        impact.setRows(5);
        south.add(actions, BorderLayout.NORTH);
        south.add(new JBScrollPane(impact), BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    @Override
    protected void doOKAction() {
        apply.accept(rules());
        super.doOKAction();
    }

    private List<TransientRule> rules() {
        List<TransientRule> values = new ArrayList<>();
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            RuleKind kind;
            try { kind = RuleKind.valueOf(String.valueOf(tableModel.getValueAt(row, 0))); }
            catch (Exception ignored) { kind = RuleKind.PARAMETER_FACT; }
            String target = String.valueOf(tableModel.getValueAt(row, 1)).trim();
            String operator = String.valueOf(tableModel.getValueAt(row, 2)).trim();
            ValueType valueType;
            try { valueType = ValueType.valueOf(String.valueOf(tableModel.getValueAt(row, 3))); }
            catch (Exception ignored) { valueType = ValueType.STRING; }
            final ValueType selectedValueType = valueType;
            String raw = String.valueOf(tableModel.getValueAt(row, 4));
            if (target.isBlank()) continue;
            List<TypedValue> typed = Arrays.stream(raw.split(","))
                    .map(String::trim).filter(value -> !value.isBlank())
                    .map(value -> TypedValue.scalar(selectedValueType, value)).toList();
            values.add(new TransientRule("tmp_" + row, kind, target, operator, typed));
        }
        return List.copyOf(values);
    }

    private static String format(TransientRuleImpact result) {
        return "新增场景：" + result.addedScenarioIds()
                + "\n移除场景：" + result.removedScenarioIds()
                + "\n覆盖变化：+" + result.addedCoverageGoals() + " -" + result.removedCoverageGoals()
                + "\n守卫变化：" + result.guardChanges()
                + "\n成本：" + result.costBefore() + " → " + result.costAfter();
    }
}
