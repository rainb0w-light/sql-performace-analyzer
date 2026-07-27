package com.biz.sccba.sqlanalyzer.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates report JSON against the frozen standard schema (docs/contracts/report-schema.json).
 * The classpath copy is byte-identical to the docs contract (ReportSchemaIdentityTest guards
 * the single source of truth).
 */
@Component
public class ReportSchemaValidator {

    private final ObjectMapper mapper;
    private final JsonSchema schema;

    public ReportSchemaValidator(ObjectMapper mapper) {
        this.mapper = mapper;
        try (InputStream in = getClass().getResourceAsStream("/contracts/report-schema.json")) {
            if (in == null) throw new IllegalStateException("classpath:contracts/report-schema.json 缺失");
            this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.valueOf("V" + "202012")).getSchema(in);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("报告 Schema 加载失败", e);
        }
    }

    /** Throws IllegalArgumentException with all validation messages when the report is invalid. */
    public void validate(String reportJson) {
        try {
            JsonNode node = mapper.readTree(reportJson);
            Set<String> errors = schema.validate(node).stream()
                    .map(m -> m.getInstanceLocation() + ": " + m.getMessage())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("报告不符合 report-schema.json：" + errors);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("报告 JSON 无法解析：" + e.getMessage(), e);
        }
    }
}
