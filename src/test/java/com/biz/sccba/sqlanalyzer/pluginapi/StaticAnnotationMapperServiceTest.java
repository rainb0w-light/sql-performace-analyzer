package com.biz.sccba.sqlanalyzer.pluginapi;

import com.biz.sccba.sqlanalyzer.service.ArtifactPipelineService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaticAnnotationMapperServiceTest {

    @Test
    void acceptsLiteralArraysAndIndexesSyntheticMapper() {
        ArtifactPipelineService pipeline = mock(ArtifactPipelineService.class);
        when(pipeline.ingestMyBatisMapper(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString()))
                .thenReturn(new ArtifactPipelineService.IndexedArtifact("artifact_1", "document_1", 1));
        StaticAnnotationMapperService service = new StaticAnnotationMapperService(pipeline);

        var result = service.index("client_1", "session_1", """
                package demo;
                interface LoanMapper {
                  @org.apache.ibatis.annotations.Select({
                    "<script>",
                    "select * from loan",
                    "<if test=\\"status != null\\">where status = #{status}</if>",
                    "</script>"
                  })
                  java.util.List<Object> find(String status);
                }
                """, "demo.LoanMapper", "find");

        assertEquals("artifact_1", result.artifactId());
        ArgumentCaptor<String> xml = ArgumentCaptor.forClass(String.class);
        verify(pipeline).ingestMyBatisMapper(
                org.mockito.ArgumentMatchers.eq("client_1"),
                org.mockito.ArgumentMatchers.eq("session_1"),
                xml.capture(), org.mockito.ArgumentMatchers.eq("demo.LoanMapper"),
                org.mockito.ArgumentMatchers.eq("MYBATIS_ANNOTATION_MAPPER"),
                org.mockito.ArgumentMatchers.contains("sourceContentHash"));
        assertTrue(xml.getValue().contains("<select id=\"find\">"));
        assertTrue(xml.getValue().contains("<if test=\"status != null\">"));
    }

    @Test
    void supportsTextBlocksAndLiteralConcatenation() {
        assertEquals("select * from loan where id = #{id}",
                StaticAnnotationMapperService.parseLiteralExpression(
                        "\"select * from loan\" + \"where id = #{id}\""));
        assertEquals("select *\nfrom loan",
                StaticAnnotationMapperService.parseLiteralExpression(
                        "\"\"\"\nselect *\nfrom loan\n\"\"\""));
    }

    @Test
    void rejectsConstantsAndCallsAsUnsupported() {
        var constant = assertThrows(IllegalArgumentException.class,
                () -> StaticAnnotationMapperService.parseLiteralExpression("SQL"));
        assertTrue(constant.getMessage().startsWith("UNSUPPORTED:"));
        assertThrows(IllegalArgumentException.class,
                () -> StaticAnnotationMapperService.parseLiteralExpression("sql()"));
    }

    @Test
    void rejectsAmbiguousTargetMethod() {
        StaticAnnotationMapperService service =
                new StaticAnnotationMapperService(mock(ArtifactPipelineService.class));
        var error = assertThrows(IllegalArgumentException.class, () -> service.index(
                "client", null, """
                        interface M {
                          @Select("select 1") int find();
                          @Delete("delete from t") int find(String id);
                        }
                        """, "M", "find"));
        assertTrue(error.getMessage().startsWith("UNSUPPORTED:"));
    }
}
