package com.example.sqlanalyzer.repository;

import com.example.sqlanalyzer.model.PromptTemplateDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplateDefinition, Long> {
    Optional<PromptTemplateDefinition> findByTemplateType(String templateType);
}

