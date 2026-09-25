package dev.learning.contracts.web;

import dev.learning.contracts.domain.ContractOnboardingService;
import dev.learning.contracts.domain.TenantAccount;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/admin/tenants")
public class TenantAdminController {
    private final ContractOnboardingService onboarding;

    public TenantAdminController(ContractOnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContractOnboardingService.SignedOnboarding onboard(
            @RequestParam String tenantId,
            @RequestParam String legalName,
            @RequestPart MultipartFile contract,
            @RequestPart MultipartFile certificate,
            @RequestPart MultipartFile privateKey) throws IOException {
        return onboarding.onboard(tenantId, legalName, contract.getBytes(),
                new String(certificate.getBytes(), StandardCharsets.UTF_8),
                new String(privateKey.getBytes(), StandardCharsets.UTF_8));
    }

    @GetMapping("/{tenantId}")
    public TenantAccount find(@PathVariable String tenantId) {
        return onboarding.find(tenantId);
    }

    @PostMapping("/{tenantId}/suspension")
    public TenantAccount suspend(@PathVariable String tenantId) {
        return onboarding.suspend(tenantId);
    }

    @PostMapping("/{tenantId}/restoration")
    public TenantAccount restore(@PathVariable String tenantId) {
        return onboarding.restore(tenantId);
    }
}
