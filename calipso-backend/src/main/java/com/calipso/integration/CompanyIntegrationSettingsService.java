package com.calipso.integration;

import com.calipso.compagny.Company;
import com.calipso.compagny.CompanyRepository;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class CompanyIntegrationSettingsService {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CompanyIntegrationSettingsRepository settingsRepository;
    private final CompanyRepository companyRepository;

    @Value("${calipso.integrations.encryption-key:}")
    private String encryptionKey;

    @Transactional(readOnly = true)
    public CompanyIntegrationSettingsResponse get(Long companyId) {
        return settingsRepository.findByCompanyId(companyId)
                .map(CompanyIntegrationSettingsResponse::from)
                .orElseGet(() -> CompanyIntegrationSettingsResponse.empty(companyId));
    }

    @Transactional
    public CompanyIntegrationSettingsResponse update(Long companyId, CompanyIntegrationSettingsRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable."));
        CompanyIntegrationSettings settings = settingsRepository.findByCompanyId(companyId)
                .orElseGet(() -> CompanyIntegrationSettings.builder().company(company).build());

        String senderAddress = clean(request.smsSenderAddress());
        if (senderAddress != null && !senderAddress.equals("+2250000") && !senderAddress.equals("tel:+2250000")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Orange SMS Côte d’Ivoire utilise l’adresse technique +2250000. Renseignez le nom approuvé dans Nom d’expéditeur.");
        }
        String senderName = clean(request.orangeSenderName());
        if (senderName != null && !senderName.matches("[A-Za-z0-9 ]{1,11}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le nom d’expéditeur Orange doit contenir au maximum 11 caractères alphanumériques ou espaces.");
        }
        settings.setSmsSenderAddress("+2250000");
        settings.setOrangeSenderName(senderName);
        settings.setWhatsappPhoneNumberId(clean(request.whatsappPhoneNumberId()));
        String whatsappApiUrl = clean(request.whatsappApiUrl());
        if (whatsappApiUrl != null && !whatsappApiUrl.matches("^https://graph\\.facebook\\.com(?:/v\\d+\\.\\d+)?/?$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "L’URL WhatsApp doit utiliser le domaine HTTPS officiel graph.facebook.com.");
        }
        settings.setWhatsappApiUrl(whatsappApiUrl);
        settings.setSmtpHost(clean(request.smtpHost()));
        settings.setSmtpPort(request.smtpPort());
        settings.setSmtpFrom(clean(request.smtpFrom()));
        settings.setSmtpUsername(clean(request.smtpUsername()));
        settings.setSmtpStartTls(Boolean.TRUE.equals(request.smtpStartTls()));

        settings.setOrangeClientIdEncrypted(updateSecret(settings.getOrangeClientIdEncrypted(), request.orangeClientId()));
        settings.setOrangeClientSecretEncrypted(updateSecret(settings.getOrangeClientSecretEncrypted(), request.orangeClientSecret()));
        settings.setWhatsappAccessTokenEncrypted(updateSecret(settings.getWhatsappAccessTokenEncrypted(), request.whatsappAccessToken()));
        settings.setSmtpPasswordEncrypted(updateSecret(settings.getSmtpPasswordEncrypted(), request.smtpPassword()));

        if (settings.getSmtpPort() != null && (settings.getSmtpPort() < 1 || settings.getSmtpPort() > 65535)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le port SMTP doit être compris entre 1 et 65535.");
        }
        return CompanyIntegrationSettingsResponse.from(settingsRepository.save(settings));
    }

    public DecryptedSettings findDecrypted(Long companyId) {
        return settingsRepository.findByCompanyId(companyId).map(settings -> new DecryptedSettings(
                settings.getSmsSenderAddress(),
                decryptOrNull(settings.getOrangeClientIdEncrypted()),
                decryptOrNull(settings.getOrangeClientSecretEncrypted()),
                settings.getWhatsappPhoneNumberId(),
                decryptOrNull(settings.getWhatsappAccessTokenEncrypted()),
                settings.getWhatsappApiUrl(),
                settings.getSmtpHost(),
                settings.getSmtpPort(),
                settings.getSmtpFrom(),
                settings.getSmtpUsername(),
                decryptOrNull(settings.getSmtpPasswordEncrypted()),
                Boolean.TRUE.equals(settings.getSmtpStartTls()),
                settings.getOrangeSenderName()
        )).orElse(null);
    }

    private String updateSecret(String current, String requested) {
        if (requested == null) return current;
        if (requested.isBlank()) return null;
        return encrypt(requested);
    }

    private String encrypt(String value) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] result = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossible de chiffrer le secret d’intégration.");
        }
    }

    private String decryptOrNull(String value) {
        if (value == null) return null;
        try {
            byte[] stored = Base64.getDecoder().decode(value);
            byte[] nonce = java.util.Arrays.copyOfRange(stored, 0, NONCE_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(stored, NONCE_BYTES, stored.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Échec du déchiffrement des secrets d’intégration; vérifier la clé configurée.", exception);
        }
    }

    private SecretKey key() {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CALIPSO_INTEGRATIONS_ENCRYPTION_KEY doit être configurée avant d’enregistrer des secrets.");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encryptionKey);
            if (bytes.length != 32) throw new IllegalArgumentException("La clé doit contenir 32 octets.");
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La clé de chiffrement doit être une valeur Base64 valide de 32 octets.");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record DecryptedSettings(
            String smsSenderAddress,
            String orangeClientId,
            String orangeClientSecret,
            String whatsappPhoneNumberId,
            String whatsappAccessToken,
            String whatsappApiUrl,
            String smtpHost,
            Integer smtpPort,
            String smtpFrom,
            String smtpUsername,
            String smtpPassword,
            boolean smtpStartTls,
            String orangeSenderName
    ) { }
}
