package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.model.PromptTemplateDefinition;
import com.biz.sccba.sqlanalyzer.service.PromptTemplateManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prompts")
public class PromptTemplateController {

    @Autowired
    private PromptTemplateManagerService promptTemplateManagerService;

    @GetMapping
    public List<PromptTemplateDefinition> getAllTemplates() {
        return promptTemplateManagerService.getAllTemplates();
    }

    @GetMapping("/{type}")
    public PromptTemplateDefinition getTemplate(@PathVariable String type) {
        return promptTemplateManagerService.getTemplateDefinition(type);
    }

    @PutMapping("/{type}")
    public ResponseEntity<PromptTemplateDefinition> updateTemplate(
            @PathVariable String type, 
            @RequestBody Map<String, String> payload) {
        
        String content = payload.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        PromptTemplateDefinition updated = promptTemplateManagerService.updateTemplate(type, content);
        return ResponseEntity.ok(updated);
    }

    @PostMapping
    public ResponseEntity<?> createTemplate(@RequestBody Map<String, String> payload) {
        String type = payload.get("templateType");
        String name = payload.get("templateName");
        String content = payload.get("templateContent");
        String description = payload.get("description");
        
        if (type == null || type.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "模板类型不能为空"));
        }
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "模板名称不能为空"));
        }
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "模板内容不能为空"));
        }
        
        try {
            PromptTemplateDefinition created = promptTemplateManagerService.createTemplate(
                type.trim().toUpperCase(), 
                name.trim(), 
                content.trim(), 
                description != null ? description.trim() : ""
            );
            return ResponseEntity.ok(created);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

