package com.example.sqlanalyzer.config;

import com.example.sqlanalyzer.model.LlmConfig;
import lombok.Getter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI模型配置
 * 支持多个大模型配置
 */
@Configuration
@EnableConfigurationProperties(AiConfig.LlmConfigProperties.class)
public class AiConfig {


    /**
     * 创建所有配置的ChatModel Bean
     */
    @Bean
    public Map<String, ChatModel> chatModelMap(LlmConfigProperties properties) {
        Map<String, ChatModel> chatModelMap = new HashMap<>();
        List<LlmConfig> configs = properties.getConfigs();
        
        if (configs != null && !configs.isEmpty()) {
            for (LlmConfig config : configs) {
                if (config.getName() == null || config.getName().trim().isEmpty()) {
                    throw new IllegalStateException("大模型配置中name不能为空");
                }
                
                if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
                    throw new IllegalStateException("大模型配置中api-key不能为空: " + config.getName());
                }
                
                if (config.getBaseUrl() == null || config.getBaseUrl().trim().isEmpty()) {
                    throw new IllegalStateException("大模型配置中base-url不能为空: " + config.getName());
                }
                
                if (config.getModel() == null || config.getModel().trim().isEmpty()) {
                    throw new IllegalStateException("大模型配置中model不能为空: " + config.getName());
                }
                
                // 创建OpenAI API客户端（兼容DeepSeek等OpenAI兼容的API）
                OpenAiApi openAiApi = new OpenAiApi(config.getBaseUrl(), config.getApiKey());
                
                // 创建ChatOptions
                OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                    .withModel(config.getModel());
                
                if (config.getTemperature() != null) {
                    optionsBuilder.withTemperature(config.getTemperature());
                }
                
                // 创建ChatModel
                OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, optionsBuilder.build());
                chatModelMap.put(config.getName(), chatModel);
            }
        }
        
        return chatModelMap;
    }

    /**
     * 默认ChatClient（向后兼容，使用第一个配置的模型）
     */
    @Bean
    @Primary
    public ChatClient chatClient(Map<String, ChatModel> chatModelMap) {
        if (chatModelMap == null || chatModelMap.isEmpty()) {
            throw new IllegalStateException("未配置任何AI模型。请配置至少一个大模型。");
        }
        
        // 使用第一个模型作为默认模型
        ChatModel defaultModel = chatModelMap.values().iterator().next();
        return ChatClient.builder(defaultModel).build();
    }

    /**
     * 大模型配置属性类
     * 配置格式：spring.llms.configs 列表
     */
    @Getter
    @ConfigurationProperties(prefix = "spring.llms")
    public static class LlmConfigProperties {
        private List<LlmConfig> configs;

        public void setConfigs(List<LlmConfig> configs) {
            this.configs = configs;
        }
    }
}

