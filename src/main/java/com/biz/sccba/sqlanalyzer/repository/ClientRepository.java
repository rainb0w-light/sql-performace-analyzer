package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.domain.Client;

import java.util.Optional;

/** Tenant identity root (docs/cloud-code-next-goal.md §3.4). Vendor-neutral domain port. */
public interface ClientRepository {
    Client create(String id, String name, String type, String deviceId);

    Optional<Client> findById(String id);

    void touch(String id);
}
