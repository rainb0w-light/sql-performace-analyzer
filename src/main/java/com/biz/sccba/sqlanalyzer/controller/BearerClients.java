package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.service.TokenService;
import com.biz.sccba.sqlanalyzer.api.ApiAuthenticationException;
import com.biz.sccba.sqlanalyzer.api.ApiForbiddenException;
import com.biz.sccba.sqlanalyzer.service.TokenService.AuthenticatedClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Resolves the authenticated client id from the Bearer token on resource APIs. */
@Component
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class BearerClients {

    private final TokenService tokens;

    public BearerClients(TokenService tokens) {
        this.tokens = tokens;
    }

    public String clientId(String authorization) {
        return identity(authorization).clientId();
    }

    public AuthenticatedClient identity(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiAuthenticationException("Authorization 必须使用 Bearer Token");
        }
        try {
            return tokens.resolveIdentity(authorization.substring("Bearer ".length()).trim());
        } catch (IllegalArgumentException exception) {
            throw new ApiAuthenticationException(exception.getMessage(), exception);
        }
    }

    public AuthenticatedClient requireAny(String authorization, String... roles) {
        AuthenticatedClient identity = identity(authorization);
        for (String role : roles) {
            if (role.equals(identity.role())) return identity;
        }
        throw new ApiForbiddenException("当前身份无权访问知识管理 API");
    }
}
