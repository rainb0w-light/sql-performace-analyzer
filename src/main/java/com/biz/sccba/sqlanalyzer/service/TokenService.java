package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.repository.ClientRepository;
import com.biz.sccba.sqlanalyzer.repository.ClientTokenRepository;
import com.biz.sccba.sqlanalyzer.domain.Client;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and resolves client bearer tokens.
 *
 * <p>Contract: {@link #hash(String)} is SHA-256 lowercase hex and is persisted in the database;
 * it must remain bit-for-bit stable across renames and releases (pinned by TokenHashContractTest).
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class TokenService {
    private final ClientRepository clientDao;
    private final ClientTokenRepository tokenDao;
    private final SecureRandom random = new SecureRandom();

    public TokenService(ClientRepository clientDao, ClientTokenRepository tokenDao) {
        this.clientDao = clientDao;
        this.tokenDao = tokenDao;
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public IssuedToken issue(String name, String type, String deviceId) {
        String clientId = "client_" + UUID.randomUUID();
        Client client = clientDao.create(clientId, name, type, deviceId);
        String raw = "spa_" + randomToken();
        String prefix = raw.substring(0, Math.min(raw.length(), 16));
        tokenDao.create("token_" + UUID.randomUUID(), client.id(), hash(raw), prefix);
        return new IssuedToken(client, raw);
    }

    public String resolveClientId(String rawToken) {
        return resolveIdentity(rawToken).clientId();
    }

    public AuthenticatedClient resolveIdentity(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new IllegalArgumentException("缺少 Bearer Token");
        var token = tokenDao.findActiveByHash(hash(rawToken)).orElseThrow(() -> new IllegalArgumentException("Token 无效或已吊销"));
        tokenDao.touch(token.id());
        clientDao.touch(token.clientId());
        Client client = clientDao.findById(token.clientId())
                .orElseThrow(() -> new IllegalArgumentException("Token 对应客户端不存在"));
        String role = switch (client.type()) {
            case "KNOWLEDGE_ADMIN" -> "KNOWLEDGE_ADMIN";
            case "KNOWLEDGE_VIEWER" -> "KNOWLEDGE_VIEWER";
            case "AGENT_CLIENT" -> "AGENT_CLIENT";
            default -> "AGENT_CLIENT"; // all existing IDEA Plugin tokens remain read-only agents
        };
        return new AuthenticatedClient(client.id(), client.id(), role);
    }

    public void revoke(String clientId, String rawToken) {
        var token = tokenDao.findActiveByHash(hash(rawToken)).orElseThrow(() -> new IllegalArgumentException("Token 无效"));
        if (!token.clientId().equals(clientId)) throw new IllegalArgumentException("Token 不属于当前客户端");
        tokenDao.revoke(token.id());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算 Token hash", e);
        }
    }

    public record IssuedToken(Client client, String accessToken) {}

    public record AuthenticatedClient(String clientId, String actorId, String role) {}
}
