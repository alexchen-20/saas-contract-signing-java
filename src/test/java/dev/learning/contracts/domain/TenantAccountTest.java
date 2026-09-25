package dev.learning.contracts.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantAccountTest {
    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");

    @Test
    void successfulSignatureActivatesTenantAndAllowsAdminSuspension() {
        TenantAccounts accounts = new TenantAccounts();
        ContractSigner signer = (pdf, cert, key, idempotencyKey) ->
                new ObjectMapper().createObjectNode().put("document", "signed-contract");
        ContractOnboardingService service = new ContractOnboardingService(
                accounts, signer, Clock.fixed(NOW, ZoneOffset.UTC));

        ContractOnboardingService.SignedOnboarding result = service.onboard(
                "academy-42", "Northwind Learning", "%PDF".getBytes(StandardCharsets.UTF_8),
                "certificate", "private-key");
        TenantAccount suspended = service.suspend("academy-42");

        assertEquals(TenantAccount.Status.ACTIVE, result.tenant().status());
        assertEquals(TenantAccount.Status.SUSPENDED, suspended.status());
    }

    @Test
    void unsignedTenantCannotBeSuspended() {
        TenantAccount pending = new TenantAccount(
                "academy-42", "Northwind Learning", TenantAccount.Status.PENDING_SIGNATURE, NOW);

        assertThrows(IllegalStateException.class, () -> pending.suspend(NOW.plusSeconds(1)));
    }
}
