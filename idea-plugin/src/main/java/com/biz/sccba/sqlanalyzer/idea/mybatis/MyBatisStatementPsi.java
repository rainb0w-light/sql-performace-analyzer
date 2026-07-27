package com.biz.sccba.sqlanalyzer.idea.mybatis;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

/**
 * XML PSI/DOM based MyBatis statement identification (development-guide §8.1) — no regex:
 * a Mapper is an XML file whose root tag is {@code <mapper>} with a namespace, and only complete
 * statement tags may start a standard analysis.
 */
public final class MyBatisStatementPsi {

    public static final Set<String> STATEMENT_TAGS = Set.of("select", "insert", "update", "delete");

    private MyBatisStatementPsi() {}

    public record StatementRef(String namespace, String statementId, String statementType,
                               String moduleName, String mapperPath, String contentHash,
                               String mapperXml) {}

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
                sha256(mapperXml), mapperXml);
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
