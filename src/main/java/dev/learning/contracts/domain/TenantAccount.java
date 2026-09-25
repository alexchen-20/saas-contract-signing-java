package dev.learning.contracts.domain;

import java.time.Instant;

public record TenantAccount(String tenantId, String legalName, Status status, Instant updatedAt) {
    public enum Status { PENDING_SIGNATURE, ACTIVE, SUSPENDED }

    public TenantAccount activate(Instant now) {
        if (status != Status.PENDING_SIGNATURE) {
            throw new IllegalStateException("Only a tenant awaiting signature can be activated");
        }
        return new TenantAccount(tenantId, legalName, Status.ACTIVE, now);
    }

    public TenantAccount suspend(Instant now) {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException("Only an active tenant can be suspended");
        }
        return new TenantAccount(tenantId, legalName, Status.SUSPENDED, now);
    }

    public TenantAccount restore(Instant now) {
        if (status != Status.SUSPENDED) {
            throw new IllegalStateException("Only a suspended tenant can be restored");
        }
        return new TenantAccount(tenantId, legalName, Status.ACTIVE, now);
    }
}
