package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.api.IdempotencyConflictException;
import com.biz.sccba.sqlanalyzer.repository.IdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP idempotency boundary for statement analysis. The response and Session/Run/Job creation are
 * committed in one management transaction. The database key is tenant scoped; the local lock
 * prevents duplicate work under concurrent requests in one application instance, while the
 * database primary key remains the final cross-instance guard.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class IdempotentAnalysisRunService {

    private static final String METHOD = "POST";
    private static final String PATH = "/api/v1/mapper-statements/analyze";

    private final AnalysisRunOrchestrator orchestrator;
    private final IdempotencyRepository idempotency;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public IdempotentAnalysisRunService(AnalysisRunOrchestrator orchestrator,
                                        IdempotencyRepository idempotency,
                                        ObjectMapper objectMapper,
                                        @Qualifier("managementTransactionManager")
                                        PlatformTransactionManager transactionManager) {
        this.orchestrator = orchestrator;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public AnalysisRunOrchestrator.RunHandle start(String clientId, String idempotencyKey,
                                                   AnalysisRunOrchestrator.Command command) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key 不能为空");
        }
        if (idempotencyKey.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key 长度不能超过 200");
        }
        String normalizedKey = idempotencyKey.trim();
        String lockKey = clientId + "\u0000" + normalizedKey;
        Object lock = locks.computeIfAbsent(lockKey, ignored -> new Object());
        try {
            synchronized (lock) {
                return transactions.execute(status -> startInTransaction(
                        clientId, normalizedKey, command));
            }
        } finally {
            locks.remove(lockKey, lock);
        }
    }

    private AnalysisRunOrchestrator.RunHandle startInTransaction(
            String clientId, String normalizedKey, AnalysisRunOrchestrator.Command command) {
        String digest = digest(command);
        var stored = idempotency.find(clientId, normalizedKey);
        if (stored.isPresent()) {
            if (!digest.equals(stored.get().requestDigest())
                    || !METHOD.equals(stored.get().method())
                    || !PATH.equals(stored.get().path())) {
                throw new IdempotencyConflictException(
                        "Idempotency-Key 已用于不同的分析请求");
            }
            return readHandle(stored.get().responseBody());
        }

        var handle = orchestrator.start(clientId, command);
        String response = write(handle);
        Instant now = Instant.now();
        idempotency.save(new IdempotencyRepository.Record(
                clientId, normalizedKey, digest, METHOD, PATH, 202, response,
                now, now.plus(24, ChronoUnit.HOURS)));
        return handle;
    }

    private String digest(AnalysisRunOrchestrator.Command command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(command)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算请求摘要", e);
        }
    }

    private String write(AnalysisRunOrchestrator.RunHandle handle) {
        try {
            return objectMapper.writeValueAsString(handle);
        } catch (Exception e) {
            throw new IllegalStateException("无法持久化幂等响应", e);
        }
    }

    private AnalysisRunOrchestrator.RunHandle readHandle(String body) {
        try {
            return objectMapper.readValue(body, AnalysisRunOrchestrator.RunHandle.class);
        } catch (Exception e) {
            throw new IllegalStateException("已存储的幂等响应无效", e);
        }
    }
}
