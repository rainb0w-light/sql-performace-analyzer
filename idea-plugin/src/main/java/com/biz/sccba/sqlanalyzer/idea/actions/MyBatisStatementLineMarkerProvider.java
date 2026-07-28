package com.biz.sccba.sqlanalyzer.idea.actions;

import com.biz.sccba.sqlanalyzer.idea.mybatis.MyBatisStatementPsi;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Gutter entry for top-level Mapper XML statements and statically resolvable annotation methods. */
public final class MyBatisStatementLineMarkerProvider implements LineMarkerProvider {
    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        PsiElement anchor = anchor(element);
        if (anchor == null) return null;
        return new LineMarkerInfo<>(anchor, anchor.getTextRange(), AllIcons.Actions.Execute,
                ignored -> "分析 SQL 性能",
                (event, elt) -> {
                    PsiFile file = elt.getContainingFile();
                    if (file == null) return;
                    var project = elt.getProject();
                    var editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                    if (editor != null) {
                        editor.getCaretModel().moveToOffset(elt.getTextOffset());
                        AnalyzeStatementAction.startAnalysis(project, editor, file);
                    }
                },
                GutterIconRenderer.Alignment.LEFT, () -> "分析 SQL 性能");
    }

    private static PsiElement anchor(PsiElement element) {
        if (element instanceof XmlTag tag
                && MyBatisStatementPsi.STATEMENT_TAGS.contains(tag.getLocalName())
                && tag.getParentTag() != null && "mapper".equals(tag.getParentTag().getLocalName())) {
            return tag;
        }
        if (element instanceof PsiIdentifier identifier && identifier.getParent() instanceof PsiMethod method) {
            PsiFile file = method.getContainingFile();
            if (file instanceof PsiJavaFile javaFile
                    && MyBatisStatementPsi.methodAt(javaFile, method.getTextOffset()) == method) {
                return identifier;
            }
        }
        return null;
    }
}
