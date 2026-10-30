package com.calipso.integration;

import com.calipso.auth.CompanyAccess;
import com.calipso.sms.OrangeSmsClient;
import com.calipso.sms.OrangeSmsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/companies/{companyId}/integrations/orange")
@RequiredArgsConstructor
public class OrangeConnectionController {
    private final CompanyAccess access;
    private final CompanyIntegrationSettingsService settings;
    private final OrangeSmsClient orange;
    private final OrangeSmsProperties properties;

    public record Configuration(String tokenUrl, String messagingUrl, String contractsUrl) {}
    public record Credentials(String clientId, String clientSecret) {}

    @GetMapping("/configuration")
    public Configuration configuration(@PathVariable Long companyId) {
        access.requirePlatformAdmin();
        return new Configuration(properties.tokenUrl(), properties.outboundUrl()
                .replace("{senderAddress}", "tel%3A%2B2250000"), properties.contractsUrl());
    }

    @PostMapping("/test")
    public OrangeSmsClient.ConnectionStatus test(@PathVariable Long companyId, @RequestBody Credentials requested) {
        access.requirePlatformAdmin();
        String clientId = requested.clientId();
        String secret = requested.clientSecret();
        boolean idProvided = clientId != null && !clientId.isBlank();
        boolean secretProvided = secret != null && !secret.isBlank();
        if (idProvided != secretProvided) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Saisissez le Client ID et le Client Secret ensemble, ou laissez les deux champs vides pour tester les identifiants enregistrés.");
        }
        if (!idProvided) {
            var saved = settings.findDecrypted(companyId);
            if (saved == null || saved.orangeClientId() == null || saved.orangeClientSecret() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun identifiant Orange complet enregistré pour cette entreprise.");
            }
            clientId = saved.orangeClientId(); secret = saved.orangeClientSecret();
        }
        try {
            return orange.checkCompanyConnection(clientId, secret);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Test Orange interrompu.");
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Authentification Orange impossible. Vérifiez les identifiants, la souscription SMS CI et la disponibilité du fournisseur.");
        }
    }
}
