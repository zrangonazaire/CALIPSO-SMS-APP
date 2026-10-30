package com.calipso.integration;

public record CompanyIntegrationSettingsResponse(
        Long companyId,
        String smsSenderAddress,
        boolean orangeCredentialsConfigured,
        String whatsappPhoneNumberId,
        String whatsappApiUrl,
        boolean whatsappCredentialsConfigured,
        String smtpHost,
        Integer smtpPort,
        String smtpFrom,
        String smtpUsername,
        boolean smtpPasswordConfigured,
        boolean smtpStartTls,
        String orangeSenderName
) {
    static CompanyIntegrationSettingsResponse from(CompanyIntegrationSettings settings) {
        return new CompanyIntegrationSettingsResponse(
                settings.getCompany().getId(),
                settings.getSmsSenderAddress(),
                settings.getOrangeClientIdEncrypted() != null && settings.getOrangeClientSecretEncrypted() != null,
                settings.getWhatsappPhoneNumberId(),
                settings.getWhatsappApiUrl(),
                settings.getWhatsappAccessTokenEncrypted() != null,
                settings.getSmtpHost(),
                settings.getSmtpPort(),
                settings.getSmtpFrom(),
                settings.getSmtpUsername(),
                settings.getSmtpPasswordEncrypted() != null,
                Boolean.TRUE.equals(settings.getSmtpStartTls()),
                settings.getOrangeSenderName()
        );
    }

    static CompanyIntegrationSettingsResponse empty(Long companyId) {
        return new CompanyIntegrationSettingsResponse(companyId, null, false, null, null, false,
                null, null, null, null, false, false, null);
    }
}
