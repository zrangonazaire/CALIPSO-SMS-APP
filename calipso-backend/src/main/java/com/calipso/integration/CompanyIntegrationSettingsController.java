package com.calipso.integration;

import com.calipso.auth.CompanyAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/integrations")
@RequiredArgsConstructor
public class CompanyIntegrationSettingsController {
    private final CompanyAccess access;
    private final CompanyIntegrationSettingsService settingsService;

    @GetMapping
    public CompanyIntegrationSettingsResponse get(@PathVariable Long companyId) {
        access.requirePlatformAdmin();
        return settingsService.get(companyId);
    }

    @PutMapping
    public CompanyIntegrationSettingsResponse update(
            @PathVariable Long companyId,
            @RequestBody @Valid CompanyIntegrationSettingsRequest request
    ) {
        access.requirePlatformAdmin();
        return settingsService.update(companyId, request);
    }
}
