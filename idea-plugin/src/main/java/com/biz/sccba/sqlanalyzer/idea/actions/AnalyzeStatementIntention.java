package com.biz.sccba.sqlanalyzer.idea.actions;

import com.biz.sccba.sqlanalyzer.idea.mybatis.MyBatisStatementPsi;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Alt/Option+Enter entry point sharing the same PSI resolver as the Action and Gutter. */
public final class AnalyzeStatementIntention extends PsiElementBaseIntentionAction {
    @Override public @NotNull String getText() { return "分析 SQL 性能"; }
    @Override public @NotNull String getFamilyName() { return "SQL Performance Analyzer"; }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        return editor != null && file != null
                && MyBatisStatementPsi.resolve(project, file, editor.getCaretModel().getOffset()) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        AnalyzeStatementAction.startAnalysis(project, editor, element.getContainingFile());
    }

    @Override public boolean startInWriteAction() { return false; }
}
