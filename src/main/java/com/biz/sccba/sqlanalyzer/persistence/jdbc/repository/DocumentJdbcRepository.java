package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.DocumentEntity;
import org.springframework.data.repository.CrudRepository;

public interface DocumentJdbcRepository extends CrudRepository<DocumentEntity, String> {
}
