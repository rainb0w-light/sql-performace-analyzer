package com.biz.sccba.sqlanalyzer.idea.mybatis;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Non-IDE structural gate for the PSI implementation. Actual XmlFile/PsiJavaFile behavior is
 * covered by the runIde acceptance fixture because contractTest intentionally does not boot IDEA.
 */
public class PsiEntryContractTest {
    @Test
    public void locatorUsesPsiAndProjectModuleModelWithoutRegexOrUserModuleInput() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/biz/sccba/sqlanalyzer/idea/mybatis/MyBatisStatementPsi.java"));
        assertTrue(source.contains("ModuleUtilCore.findModuleForPsiElement"));
        assertTrue(source.contains("instanceof XmlFile"));
        assertTrue(source.contains("instanceof PsiJavaFile"));
        assertTrue(source.contains("org.apache.ibatis.annotations."));
        assertTrue(source.contains("annotations.size() != 1"));
        assertFalse(source.contains("Pattern.compile"));
        assertFalse(source.contains("Scanner"));
    }
}
