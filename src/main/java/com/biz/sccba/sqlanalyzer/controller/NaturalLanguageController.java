package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.service.NaturalLanguageAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 自然语言分析API控制器
 */
@RestController
@RequestMapping("/api/nl")
public class NaturalLanguageController {

    private static final Logger logger = LoggerFactory.getLogger(NaturalLanguageController.class);

    @Autowired
    private NaturalLanguageAnalysisService naturalLanguageAnalysisService;

    /**
     * 处理自然语言请求
     * POST /api/nl/analyze
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody Map<String, String> request) {
        try {
            String userRequest = request.get("request");
            String datasourceName = request.get("datasourceName");
            String llmName = request.get("llmName");

            if (userRequest == null || userRequest.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("请求内容不能为空"));
            }

            logger.info("收到自然语言分析请求: {}", userRequest);

            NaturalLanguageAnalysisService.NaturalLanguageResponse response = 
                    naturalLanguageAnalysisService.processNaturalLanguageRequest(
                            userRequest.trim(), datasourceName, llmName);

            Map<String, Object> result = new HashMap<>();
            result.put("success", response.isSuccess());
            result.put("userRequest", response.getUserRequest());
            result.put("intent", response.getIntent());
            result.put("toolCalls", response.getToolCalls());
            result.put("toolResults", response.getToolResults());
            result.put("analysisResult", response.getAnalysisResult());
            if (response.getError() != null) {
                result.put("error", response.getError());
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("处理自然语言请求失败", e);
            return ResponseEntity.status(500)
                    .body(createErrorResponse("处理失败: " + e.getMessage()));
        }
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}



