package com.biz.sccba.sqlanalyzer.idea.actions;

import com.biz.sccba.sqlanalyzer.idea.mybatis.MyBatisStatementPsi;
import com.biz.sccba.sqlanalyzer.idea.state.AnalysisCoordinator;
import com.biz.sccba.sqlanalyzer.idea.ui.AnalysisUiBridge;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

/** Keymap-configurable editor action shared by XML/Java popup, Gutter and Intention entries. */
public final class AnalyzeStatementAction extends AnAction {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        boolean available = project != null && editor != null
                && (file instanceof XmlFile || file instanceof PsiJavaFile)
                && ReadAction.compute(() -> MyBatisStatementPsi.resolve(
                project, file, editor.getCaretModel().getOffset()) != null);
        event.getPresentation().setEnabledAndVisible(available);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project != null && file != null && editor != null) startAnalysis(project, editor, file);
    }

    public static void startAnalysis(Project project, Editor editor, PsiFile file) {
        MyBatisStatementPsi.StatementRef ref = ReadAction.compute(() ->
                MyBatisStatementPsi.resolve(project, file, editor.getCaretModel().getOffset()));
        AnalysisUiBridge bridge = AnalysisUiBridge.getInstance(project);
        if (ref == null) {
            bridge.showAndDispatch(() -> bridge.status(
                    "请将光标放在 Mapper statement 或可静态解析的 MyBatis 注解方法内"));
            return;
        }
        bridge.showAndDispatch(() -> {
            bridge.status("正在准备 " + ref.namespace() + "." + ref.statementId());
            AnalysisCoordinator.getInstance(project).begin(ref);
        });
    }
}
