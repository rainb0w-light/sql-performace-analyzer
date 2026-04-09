package com.biz.sccba.sqlanalyzer.memory;

import com.biz.sccba.sqlanalyzer.model.TransactionLogEntry;
import com.biz.sccba.sqlanalyzer.model.TransactionLogEntry.ActionType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事务日志管理器
 * 记录和跟踪所有 DDL 事务操作
 */
@Component
public class TransactionLogManager {

    /**
     * 事务日志存储
     */
    private final Map<String, List<TransactionLogEntry>> transactionLogs = new ConcurrentHashMap<>();

    /**
     * 记录事务开始
     */
    public void logTransactionStart(String transactionId, String sessionId, ActionType actionType) {
        TransactionLogEntry entry = TransactionLogEntry.builder()
            .transactionId(transactionId)
            .sessionId(sessionId)
            .actionType(actionType)
            .status(TransactionLogEntry.TransactionStatus.STARTED)
            .timestamp(LocalDateTime.now())
            .build();

        addLogEntry(transactionId, entry);
        System.out.println("[TransactionLogManager] 事务开始：" + transactionId + " - " + sessionId + " - " + actionType);
    }

    /**
     * 记录事务完成
     */
    public void logTransactionComplete(String transactionId, String result) {
        List<TransactionLogEntry> logs = transactionLogs.get(transactionId);
        if (logs != null && !logs.isEmpty()) {
            TransactionLogEntry lastEntry = logs.get(logs.size() - 1);
            TransactionLogEntry completeEntry = TransactionLogEntry.builder()
                .transactionId(transactionId)
                .sessionId(lastEntry.getSessionId())
                .actionType(lastEntry.getActionType())
                .status(TransactionLogEntry.TransactionStatus.COMPLETED)
                .result(result)
                .timestamp(LocalDateTime.now())
                .build();

            addLogEntry(transactionId, completeEntry);
            System.out.println("[TransactionLogManager] 事务完成：" + transactionId + " - 结果：" + result);
        }
    }

    /**
     * 记录事务失败
     */
    public void logTransactionFailed(String transactionId, String errorMessage) {
        List<TransactionLogEntry> logs = transactionLogs.get(transactionId);
        if (logs != null && !logs.isEmpty()) {
            TransactionLogEntry lastEntry = logs.get(logs.size() - 1);
            TransactionLogEntry failedEntry = TransactionLogEntry.builder()
                .transactionId(transactionId)
                .sessionId(lastEntry.getSessionId())
                .actionType(lastEntry.getActionType())
                .status(TransactionLogEntry.TransactionStatus.FAILED)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();

            addLogEntry(transactionId, failedEntry);
            System.out.println("[TransactionLogManager] 事务失败：" + transactionId + " - 错误：" + errorMessage);
        }
    }

    /**
     * 记录事务日志（简化方法）
     */
    public void logTransaction(TransactionLogEntry entry) {
        if (entry == null) return;
        String transactionId = entry.getTransactionId() != null ? entry.getTransactionId() : entry.getSessionId();
        if (transactionId == null) {
            transactionId = "tx_" + System.currentTimeMillis();
            entry.setTransactionId(transactionId);
        }
        addLogEntry(transactionId, entry);
        System.out.println("[TransactionLogManager] 记录事务：" + transactionId + " - " + entry.getActionType());
    }

    /**
     * 添加日志条目
     */
    private void addLogEntry(String transactionId, TransactionLogEntry entry) {
        transactionLogs.computeIfAbsent(transactionId, k -> new ArrayList<>()).add(entry);
    }

    /**
     * 获取事务日志历史
     */
    public List<TransactionLogEntry> getTransactionHistory(String transactionId) {
        return transactionLogs.getOrDefault(transactionId, new ArrayList<>());
    }

    /**
     * 清理事务日志
     */
    public void cleanupOldTransactions(LocalDateTime before) {
        System.out.println("[TransactionLogManager] 清理 " + before + " 之前的事务日志");
        // 可以实现清理逻辑
    }
}
