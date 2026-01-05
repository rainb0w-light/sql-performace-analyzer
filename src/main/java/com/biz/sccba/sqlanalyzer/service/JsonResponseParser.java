package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.AgentErrorCode;
import com.biz.sccba.sqlanalyzer.model.AgentException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * JSON 解析与清洗管线，提供统一的错误处理。
 */
@Component
public class JsonResponseParser {

    private static final Logger logger = LoggerFactory.getLogger(JsonResponseParser.class);

    private final ObjectMapper objectMapper;

    public JsonResponseParser() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public <T> T parse(String response, Class<T> clazz, String scene) throws AgentException {
        String jsonContent = normalize(response);
        try {
            return objectMapper.readValue(jsonContent, clazz);
        } catch (JsonProcessingException e) {
            logger.warn("JSON 解析失败，尝试清洗。scene={}, message={}", scene, e.getMessage());
            try {
                String cleaned = cleanJsonContent(jsonContent);
                return objectMapper.readValue(cleaned, clazz);
            } catch (JsonProcessingException ex) {
                logger.error("JSON 解析失败，scene={}, 原始长度={}, 片段={}",
                        scene, response != null ? response.length() : 0,
                        truncate(response, 500));
                throw new AgentException(AgentErrorCode.JSON_PARSE_FAILED,
                        "JSON 解析失败: " + ex.getMessage(), ex);
            }
        }
    }

    private String normalize(String response) {
        if (response == null) {
            return "";
        }
        String jsonContent = response.trim();
        if (jsonContent.startsWith("```json")) {
            jsonContent = jsonContent.substring(7);
        }
        if (jsonContent.startsWith("```")) {
            jsonContent = jsonContent.substring(3);
        }
        if (jsonContent.endsWith("```")) {
            jsonContent = jsonContent.substring(0, jsonContent.length() - 3);
        }
        return jsonContent.trim();
    }

    /**
     * 清理 JSON 内容中的常见问题。
     */
    private String cleanJsonContent(String jsonContent) {
        String cleaned = jsonContent;

        int firstBrace = Math.min(
                cleaned.indexOf('{') >= 0 ? cleaned.indexOf('{') : Integer.MAX_VALUE,
                cleaned.indexOf('[') >= 0 ? cleaned.indexOf('[') : Integer.MAX_VALUE);
        if (firstBrace != Integer.MAX_VALUE && firstBrace > 0) {
            cleaned = cleaned.substring(firstBrace);
        }

        int lastBrace = Math.max(cleaned.lastIndexOf('}'), cleaned.lastIndexOf(']'));
        if (lastBrace > 0 && lastBrace < cleaned.length() - 1) {
            cleaned = cleaned.substring(0, lastBrace + 1);
        }

        cleaned = cleaned.replaceAll("\\\\\\\\", "\\\\");
        cleaned = cleaned.replaceAll("\\\\\\\\\"", "\\\\\"");
        cleaned = cleaned.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return cleaned.trim();
    }

    private String truncate(String content, int max) {
        if (content == null) {
            return "";
        }
        return content.substring(0, Math.min(max, content.length()));
    }
}

