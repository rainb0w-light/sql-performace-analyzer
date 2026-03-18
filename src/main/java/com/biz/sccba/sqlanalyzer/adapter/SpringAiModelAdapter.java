package com.biz.sccba.sqlanalyzer.adapter;

import io.agentscope.core.model.Model;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Spring AI ChatModel 到 AgentScope Model 接口的适配器
 */
public class SpringAiModelAdapter implements Model {

    private final ChatModel springAiChatModel;
    private final String modelName;

    public SpringAiModelAdapter(ChatModel springAiChatModel, String modelName) {
        this.springAiChatModel = springAiChatModel;
        this.modelName = modelName;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        List<Message> springMessages = convertToSpringMessages(messages);
        if (springMessages.isEmpty()) {
            return Flux.empty();
        }
        Prompt prompt = new Prompt(springMessages);
        org.springframework.ai.chat.model.ChatResponse springResponse = springAiChatModel.call(prompt);

        // 转换 Spring AI ChatResponse 到 AgentScope ChatResponse
        ChatResponse agentScopeResponse = convertToAgentScopeResponse(springResponse);
        return Flux.just(agentScopeResponse);
    }

    private ChatResponse convertToAgentScopeResponse(org.springframework.ai.chat.model.ChatResponse springResponse) {
        String content = springResponse.getResult().getOutput().getContent();
        TextBlock textBlock = TextBlock.builder().text(content).build();

        // 构建 ChatUsage (inputTokens, outputTokens, time)
        var usage = springResponse.getMetadata() != null ?
            springResponse.getMetadata().getUsage() : null;
        long inputTokens = usage != null ? usage.getPromptTokens() : 0;
        long outputTokens = usage != null ? usage.getGenerationTokens() : 0;
        ChatUsage chatUsage = new ChatUsage((int)inputTokens, (int)outputTokens, 0.0);

        // 获取 finishReason
        String finishReason = null;
        var generationMetadata = springResponse.getResult().getMetadata();
        if (generationMetadata != null) {
            finishReason = generationMetadata.getFinishReason();
        }

        return new ChatResponse(
            java.util.UUID.randomUUID().toString(),
            List.of(textBlock),
            chatUsage,
            Collections.emptyMap(),
            finishReason
        );
    }

    private List<Message> convertToSpringMessages(List<Msg> messages) {
        return messages.stream()
                .map(this::convertMsg)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<Message> convertMsg(Msg msg) {
        String content = msg.getTextContent();
        if (content == null || content.trim().isEmpty()) {
            return Optional.empty();
        }

        MsgRole role = msg.getRole();
        if (role == MsgRole.USER) {
            return Optional.of(new UserMessage(content));
        } else if (role == MsgRole.ASSISTANT) {
            return Optional.of(new AssistantMessage(content));
        } else if (role == MsgRole.SYSTEM) {
            return Optional.of(new SystemMessage(content));
        }
        return Optional.empty();
    }
}
