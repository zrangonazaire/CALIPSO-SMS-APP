package com.calipso.integration;

import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyIntegrationSettingsServiceTest {
    private final CompanyIntegrationSettingsRepository settingsRepository = mock(CompanyIntegrationSettingsRepository.class);
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final CompanyIntegrationSettingsService service =
            new CompanyIntegrationSettingsService(settingsRepository, companyRepository);

    @Test
    void encryptsProviderSecretsAndNeverReturnsThemInTheSettingsResponse() {
        ReflectionTestUtils.setField(service, "encryptionKey", Base64.getEncoder().encodeToString(new byte[32]));
        var company = Company.builder().id(7L).name("Entreprise").build();
        when(companyRepository.findById(7L)).thenReturn(Optional.of(company));
        var persisted = new AtomicReference<CompanyIntegrationSettings>();
        when(settingsRepository.findByCompanyId(7L)).thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        when(settingsRepository.save(any())).thenAnswer(invocation -> {
            var value = invocation.<CompanyIntegrationSettings>getArgument(0);
            persisted.set(value);
            return value;
        });

        var response = service.update(7L, new CompanyIntegrationSettingsRequest(
                "tel:+2250000", "orange-client", "orange-secret", "wa-phone", "wa-secret",
                "https://graph.facebook.com/v19.0", "smtp.example.ci", 587, "noreply@example.ci",
                "smtp-user", "smtp-secret", true, "MAGISER"));

        assertTrue(response.orangeCredentialsConfigured());
        assertTrue(response.whatsappCredentialsConfigured());
        assertTrue(response.smtpPasswordConfigured());
        assertFalse(response.toString().contains("orange-secret"));
        assertFalse(response.toString().contains("wa-secret"));
        assertFalse(response.toString().contains("smtp-secret"));

        var saved = persisted.get();
        assertNotEquals("orange-secret", saved.getOrangeClientSecretEncrypted());
        assertNotEquals("wa-secret", saved.getWhatsappAccessTokenEncrypted());
        assertNotEquals("smtp-secret", saved.getSmtpPasswordEncrypted());
        var decrypted = service.findDecrypted(7L);
        assertEquals("orange-secret", decrypted.orangeClientSecret());
        assertEquals("wa-secret", decrypted.whatsappAccessToken());
        assertEquals("smtp-secret", decrypted.smtpPassword());
    }

    @Test
    void rejectsNonMetaWhatsappEndpoints() {
        var company = Company.builder().id(7L).name("Entreprise").build();
        when(companyRepository.findById(7L)).thenReturn(Optional.of(company));
        when(settingsRepository.findByCompanyId(7L)).thenReturn(Optional.empty());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.update(7L, new CompanyIntegrationSettingsRequest(
                        null, null, null, "wa-phone", null, "https://attacker.example",
                        null, null, null, null, null, null, null)));
        verify(settingsRepository, never()).save(any());
    }
}
