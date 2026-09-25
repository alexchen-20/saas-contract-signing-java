package dev.learning.contracts.domain;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TenantAccounts {
    private final Map<String, TenantAccount> accounts = new ConcurrentHashMap<>();

    public TenantAccount register(String tenantId, String legalName, Instant now) {
        TenantAccount pending = new TenantAccount(
                tenantId, legalName, TenantAccount.Status.PENDING_SIGNATURE, now);
        TenantAccount existing = accounts.putIfAbsent(tenantId, pending);
        if (existing != null) {
            throw new IllegalStateException("Tenant already exists: " + tenantId);
        }
        return pending;
    }

    public TenantAccount require(String tenantId) {
        TenantAccount account = accounts.get(tenantId);
        if (account == null) {
            throw new IllegalArgumentException("Unknown tenant: " + tenantId);
        }
        return account;
    }

    public TenantAccount save(TenantAccount account) {
        accounts.put(account.tenantId(), account);
        return account;
    }
}
