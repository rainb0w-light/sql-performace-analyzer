package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "document_chunk")
public class DocumentChunkEntity extends AssignedIdEntity {
    private String documentId;
    private Integer sequenceNo;
    private String chunkType;
    private String content;
    private Integer tokenCount;
    private String metadata;
}
