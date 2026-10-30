package com.calipso.integration;

import com.calipso.auth.CompanyAccess;
import com.calipso.sms.SmtpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/integrations/smtp")
@RequiredArgsConstructor
public class SmtpConnectionController {
    private final CompanyAccess access;
    private final CompanyIntegrationSettingsService settings;
    private final SmtpClient smtp;

    @PostMapping("/test")
    public SmtpClient.ConnectionStatus test(@PathVariable Long companyId) {
        access.requirePlatformAdmin();
        var saved = settings.findDecrypted(companyId);
        if (saved == null || saved.smtpHost() == null || saved.smtpHost().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enregistrez d’abord le serveur SMTP de cette entreprise.");
        }
        return smtp.checkConnection(saved);
    }
}
