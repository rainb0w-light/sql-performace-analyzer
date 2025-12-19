package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.TestExecutionResult;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Mermaid时序图生成器
 */
@Service
public class SequenceDiagramGenerator {

    /**
     * 将执行结果转换为Mermaid时序图
     */
    public String generateSequenceDiagram(List<TestExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return "sequenceDiagram\n    Note over Database: 无执行结果";
        }

        StringBuilder diagram = new StringBuilder();
        diagram.append("sequenceDiagram\n");

        // 获取所有参与者（线程和数据库）
        Set<String> participants = new LinkedHashSet<>();
        for (TestExecutionResult result : results) {
            participants.add(result.getThreadId());
        }
        participants.add("Database");

        // 定义参与者
        for (String participant : participants) {
            String displayName = participant.equals("Database") ? "Database" : participant;
            diagram.append("    participant ").append(getParticipantId(participant))
                   .append(" as ").append(displayName).append("\n");
        }

        diagram.append("\n");

        // 按时间顺序生成消息
        List<TestExecutionResult> sortedResults = new ArrayList<>(results);
        sortedResults.sort(Comparator.comparing(TestExecutionResult::getStartTimeNanos));

        // 记录每个线程的激活状态
        Map<String, Boolean> activeStates = new HashMap<>();
        for (String participant : participants) {
            if (!participant.equals("Database")) {
                activeStates.put(participant, false);
            }
        }

        for (TestExecutionResult result : sortedResults) {
            String threadId = result.getThreadId();
            String participantId = getParticipantId(threadId);
            String sql = result.getSql().trim();
            
            // 简化SQL显示（如果太长）
            String displaySql = truncateSql(sql, 50);
            
            // 根据SQL类型决定消息类型
            String sqlUpper = sql.toUpperCase();
            boolean isTransactionControl = sqlUpper.startsWith("BEGIN") || 
                                         sqlUpper.startsWith("COMMIT") || 
                                         sqlUpper.startsWith("ROLLBACK");

            // 如果是BEGIN，激活线程
            if (sqlUpper.startsWith("BEGIN")) {
                if (!activeStates.getOrDefault(threadId, false)) {
                    diagram.append("    activate ").append(participantId).append("\n");
                    activeStates.put(threadId, true);
                }
            }

            // 生成消息
            if (result.getStatus() == TestExecutionResult.ExecutionStatus.SUCCESS) {
                // 成功消息
                if (isTransactionControl) {
                    diagram.append("    ").append(participantId).append("->>Database: ")
                           .append(displaySql);
                } else {
                    diagram.append("    ").append(participantId).append("->>Database: ")
                           .append(displaySql);
                }
                
                // 添加延迟信息（如果有）
                if (result.getDurationMillis() != null && result.getDurationMillis() > 100) {
                    diagram.append(" (").append(result.getDurationMillis()).append("ms)");
                }
                diagram.append("\n");
            } else {
                // 失败消息（使用虚线表示异常）
                diagram.append("    ").append(participantId).append("-->>Database: ")
                       .append(displaySql).append(" (FAILED)\n");
                
                // 添加异常信息
                if (result.getException() != null) {
                    String exceptionMsg = result.getException().getMessage();
                    if (exceptionMsg != null && !exceptionMsg.isEmpty()) {
                        String shortMsg = truncateString(exceptionMsg, 40);
                        diagram.append("    Note over ").append(participantId)
                               .append(": ").append(escapeMermaidText(shortMsg)).append("\n");
                    }
                    
                    // 如果是死锁异常，特别标注
                    if (exceptionMsg != null && (exceptionMsg.contains("Deadlock") || 
                                                 exceptionMsg.contains("deadlock") ||
                                                 exceptionMsg.contains("1213"))) {
                        diagram.append("    Note over ").append(participantId)
                               .append(",Database: 死锁检测\n");
                    }
                }
            }

            // 如果是COMMIT或ROLLBACK，取消激活
            if (sqlUpper.startsWith("COMMIT") || sqlUpper.startsWith("ROLLBACK")) {
                if (activeStates.getOrDefault(threadId, false)) {
                    diagram.append("    deactivate ").append(participantId).append("\n");
                    activeStates.put(threadId, false);
                }
            }
        }

        // 确保所有激活都被取消
        for (Map.Entry<String, Boolean> entry : activeStates.entrySet()) {
            if (entry.getValue()) {
                diagram.append("    deactivate ").append(getParticipantId(entry.getKey())).append("\n");
            }
        }

        return diagram.toString();
    }

    /**
     * 获取参与者ID（用于Mermaid语法）
     */
    private String getParticipantId(String participant) {
        // Mermaid参与者ID不能包含特殊字符，使用简化形式
        return participant.replaceAll("[^a-zA-Z0-9]", "");
    }

    /**
     * 截断SQL语句以便显示
     */
    private String truncateSql(String sql, int maxLength) {
        if (sql == null) {
            return "";
        }
        sql = sql.replaceAll("\\s+", " ").trim();
        if (sql.length() <= maxLength) {
            return escapeMermaidText(sql);
        }
        return escapeMermaidText(sql.substring(0, maxLength - 3) + "...");
    }

    /**
     * 截断字符串
     */
    private String truncateString(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * 转义Mermaid文本中的特殊字符
     */
    private String escapeMermaidText(String text) {
        if (text == null) {
            return "";
        }
        // Mermaid中需要转义的字符
        return text.replace("\"", "&quot;")
                   .replace("'", "&#39;")
                   .replace("\n", " ")
                   .replace("\r", " ");
    }
}

