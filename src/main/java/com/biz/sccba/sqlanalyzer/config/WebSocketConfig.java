package com.biz.sccba.sqlanalyzer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置
 * 支持 STOMP 协议，用于实时推送分析进度和结果
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的内存消息代理，将消息转发到以 /topic 开头的目的地
        config.enableSimpleBroker("/topic", "/queue", "/user");

        // 设置应用程序目的地前缀
        config.setApplicationDestinationPrefixes("/app");

        // 设置用户目的地前缀（用于点对点消息）
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册 STOMP 端点 - 原生 WebSocket（不使用 SockJS）
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*");
    }
}
