package dev.learning.contracts.domain;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class ContractOnboardingService {
    private final TenantAccounts accounts;
    private final ContractSigner signer;
    private final Clock clock;

    @Autowired
    public ContractOnboardingService(TenantAccounts accounts, ContractSigner signer) {
        this(accounts, signer, Clock.systemUTC());
    }

    ContractOnboardingService(TenantAccounts accounts, ContractSigner signer, Clock clock) {
        this.accounts = accounts;
        this.signer = signer;
        this.clock = clock;
    }

    public SignedOnboarding onboard(String tenantId, String legalName, byte[] contractPdf,
                                     String certPem, String keyPem) {
        TenantAccount pending = accounts.register(tenantId, legalName, Instant.now(clock));
        JsonNode signedDocument = signer.sign(contractPdf, certPem, keyPem,
                "tenant-contract:" + tenantId);
        TenantAccount active = accounts.save(pending.activate(Instant.now(clock)));
        return new SignedOnboarding(active, signedDocument);
    }

    public TenantAccount suspend(String tenantId) {
        return accounts.save(accounts.require(tenantId).suspend(Instant.now(clock)));
    }

    public TenantAccount restore(String tenantId) {
        return accounts.save(accounts.require(tenantId).restore(Instant.now(clock)));
    }

    public TenantAccount find(String tenantId) {
        return accounts.require(tenantId);
    }

    public record SignedOnboarding(TenantAccount tenant, JsonNode signedDocument) {}
}
