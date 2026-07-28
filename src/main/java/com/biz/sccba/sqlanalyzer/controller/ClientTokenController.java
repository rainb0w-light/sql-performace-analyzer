package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.service.TokenService;
import com.biz.sccba.sqlanalyzer.repository.ClientRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** clients/tokens resource API (docs/contracts/rest-api.md §1). */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ClientTokenController {

    private final TokenService tokens;
    private final BearerClients bearer;
    private final ClientRepository clients;

    public ClientTokenController(TokenService tokens, ClientRepository clients,
                                 BearerClients bearer) {
        this.tokens = tokens;
        this.clients = clients;
        this.bearer = bearer;
    }

    @PostMapping("/client-tokens/apply")
    public TokenService.IssuedToken apply(@Valid @RequestBody TokenApplyRequest request) {
        return tokens.issue(request.clientName(), request.clientType(), request.deviceId());
    }

    @GetMapping("/client")
    public Object current(@RequestHeader("Authorization") String authorization) {
        String clientId = bearer.clientId(authorization);
        var client = clients.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("客户端不存在"));
        return Map.of("id", client.id(), "name", client.name());
    }

    public record TokenApplyRequest(@NotBlank String clientName, @NotBlank String clientType, String deviceId) {}
}
