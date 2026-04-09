package com.biz.sccba.sqlanalyzer.memory;

import com.biz.sccba.sqlanalyzer.model.agent.DdlConfirmationRequest;
import com.biz.sccba.sqlanalyzer.model.agent.DdlConfirmationResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * DDL 确认管理器
 * 管理需要用户确认的 DDL 操作
 */
@Component
public class DdlConfirmationManager {

    /**
     * 待确认的请求存储
     */
    private final Map<String, DdlConfirmationRequest> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 确认响应存储
     */
    private final Map<String, CountDownLatch> pendingLatches = new ConcurrentHashMap<>();

    /**
     * 创建确认请求
     */
    public String createConfirmation(DdlConfirmationRequest request) {
        String requestId = java.util.UUID.randomUUID().toString();
        pendingRequests.put(requestId, request);
        pendingLatches.put(requestId, new CountDownLatch(1));
        System.out.println("[DdlConfirmationManager] 创建 DDL 确认请求：" + requestId + " - " + request.getType());
        return requestId;
    }

    /**
     * 获取确认请求
     */
    public DdlConfirmationRequest getConfirmationRequest(String requestId) {
        return pendingRequests.get(requestId);
    }

    /**
     * 等待用户确认
     */
    public DdlConfirmationResponse waitForConfirmation(String requestId) throws InterruptedException {
        CountDownLatch latch = pendingLatches.get(requestId);
        if (latch != null) {
            latch.await(300, TimeUnit.SECONDS); // 5 分钟超时
        }
        // 简化：返回一个默认的确认响应
        DdlConfirmationRequest request = pendingRequests.get(requestId);
        if (request != null) {
            return new DdlConfirmationResponse(true, request.getDdlStatement(), null);
        }
        return new DdlConfirmationResponse(false, null, "请求不存在");
    }

    /**
     * 确认 DDL 操作
     */
    public void confirm(String requestId, String comment) {
        CountDownLatch latch = pendingLatches.remove(requestId);
        if (latch != null) {
            latch.countDown();
        }
        System.out.println("[DdlConfirmationManager] DDL 已确认：" + requestId);
    }

    /**
     * 拒绝 DDL 操作
     */
    public void reject(String requestId, String comment) {
        CountDownLatch latch = pendingLatches.remove(requestId);
        if (latch != null) {
            latch.countDown();
        }
        System.out.println("[DdlConfirmationManager] DDL 已拒绝：" + requestId + " - " + comment);
    }

    /**
     * 移除确认请求
     */
    public void removeConfirmationRequest(String requestId) {
        pendingRequests.remove(requestId);
        pendingLatches.remove(requestId);
    }

    /**
     * 清理过期的请求
     */
    public void cleanupExpiredRequests() {
        System.out.println("[DdlConfirmationManager] 清理过期的 DDL 确认请求");
    }
}
