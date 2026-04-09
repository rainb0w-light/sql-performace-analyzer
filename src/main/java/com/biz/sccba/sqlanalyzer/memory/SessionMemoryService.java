package com.biz.sccba.sqlanalyzer.memory;

import com.biz.sccba.sqlanalyzer.model.agent.AnalysisSession;
import com.biz.sccba.sqlanalyzer.model.agent.AnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆服务
 * 管理分析会话的状态和历史
 *
 * 用于支持多轮对话和会话恢复
 */
@Service
@RequiredArgsConstructor
public class SessionMemoryService {

    // 会话存储
    private final Map<String, AnalysisSession> sessionStore = new ConcurrentHashMap<>();

    // 会话过期时间 (毫秒) - 默认 30 分钟
    private static final long SESSION_EXPIRY_MS = 30 * 60 * 1000;

    /**
     * 创建新会话
     *
     * @param userRequest    用户请求
     * @param datasourceName 数据源名称
     * @param llmName        LLM 名称
     * @return 会话 ID
     */
    public String createSession(String userRequest, String datasourceName, String llmName) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);

        AnalysisSession session = AnalysisSession.builder()
            .sessionId(sessionId)
            .userRequest(userRequest)
            .datasourceName(datasourceName)
            .llmName(llmName)
            .status(AnalysisSession.SessionStatus.PENDING)
            .reasoningSteps(new ArrayList<>())
            .toolCalls(new ArrayList<>())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .context(new HashMap<>())
            .build();

        sessionStore.put(sessionId, session);
        System.out.println("[SessionMemoryService] 创建会话：" + sessionId + " 请求：" + userRequest);

        return sessionId;
    }

    /**
     * 获取会话
     *
     * @param sessionId 会话 ID
     * @return 会话对象
     */
    public AnalysisSession getSession(String sessionId) {
        AnalysisSession session = sessionStore.get(sessionId);
        if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
        }
        return session;
    }

    /**
     * 更新会话状态
     *
     * @param sessionId 会话 ID
     * @param status    新状态
     */
    public void updateStatus(String sessionId, AnalysisSession.SessionStatus status) {
        AnalysisSession session = sessionStore.get(sessionId);
        if (session != null) {
            session.setStatus(status);
            session.setUpdatedAt(LocalDateTime.now());
            System.out.println("[SessionMemoryService] 会话 " + sessionId + " 状态更新为：" + status);
        }
    }

    /**
     * 添加推理步骤
     *
     * @param sessionId 会话 ID
     * @param step      推理步骤
     */
    public void addReasoningStep(String sessionId, AnalysisSession.ReasoningStep step) {
        AnalysisSession session = sessionStore.get(sessionId);
        if (session != null && session.getReasoningSteps() != null) {
            step.setTimestamp(LocalDateTime.now());
            step.setStepNumber(session.getReasoningSteps().size() + 1);
            session.getReasoningSteps().add(step);
            session.setUpdatedAt(LocalDateTime.now());
        }
    }

    /**
     * 添加工具调用记录
     *
     * @param sessionId 会话 ID
     * @param record    工具调用记录
     */
    public void addToolCall(String sessionId, AnalysisSession.ToolCallRecord record) {
        AnalysisSession session = sessionStore.get(sessionId);
        if (session != null && session.getToolCalls() != null) {
            record.setTimestamp(LocalDateTime.now());
            session.getToolCalls().add(record);
            session.setUpdatedAt(LocalDateTime.now());
        }
    }

    /**
     * 设置分析结果
     *
     * @param sessionId 会话 ID
     * @param result    分析结果
     */
    public void setResult(String sessionId, AnalysisResult result) {
        AnalysisSession session = sessionStore.get(sessionId);
        if (session != null) {
            session.setResult(result);
            session.setStatus(AnalysisSession.SessionStatus.COMPLETED);
            session.setUpdatedAt(LocalDateTime.now());
        }
    }

    /**
     * 设置会话上下文
     *
     * @param sessionId 会话 ID
     * @param key       键
     * @param value     值
     */
    public void setContext(String sessionId, String key, Object value) {
        AnalysisSession session = sessionStore.get(sessionId);
        if (session != null && session.getContext() != null) {
            session.getContext().put(key, value);
            session.setUpdatedAt(LocalDateTime.now());
        }
    }

    /**
     * 获取会话上下文
     *
     * @param sessionId 会话 ID
     * @param key       键
     * @return 值
     */
    public Object getContext(String sessionId, String key) {
        AnalysisSession session = sessionStore.get(sessionId);
        if (session != null && session.getContext() != null) {
            return session.getContext().get(key);
        }
        return null;
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话 ID
     */
    public void removeSession(String sessionId) {
        sessionStore.remove(sessionId);
        System.out.println("[SessionMemoryService] 删除会话：" + sessionId);
    }

    /**
     * 获取所有活动会话
     *
     * @return 会话列表
     */
    public List<AnalysisSession> getActiveSessions() {
        return new ArrayList<>(sessionStore.values());
    }

    /**
     * 清理过期会话
     */
    public void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        sessionStore.entrySet().removeIf(entry -> {
            LocalDateTime updatedAt = entry.getValue().getUpdatedAt();
            if (updatedAt != null) {
                long elapsed = now - updatedAt.minusNanos(0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (elapsed > SESSION_EXPIRY_MS) {
                    System.out.println("[SessionMemoryService] 清理过期会话：" + entry.getKey());
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * 获取会话数量
     *
     * @return 会话数量
     */
    public int getSessionCount() {
        return sessionStore.size();
    }
}
