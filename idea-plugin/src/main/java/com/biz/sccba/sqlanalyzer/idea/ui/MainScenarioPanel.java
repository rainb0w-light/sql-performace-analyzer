package com.biz.sccba.sqlanalyzer.idea.ui;

import com.biz.sccba.sqlanalyzer.idea.contract.PluginApiDtos.*;
import com.biz.sccba.sqlanalyzer.idea.scenario.MainScenarioModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.*;

/** Typed main-scenario editor: IF checkbox, CHOOSE radio and FOREACH collection mode. */
public final class MainScenarioPanel extends JBPanel<MainScenarioPanel> {
    private final MainScenarioModel model;
    private final Runnable preview;
    private final Runnable confirm;
    private final Runnable refreshSuggestions;
    private final Runnable cancel;
    private final Map<String, JComponent> controls = new LinkedHashMap<>();
    private final Map<String, JComponent> valueControls = new LinkedHashMap<>();
    private final Map<String, ButtonGroup> chooseGroups = new HashMap<>();
    private final JBTextArea previewText = new JBTextArea();
    private final JButton confirmButton = new JButton("确认主场景并分析");

    public MainScenarioPanel(MainScenarioModel model, Runnable preview, Runnable confirm,
                             Runnable refreshSuggestions, Runnable cancel) {
        super(new BorderLayout());
        this.model = Objects.requireNonNull(model);
        this.preview = preview;
        this.confirm = confirm;
        this.refreshSuggestions = refreshSuggestions;
        this.cancel = cancel;
        setBorder(BorderFactory.createTitledBorder("配置本次主场景"));
        add(header(), BorderLayout.NORTH);
        add(new JBScrollPane(rows()), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "cancel-main-scenario");
        getActionMap().put("cancel-main-scenario", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { cancel.run(); }
        });
        refreshEnabledState();
    }

    public JComponent controlFor(String nodeId) { return controls.get(nodeId); }
    public JButton confirmButton() { return confirmButton; }

    public void showPreview(BoundSqlPreview result) {
        StringBuilder text = new StringBuilder("脱敏 BoundSql\n")
                .append(result.boundSql() == null ? "" : result.boundSql())
                .append("\n\n命中 nodeId：").append(String.join(", ", result.hitNodeIds()));
        if (!result.validationErrors().isEmpty()) {
            text.append("\n\n字段校验错误");
            result.validationErrors().forEach(error -> text.append("\n- ")
                    .append(error.field()).append(" [").append(error.code()).append("] ")
                    .append(error.message()));
        }
        previewText.setText(text.toString());
    }

    private JComponent header() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout());
        long dynamicCount = model.nodes().stream().filter(SuggestionNode::assignable).count();
        panel.add(new JBLabel("检测到 " + dynamicCount + " 个可赋值动态条件；系统覆盖场景保持独立。"),
                BorderLayout.WEST);
        JButton refresh = new JButton("刷新建议");
        refresh.addActionListener(event -> refreshSuggestions.run());
        panel.add(refresh, BorderLayout.EAST);
        return panel;
    }

    private JComponent rows() {
        JBPanel<?> panel = new JBPanel<>(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = JBUI.insets(3);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        String[] headers = {"填充", "MyBatis test / 参数", "默认值", "建议依据", "校验"};
        for (int column = 0; column < headers.length; column++) {
            c.gridx = column; c.gridy = 0; c.weightx = column == 1 ? 1 : 0;
            panel.add(new JBLabel(headers[column]), c);
        }

        Set<String> renderedParameters = new HashSet<>();
        int row = 1;
        ConditionCategory currentCategory = null;
        for (SuggestionNode node : model.nodes()) {
            if (node.category() != currentCategory) {
                currentCategory = node.category();
                c.gridy = row++;
                c.gridx = 0; c.gridwidth = 5; c.weightx = 1;
                panel.add(new TitledSeparator("分类：" + categoryName(currentCategory)
                        + " · " + node.categorySource()), c);
                c.gridwidth = 1;
            }
            c.gridy = row++;
            c.gridx = 0; c.weightx = 0;
            JComponent selector = selector(node);
            controls.put(node.nodeId(), selector);
            panel.add(selector, c);

            c.gridx = 1; c.weightx = 1;
            String indent = node.parentNodeId() == null || node.parentNodeId().isBlank() ? "" : "↳ ";
            JBLabel expression = new JBLabel(indent + safe(node.testExpression())
                    + (node.parameterPath() == null || node.parameterPath().isBlank()
                    ? "" : "  ·  " + node.parameterPath()));
            expression.getAccessibleContext().setAccessibleName("动态条件 " + node.nodeId());
            panel.add(expression, c);

            c.gridx = 2; c.weightx = 0;
            JComponent value = valueControl(node, renderedParameters);
            valueControls.put(node.nodeId(), value);
            panel.add(value, c);

            c.gridx = 3;
            boolean low = MainScenarioModel.isLowConfidence(node);
            JBLabel provenance = new JBLabel((low ? "⚠ 低置信度 · " : "")
                    + safe(node.source()) + "@" + safe(node.version())
                    + " · " + safe(node.locator()) + " · "
                    + String.format(Locale.ROOT, "%.2f", node.confidence())
                    + " · " + safe(node.reason()),
                    low ? AllIcons.General.Warning : AllIcons.General.Information, SwingConstants.LEFT);
            provenance.getAccessibleContext().setAccessibleName(
                    (low ? "低置信度，" : "") + "建议依据 " + safe(node.reason()));
            panel.add(provenance, c);

            c.gridx = 4;
            panel.add(new JBLabel(model.conflicts().stream().anyMatch(conflict ->
                    conflict.startsWith(safe(node.parameterPath()) + ":")) ? "⛔ 类型冲突" : "可用"), c);
        }
        c.gridy = row; c.gridx = 0; c.gridwidth = 5; c.weighty = 1; c.fill = GridBagConstraints.BOTH;
        panel.add(Box.createVerticalGlue(), c);
        return panel;
    }

    private JComponent selector(SuggestionNode node) {
        if (!node.assignable() || node.kind() == NodeKind.STRUCTURE) return new JBLabel("—");
        if (node.kind() == NodeKind.CHOOSE_WHEN || node.kind() == NodeKind.CHOOSE_OTHERWISE) {
            JBRadioButton radio = new JBRadioButton();
            radio.setSelected(model.selected(node.nodeId()));
            chooseGroups.computeIfAbsent(safe(node.chooseGroupId()), ignored -> new ButtonGroup()).add(radio);
            radio.addActionListener(event -> {
                model.select(node.nodeId(), true);
                refreshFromModel();
            });
            radio.getAccessibleContext().setAccessibleName("选择互斥分支 " + node.nodeId());
            return radio;
        }
        if (node.kind() == NodeKind.FOREACH) {
            ComboBox<CollectionMode> combo = new ComboBox<>(CollectionMode.values());
            combo.setSelectedItem(model.collectionMode(node.nodeId()));
            combo.addActionListener(event -> {
                model.collectionMode(node.nodeId(), (CollectionMode) combo.getSelectedItem());
                refreshFromModel();
            });
            combo.getAccessibleContext().setAccessibleName("集合模式 " + node.nodeId());
            return combo;
        }
        JBCheckBox check = new JBCheckBox();
        check.setSelected(model.selected(node.nodeId()));
        check.addActionListener(event -> {
            model.select(node.nodeId(), check.isSelected());
            refreshFromModel();
        });
        check.getAccessibleContext().setAccessibleName("填充条件 " + node.nodeId());
        return check;
    }

    private JComponent valueControl(SuggestionNode node, Set<String> renderedParameters) {
        String path = safe(node.parameterPath());
        if (path.isBlank()) return new JBLabel("结构节点");
        if (!renderedParameters.add(path)) return new JBLabel("共享参数：" + path);
        TypedValue typed = model.parameter(path);
        JBTextField field = new JBTextField(MainScenarioModel.displayValue(node, typed), 16);
        field.getAccessibleContext().setAccessibleName("参数 " + path);
        field.addActionListener(event -> {
            if (typed != null) model.parameter(path, TypedValue.scalar(typed.type(), field.getText()));
        });
        return field;
    }

    private JComponent footer() {
        JBPanel<?> footer = new JBPanel<>(new BorderLayout());
        previewText.setEditable(false);
        previewText.setRows(4);
        previewText.setLineWrap(true);
        previewText.setWrapStyleWord(true);
        footer.add(new JBScrollPane(previewText), BorderLayout.CENTER);
        JBPanel<?> actions = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT));
        JButton previewButton = new JButton("预览 BoundSql");
        previewButton.addActionListener(event -> preview.run());
        JButton cancelButton = new JButton("取消本次分析");
        cancelButton.addActionListener(event -> cancel.run());
        confirmButton.addActionListener(event -> confirm.run());
        confirmButton.setMnemonic('A');
        actions.add(cancelButton);
        actions.add(previewButton);
        actions.add(confirmButton);
        footer.add(actions, BorderLayout.SOUTH);
        return footer;
    }

    private void refreshFromModel() {
        for (SuggestionNode node : model.nodes()) {
            JComponent control = controls.get(node.nodeId());
            if (control instanceof AbstractButton button) button.setSelected(model.selected(node.nodeId()));
            if (control instanceof JComboBox<?> combo) combo.setSelectedItem(model.collectionMode(node.nodeId()));
        }
        refreshEnabledState();
    }

    private void refreshEnabledState() {
        for (SuggestionNode node : model.nodes()) {
            JComponent control = controls.get(node.nodeId());
            if (control != null) control.setEnabled(model.enabled(node.nodeId()));
            JComponent value = valueControls.get(node.nodeId());
            if (value != null) value.setEnabled(model.enabled(node.nodeId()) && model.selected(node.nodeId()));
        }
        confirmButton.setEnabled(model.valid());
        confirmButton.setToolTipText(model.valid() ? "确认后由服务端校验并生成 BoundSql"
                : "共享参数存在类型冲突：" + String.join("; ", model.conflicts()));
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String categoryName(ConditionCategory category) {
        return switch (category) {
            case ROUTING -> "路由";
            case FILTER -> "过滤";
            case SORT_PAGE -> "排序/分页";
            case JOIN -> "关联";
            case OTHER -> "其他";
        };
    }
}
