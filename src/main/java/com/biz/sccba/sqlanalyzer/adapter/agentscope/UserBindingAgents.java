package com.biz.sccba.sqlanalyzer.adapter.agentscope;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Binds the authenticated user identity onto every {@link RuntimeContext} that reaches a shared
 * agent. The official AG-UI adapter only sets {@code sessionId = threadId} on the context it
 * builds; this project's identity mapping (development-guide §2.2) requires
 * {@code userId = stable clientId} so that AgentState slots and USER-scoped long-term memory
 * isolate per client and share across that client's sessions.
 *
 * <p>The userId is supplied by the server from the authenticated Bearer token (carried in
 * server-written {@code forwardedProps}), never from client-controlled fields.
 */
public final class UserBindingAgents {

    /** forwardedProps key under which the server stores the authenticated client id. */
    public static final String CLIENT_ID_PROP = "spa.clientId";

    private UserBindingAgents() {
    }

    /** Wraps {@code delegate} so any RuntimeContext argument gains {@code userId} when absent. */
    public static Agent bind(Agent delegate, String userId) {
        return (Agent) Proxy.newProxyInstance(
                UserBindingAgents.class.getClassLoader(),
                new Class<?>[] { Agent.class },
                (proxy, method, args) -> {
                    if (args != null) {
                        for (int i = 0; i < args.length; i++) {
                            if (args[i] instanceof RuntimeContext rc) {
                                args[i] = withUserId(rc, userId);
                            }
                        }
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    static RuntimeContext withUserId(RuntimeContext rc, String userId) {
        if (userId == null || userId.isBlank()) return rc;
        if (rc.getUserId() != null && !rc.getUserId().isBlank()) return rc;
        Map<String, Object> extras = rc.getExtra();
        RuntimeContext.Builder builder = RuntimeContext.builder()
                .userId(userId)
                .sessionId(rc.getSessionId());
        if (extras != null && !extras.isEmpty()) {
            builder.putAll(extras);
        }
        return builder.build();
    }
}
