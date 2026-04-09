package com.biz.sccba.sqlanalyzer.agent;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages modular prompt templates for SQL analysis.
 * Replaces the monolithic SYSTEM_PROMPT with a modular template system.
 */
@Component
public class PromptTemplateManager {

    private final Map<String, String> templates = new HashMap<>();
    
    public PromptTemplateManager() {
        loadTemplates();
    }
    
    /**
     * Loads all template files from the classpath.
     */
    private void loadTemplates() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:templates/*.txt");
            
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    String templateName = filename.replace(".txt", "");
                    String content = new String(resource.getInputStream().readAllBytes());
                    templates.put(templateName, content);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load prompt templates: " + e.getMessage());
        }
    }
    
    /**
     * Gets a template by name.
     */
    public String getTemplate(String templateName) {
        return templates.getOrDefault(templateName, "");
    }
    
    /**
     * Gets all available template names.
     */
    public Iterable<String> getTemplateNames() {
        return templates.keySet();
    }
}