package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.DocumentChunkEntity;
import org.springframework.data.repository.CrudRepository;

public interface DocumentChunkJdbcRepository extends CrudRepository<DocumentChunkEntity, String> {
}
