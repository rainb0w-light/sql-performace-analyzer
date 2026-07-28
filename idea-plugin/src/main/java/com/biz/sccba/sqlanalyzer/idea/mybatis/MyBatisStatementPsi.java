package com.biz.sccba.sqlanalyzer.idea.mybatis;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.List;

/**
 * XML PSI/DOM based MyBatis statement identification (development-guide §8.1) — no regex:
 * a Mapper is an XML file whose root tag is {@code <mapper>} with a namespace, and only complete
 * statement tags may start a standard analysis.
 */
public final class MyBatisStatementPsi {

    public static final Set<String> STATEMENT_TAGS = Set.of("select", "insert", "update", "delete");
    public static final Set<String> ANNOTATION_NAMES = Set.of("Select", "Insert", "Update", "Delete");

    private MyBatisStatementPsi() {}

    public enum SourceKind { XML, JAVA_ANNOTATION }

    public record StatementRef(String namespace, String statementId, String statementType,
                               String moduleName, String mapperPath, String contentHash,
                               String mapperXml, SourceKind sourceKind, String locator) {}

    public static boolean isMapperFile(PsiFile file) {
        if (!(file instanceof XmlFile xmlFile)) return false;
        XmlTag root = xmlFile.getRootTag();
        return root != null && "mapper".equals(root.getLocalName())
                && namespaceOf(root) != null && !namespaceOf(root).isBlank();
    }

    public static String namespaceOf(XmlTag rootTag) {
        return rootTag == null ? null : rootTag.getAttributeValue("namespace");
    }

    /** Finds the innermost statement tag containing the offset, or null. */
    public static XmlTag statementTagAt(XmlFile file, int offset) {
        var element = file.findElementAt(offset);
        while (element != null) {
            if (element instanceof XmlTag tag && STATEMENT_TAGS.contains(tag.getLocalName())) {
                XmlTag parent = tag.getParentTag();
                if (parent != null && "mapper".equals(parent.getLocalName())) {
                    return tag;
                }
            }
            element = element.getParent();
        }
        return null;
    }

    public static StatementRef toRef(com.intellij.openapi.project.Project project, XmlFile file, XmlTag statementTag) {
        XmlTag root = file.getRootTag();
        String namespace = namespaceOf(root);
        String statementId = statementTag.getAttributeValue("id");
        String type = statementTag.getLocalName() == null ? "" : statementTag.getLocalName().toUpperCase(java.util.Locale.ROOT);
        String mapperXml = file.getText();
        Module module = ModuleUtilCore.findModuleForPsiElement(file);
        return new StatementRef(namespace, statementId, type,
                module == null ? "" : module.getName(),
                file.getVirtualFile() == null ? "" : file.getVirtualFile().getPath(),
                sha256(mapperXml), mapperXml, SourceKind.XML,
                namespace + "#" + statementId);
    }

    /** Resolves XML or a uniquely static MyBatis Java annotation at the supplied PSI offset. */
    public static StatementRef resolve(com.intellij.openapi.project.Project project, PsiFile file, int offset) {
        if (file instanceof XmlFile xmlFile) {
            XmlTag tag = statementTagAt(xmlFile, offset);
            return tag == null ? null : toRef(project, xmlFile, tag);
        }
        if (file instanceof PsiJavaFile javaFile) {
            PsiMethod method = methodAt(javaFile, offset);
            return method == null ? null : toRef(project, javaFile, method);
        }
        return null;
    }

    public static PsiMethod methodAt(PsiJavaFile file, int offset) {
        PsiElement element = file.findElementAt(offset);
        while (element != null && !(element instanceof PsiMethod)) element = element.getParent();
        if (!(element instanceof PsiMethod method)) return null;
        return supportedAnnotations(method).size() == 1 && annotationSqlIsStatic(supportedAnnotations(method).get(0))
                ? method : null;
    }

    public static StatementRef toRef(com.intellij.openapi.project.Project project,
                                     PsiJavaFile file, PsiMethod method) {
        List<PsiAnnotation> annotations = supportedAnnotations(method);
        if (annotations.size() != 1 || !annotationSqlIsStatic(annotations.get(0))) return null;
        PsiClass owner = method.getContainingClass();
        String namespace = owner == null || owner.getQualifiedName() == null ? "" : owner.getQualifiedName();
        if (namespace.isBlank()) return null;
        String annotationName = annotations.get(0).getNameReferenceElement() == null ? ""
                : annotations.get(0).getNameReferenceElement().getReferenceName();
        String type = annotationName == null ? "" : annotationName.toUpperCase(java.util.Locale.ROOT);
        Module module = ModuleUtilCore.findModuleForPsiElement(method);
        String source = file.getText();
        return new StatementRef(namespace, method.getName(), type,
                module == null ? "" : module.getName(),
                file.getVirtualFile() == null ? "" : file.getVirtualFile().getPath(),
                sha256(source), source, SourceKind.JAVA_ANNOTATION,
                namespace + "#" + method.getName() + "@" + annotationName);
    }

    private static List<PsiAnnotation> supportedAnnotations(PsiMethod method) {
        return java.util.Arrays.stream(method.getModifierList().getAnnotations())
                .filter(annotation -> {
                    String qn = annotation.getQualifiedName();
                    String simple = qn == null ? "" : qn.substring(qn.lastIndexOf('.') + 1);
                    return qn != null && qn.startsWith("org.apache.ibatis.annotations.")
                            && ANNOTATION_NAMES.contains(simple);
                }).toList();
    }

    private static boolean annotationSqlIsStatic(PsiAnnotation annotation) {
        PsiNameValuePair[] pairs = annotation.getParameterList().getAttributes();
        if (pairs.length == 0) return false;
        for (PsiNameValuePair pair : pairs) {
            if (!"value".equals(pair.getName()) && pair.getName() != null) continue;
            PsiElement value = pair.getValue();
            if (value instanceof PsiLiteralExpression literal) {
                if (!(literal.getValue() instanceof String)) return false;
            } else if (value instanceof com.intellij.psi.PsiArrayInitializerMemberValue array) {
                for (var initializer : array.getInitializers()) {
                    if (!(initializer instanceof PsiLiteralExpression literal)
                            || !(literal.getValue() instanceof String)) return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "";
        }
    }
}
