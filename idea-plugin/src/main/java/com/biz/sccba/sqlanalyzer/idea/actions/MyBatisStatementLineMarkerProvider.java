package com.biz.sccba.sqlanalyzer.idea.actions;

import com.biz.sccba.sqlanalyzer.idea.mybatis.MyBatisStatementPsi;
import com.biz.sccba.sqlanalyzer.idea.mybatis.GutterAnalysisState;
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
        var project = anchor.getProject();
        MyBatisStatementPsi.StatementRef ref = MyBatisStatementPsi.resolve(
                project, anchor.getContainingFile(), anchor.getTextOffset());
        if (ref == null) return null;
        GutterAnalysisState.Entry state = project.getService(GutterAnalysisState.class)
                .get(ref.locator(), ref.contentHash(), "");
        javax.swing.Icon icon = switch (state.status()) {
            case RUNNING -> AllIcons.Process.Step_1;
            case COMPLETED -> AllIcons.General.InspectionsOK;
            case FAILED, STALE -> AllIcons.General.Warning;
            case READY -> AllIcons.Actions.Execute;
        };
        String tooltip = state.message() + (state.updatedAt() == null ? ""
                : " · " + state.updatedAt()) + (state.severity().isBlank() ? ""
                : " · " + state.severity());
        return new LineMarkerInfo<>(anchor, anchor.getTextRange(), icon,
                ignored -> tooltip,
                (event, elt) -> {
                    PsiFile file = elt.getContainingFile();
                    if (file == null) return;
                    var targetProject = elt.getProject();
                    var editor = FileEditorManager.getInstance(targetProject).getSelectedTextEditor();
                    if (editor != null) {
                        editor.getCaretModel().moveToOffset(elt.getTextOffset());
                        AnalyzeStatementAction.startAnalysis(targetProject, editor, file);
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
