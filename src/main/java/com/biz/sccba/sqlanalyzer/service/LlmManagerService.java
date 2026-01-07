package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.config.AiConfig;
import com.biz.sccba.sqlanalyzer.data.LlmConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 大模型管理服务
 * 提供大模型的获取和管理功能
 */
@Service
public class LlmManagerService {

    private final Map<String, ChatModel> chatModelMap;
    private final List<LlmConfig> llmConfigs;

    @Autowired
    public LlmManagerService(
            Map<String, ChatModel> chatModelMap,
            AiConfig.LlmConfigProperties properties) {
        this.chatModelMap = chatModelMap != null ? chatModelMap : new HashMap<>();
        this.llmConfigs = properties != null && properties.getConfigs() != null 
            ? properties.getConfigs() 
            : List.of();
    }

    /**
     * 根据名称获取ChatClient
     */
    public ChatClient getChatClient(String name) {
        if (name == null || name.trim().isEmpty()) {
            // 如果没有指定名称，返回第一个模型（向后兼容）
            if (!chatModelMap.isEmpty()) {
                ChatModel defaultModel = chatModelMap.values().iterator().next();
                return ChatClient.builder(defaultModel).build();
            }
            throw new IllegalStateException("没有配置任何大模型");
        }
        
        ChatModel chatModel = chatModelMap.get(name);
        if (chatModel == null) {
            throw new IllegalArgumentException("未找到名称为 '" + name + "' 的大模型");
        }
        
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 根据名称获取ChatModel
     */
    public ChatModel getChatModel(String name) {
        if (name == null || name.trim().isEmpty()) {
            // 如果没有指定名称，返回第一个模型（向后兼容）
            if (!chatModelMap.isEmpty()) {
                return chatModelMap.values().iterator().next();
            }
            throw new IllegalStateException("没有配置任何大模型");
        }
        
        ChatModel chatModel = chatModelMap.get(name);
        if (chatModel == null) {
            throw new IllegalArgumentException("未找到名称为 '" + name + "' 的大模型");
        }
        
        return chatModel;
    }

    /**
     * 获取所有大模型配置列表
     */
    public List<LlmInfo> getAllLlms() {
        return llmConfigs.stream()
            .map(config -> {
                LlmInfo info = new LlmInfo();
                info.setName(config.getName());
                info.setType(config.getType());
                info.setModel(config.getModel());
                info.setBaseUrl(config.getBaseUrl());
                info.setTemperature(config.getTemperature());
                // 不返回API Key
                return info;
            })
            .collect(Collectors.toList());
    }

    /**
     * 检查大模型是否存在
     */
    public boolean exists(String name) {
        return name != null && chatModelMap.containsKey(name);
    }

    /**
     * 大模型信息（用于API返回）
     */
    public static class LlmInfo {
        private String name;
        private String type;
        private String model;
        private String baseUrl;
        private Double temperature;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }
    }
}

