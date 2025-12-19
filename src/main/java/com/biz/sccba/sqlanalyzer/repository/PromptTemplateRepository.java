package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.PromptTemplateDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplateDefinition, Long> {
    Optional<PromptTemplateDefinition> findByTemplateType(String templateType);
}

